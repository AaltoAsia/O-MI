/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  https://github.com/AaltoAsia/O-MI/blob/master/LICENSE.txt

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

object InputPusherCmds {
  case class HandleOdf(objects: OdfObjects)
  case class HandleObjects(objs: Iterable[OdfObject])
  case class HandleInfoItems(items: Iterable[OdfInfoItem])
  case class HandlePathValuePairs(pairs: Iterable[(Path, OdfValue)])
  case class HandlePathMetaDataPairs(pairs: Iterable[(Path, String)])
}

import InputPusherCmds._
/**
 * Creates an object for pushing data from internal agents to db.
 *
 */
class DBPusher(val dbobject: DB) extends Actor with ActorLogging with IInputPusher {

  override def receive = {
    case HandleOdf(objects)             => handleOdf(objects)
    case HandleObjects(objs)            => if (objs.nonEmpty) handleObjects(objs)
    case HandleInfoItems(items)         => if (items.nonEmpty) handleInfoItems(items)
    case HandlePathValuePairs(pairs)    => if (pairs.nonEmpty) handlePathValuePairs(pairs)
    case HandlePathMetaDataPairs(pairs) => if (pairs.nonEmpty) handlePathMetaDataPairs(pairs)
    case u                              =>
  }
  override def handleOdf(objects: OdfObjects): Unit = {
    val data = getLeafs(objects)
    if (data.nonEmpty) {
      handleInfoItems(data.collect { case infoitem: OdfInfoItem => infoitem })
      log.debug("Successfully saved Odfs to DB")
      val hasPaths = getOdfNodes(objects.objects.toSeq: _*).toSet
      val des = hasPaths.collect {
        case hPath if hPath.description.nonEmpty => hPath
      }.toIterable
      des.foreach { hpath => dbobject.setDescription(hpath) }
    }
  }
  /**
   * Function for handling sequences of OdfObject.
   *
   */
  override def handleObjects(objs: Iterable[OdfObject]): Unit = {
    handleOdf(OdfObjects(objs))
  }

  /**
   * Function for handling sequences of OdfInfoItem.
   *
   */
  override def handleInfoItems(infoitems: Iterable[OdfInfoItem]): Unit = {
    val infos = infoitems.map { info =>
      info.values.map { tv => (info.path, tv) }
    }.flatten[(Path, OdfValue)].toList
    val many = dbobject.setMany(infos)
    log.debug("Successfully saved InfoItems to DB")
    val meta = infoitems.collect {
      case OdfInfoItem(path, _, _, Some(metaData)) => (path, metaData.data)
    }
    if (meta.nonEmpty) handlePathMetaDataPairs(meta)
    val des = infoitems.collect {
      case info if info.description.nonEmpty => info
    }
    des.foreach { info => dbobject.setDescription(info) }
  }

  /**
   * Function for handling sequences of path and value pairs.
   *
   */
  override def handlePathValuePairs(pairs: Iterable[(Path, OdfValue)]): Unit = {
    dbobject.setMany(pairs.toList)
    log.debug("Successfully saved Path-TimedValue pairs to DB")
  }
  def handlePathMetaDataPairs(pairs: Iterable[(Path, String)]): Unit = {
    pairs.foreach { case (path, metadata) => dbobject.setMetaData(path, metadata) }
    log.debug("Successfully saved Path-MetaData pairs to DB")
  }

}
