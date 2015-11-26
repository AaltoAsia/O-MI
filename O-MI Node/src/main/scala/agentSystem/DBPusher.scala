/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package agentSystem

import database._
import types._
import types.OdfTypes._
import akka.actor._
import java.lang.Iterable
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}
import scala.collection.JavaConversions.asJavaIterable
import akka.dispatch.RequiresMessageQueue
import akka.dispatch.BoundedMessageQueueSemantics
import scala.util.{Try, Success, Failure}
import scala.xml.XML
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import types._



/** Object that contains all commands of InputPusher.
 */
object InputPusherCmds {
  case class HandleOdf(objects: OdfObjects)
  case class HandleObjects(objs: Iterable[OdfObject])
  case class HandleInfoItems(items: Iterable[OdfInfoItem])
  case class HandlePathValuePairs(pairs: Iterable[(Path, OdfValue)])
  case class HandlePathMetaDataPairs(pairs: Iterable[(Path, String)])
}

import InputPusherCmds._
/**
 * Actor for pushing data to db.
 */
class DBPusher(val dbobject: DB)
  extends Actor
  with ActorLogging
  with RequiresMessageQueue[BoundedMessageQueueSemantics]
  {

  /**
   * Function for handling InputPusherCmds.
   *
   */
  override def receive = {
    case HandleOdf(objects)             => handleOdf(objects)
    case HandleObjects(objs)            => if (objs.nonEmpty) sender() ! handleObjects(objs)
    case HandleInfoItems(items)         => if (items.nonEmpty) sender() ! handleInfoItems(items)
    case HandlePathValuePairs(pairs)    => if (pairs.nonEmpty) sender() ! handlePathValuePairs(pairs)
    case HandlePathMetaDataPairs(pairs) => if (pairs.nonEmpty) sender() ! handlePathMetaDataPairs(pairs)
    case u                              => log.warning("Unknown message received.")
  }

  /**
   * Function for handling OdfObjects.
   *
   */
  private def handleOdf(objects: OdfObjects):  Try[Boolean] = Try{
    val data = getLeafs(objects)
    if(
      data.nonEmpty 
    ){
      val write : Try[Boolean] = handleInfoItems(data.collect { case infoitem: OdfInfoItem => infoitem })
      if( write.isSuccess ){
        log.debug("Successfully saved Odfs to DB")
        val odfNodes = getOdfNodes(objects.objects.toSeq: _*).toSet
        val descriptions = odfNodes.collect {
          case node if node.description.nonEmpty => node
        }.toIterable
        descriptions.map{ node => dbobject.setDescription(node) }
      } 
      write.get
    } else {
      log.warning("Empty odf pushed for DBPusher.")
      false
    }
  }
  /**
   * Function for handling sequences of OdfObject.
   *
   */
  private def handleObjects(objs: Iterable[OdfObject]): Try[Boolean] = {
    handleOdf(OdfObjects(objs))
  }

  /**
   * Function for handling sequences of OdfInfoItem.
   *
   */
  private def handleInfoItems(infoitems: Iterable[OdfInfoItem]): Try[Boolean] = Try{
    
    val pairs = infoitems.map { info =>
      info.values.map { tv => (info.path, tv) }
    }.flatten[(Path, OdfValue)].toList
    dbobject.setMany(pairs)

    log.debug("Successfully saved InfoItems to DB")
    val meta = infoitems.collect {
      case OdfInfoItem(path, _, _, Some(metaData)) => (path, metaData.data)
    }
    val metaTry = if (meta.nonEmpty) handlePathMetaDataPairs(meta) else Success(true)

    val descriptions = infoitems.collect {
      case info if info.description.nonEmpty => info
    }
    descriptions.map{ node => dbobject.setDescription(node) }
    metaTry.get 

  }

  /**
   * Function for handling sequences of path and value pairs.
   *
   */
  private def handlePathValuePairs(pairs: Iterable[(Path, OdfValue)]): Try[Boolean] = Try{
    dbobject.setMany(pairs.toList)
    log.debug("Successfully saved Path-TimedValue pairs to DB")
    true
  }

  /**
   * Function for handling sequences of path and MetaData(as String)  pairs.
   *
   */
  private def handlePathMetaDataPairs(pairs: Iterable[(Path, String)]):  Try[Boolean] = Try{
    
    pairs.foreach { case (path, metadata) => 
    
      Try{
        val xml = XML.loadString(metadata)
        val meta = xmlGen.scalaxb.fromXML[MetaData](xml)
      } match {
        case Success(a) =>
          dbobject.setMetaData(path, metadata) 
        case Failure(exp) =>
         throw exp;
      }
    }
    log.debug("Successfully saved Path-MetaData pairs to DB")
    true
  }

}
