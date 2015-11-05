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
    case HandleObjects(objs)            => if (objs.nonEmpty) handleObjects(objs)
    case HandleInfoItems(items)         => if (items.nonEmpty) handleInfoItems(items)
    case HandlePathValuePairs(pairs)    => if (pairs.nonEmpty) handlePathValuePairs(pairs)
    case HandlePathMetaDataPairs(pairs) => if (pairs.nonEmpty) handlePathMetaDataPairs(pairs)
    case u                              => log.warning("Unknown message received.")
  }

  /**
   * Function for handling OdfObjects.
   *
   */
  private def handleOdf(objects: OdfObjects): Unit = {
    val data = getLeafs(objects)
    if (data.nonEmpty) {
      handleInfoItems(data.collect { case infoitem: OdfInfoItem => infoitem })
      log.debug("Successfully saved Odfs to DB")
      val odfNodes = getOdfNodes(objects.objects.toSeq: _*).toSet
      val descriptions = odfNodes.collect {
        case node if node.description.nonEmpty => node
      }.toIterable
      descriptions.foreach { node => dbobject.setDescription(node) }
    }
  }
  /**
   * Function for handling sequences of OdfObject.
   *
   */
  private def handleObjects(objs: Iterable[OdfObject]): Unit = {
    handleOdf(OdfObjects(objs))
  }

  /**
   * Function for handling sequences of OdfInfoItem.
   *
   */
  private def handleInfoItems(infoitems: Iterable[OdfInfoItem]): Unit = {
    
    val pairs = infoitems.map { info =>
      info.values.map { tv => (info.path, tv) }
    }.flatten[(Path, OdfValue)].toList

    handlePathValuePairs(pairs)

    //log.debug("Successfully saved InfoItems to DB")
    val meta = infoitems.collect {
      case OdfInfoItem(path, _, _, Some(metaData)) => (path, metaData.data)
    }
    if (meta.nonEmpty) handlePathMetaDataPairs(meta)

    val descriptions = infoitems.collect {
      case info if info.description.nonEmpty => info
    }
    descriptions.foreach { info => dbobject.setDescription(info) }
  }

  /**
   * Function for handling sequences of path and value pairs.
   *
   */
  private def handlePathValuePairs(pairs: Iterable[(Path, OdfValue)]): Unit = {
    // save first to latest values and then db
    pairs foreach (data => dbobject.latestStore execute (SetSensorData.apply _).tupled(data))
    dbobject.setMany(pairs.toList)
    log.debug("Successfully saved Path-TimedValue pairs to DB")
  }

  /**
   * Function for handling sequences of path and MetaData(as String)  pairs.
   *
   */
  private def handlePathMetaDataPairs(pairs: Iterable[(Path, String)]): Unit = {
    pairs.foreach { case (path, metadata) => dbobject.setMetaData(path, metadata) }
    log.debug("Successfully saved Path-MetaData pairs to DB")
  }

}
