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

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.concurrent.ExecutionContext.Implicits.global
import akka.dispatch.{BoundedMessageQueueSemantics, RequiresMessageQueue}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.actor._
import java.lang.{Iterable => JavaIterable}
import scala.xml.XML

import database._
import types._
import responses.Results
import responses.OmiGenerator.xmlFromResults
import responses.CallbackHandlers._
import responses.Results
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import types.OdfTypes._
import types.Path



/** Object that contains all commands of InputPusher.
 */
object InputPusherCmds {
  case class HandleOdf(objects: OdfObjects)
  case class HandleObjects(objs: Iterable[OdfObject])
  case class HandleInfoItems(items: Iterable[OdfInfoItem])
  case class HandlePathValuePairs(pairs: Iterable[(Path, OdfValue)])
  case class HandlePathMetaDataPairs(pairs: Iterable[(Path, String)])
}

import agentSystem.InputPusherCmds._
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

  private def sendEventCallback(esub: EventSub, infoItems: Seq[OdfInfoItem]): Unit = {
    sendEventCallback(esub,
      (infoItems map fromPath).foldLeft(OdfObjects())(_ union _)
    )
  }

  private def sendEventCallback(esub: EventSub, odf: OdfObjects): Unit = {
    val log = http.Boot.system.log
    val id = esub.id
    val callbackAddr = esub.callback
    log.debug(s"Sending data to event sub: $id.")
    val xmlMsg = xmlFromResults(
      1.0,
      Results.poll(id.toString, odf))
    log.info(s"Sending in progress; Subscription subId:$id addr:$callbackAddr interval:-1")
    //log.debug("Send msg:\n" + xmlMsg)

    def failed(reason: String) =
      log.warning(
        s"Callback failed; subscription id:$id interval:-1  reason: $reason")


    sendCallback(
      callbackAddr,
      xmlMsg,
      (esub.endTime.getTime - parsing.OdfParser.currentTime().getTime).milliseconds
    ) onComplete {
      case Success(CallbackSuccess) =>
        log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1")

      case Success(fail: CallbackFailure) =>
        failed(fail.toString)
      case Failure(e) =>
        failed(e.getMessage)
    }
  }

  private def processEvents(events: Seq[InfoItemEvent]) = {

    val esubLists: Seq[(EventSub, OdfInfoItem)] = events flatMap {
      case ChangeEvent(infoItem) =>  // note: AttachEvent extends Changeevent

        val esubs = SingleStores.eventPrevayler execute LookupEventSubs(infoItem.path)
        esubs map { (_, infoItem) }  // make tuples
    }
    // Aggregate under same subscriptions (for optimized callbacks)
    val esubAggregation: Map[EventSub, Seq[(EventSub, OdfInfoItem)]] =
        esubLists groupBy {_._1}

    for ((_, infoSeq) <- esubAggregation) {

        val esubOpt = infoSeq.headOption map {_._1}
        val infoItems = infoSeq map {_._2}

        esubOpt match {
            case Some(esub) => sendEventCallback(esub, infoItems)
        }
    }

  }

  /**
   * Function for handling OdfObjects.
   *
   */
  private def handleOdf(objects: OdfObjects):  Try[Boolean] = {
    // val data = getLeafs(objects)
    // if ( data.nonEmpty ) {
    val items = getInfoItems(objects)
    if (items.nonEmpty) {
      val writeValues : Try[Boolean] = handleInfoItems(items)
      
      // TODO: descriptions of objects
      log.debug("Successfully saved Odfs to DB")

      
      //writeValues match {
      //  case Success(ret) => ret
      //  case Failure(e) => throw e // TODO: better ideas to pass Try result?
      //}
      writeValues

    } else {
      log.warning("Empty odf pushed for DBPusher.")
      Success(false)
    }
  }
  /**
   * Function for handling sequences of OdfObject.
   */
  private def handleObjects(objs: Iterable[OdfObject]): Try[Boolean] = {
    handleOdf(OdfObjects(objs))
  }

  /**
   * Function for handling sequences of OdfInfoItem.
   * @return true if the write was accepted.
   */
  private def handleInfoItems(infoItems: Iterable[OdfInfoItem]): Try[Boolean] = Try{
    // save only changed values
    val callbackDataOptions: Seq[List[InfoItemEvent]] = for {
      info <- infoItems.toSeq
      path = info.path
      value <- info.values
    } yield SingleStores.processData(path, value).toList

    val triggeringEvents = callbackDataOptions.flatten
    
    if (triggeringEvents.nonEmpty) {  // (unnecessary if?)
      // TODO: implement responsible agent check here or processEvents method
      // return false  // command was not accepted or failed in agent or physical world but no internal server errors

      // Send all callbacks
      processEvents(triggeringEvents)
    }


    // Save new/changed stuff to transactional in-memory SingleStores and then DB

    val newItems = triggeringEvents collect {
        case AttachEvent(item) => item
    }

    val metas = infoItems filter { _.hasMetadata }
    // check syntax
    metas foreach {metaInfo =>
      checkMetaData(metaInfo.metaData) match {
        case Failure(exp) =>
         log.error( exp, "InputPusher" )
         throw exp;
      }
    }

    val descriptions = infoItems filter { _.description.nonEmpty }

    val updatedStaticItems = metas ++ descriptions ++ newItems

    // Update our hierarchy data structures if needed
    if (updatedStaticItems.nonEmpty) {

        // aggregate all updates to single odf tree
        val updateTree: OdfObjects =
          (updatedStaticItems map fromPath).foldLeft(OdfObjects())(_ union _)

        SingleStores.hierarchyStore execute Union(updateTree)
    }

    // DB
    val itemValues = infoItems flatMap {item =>
      val values = item.values.toSeq
      values map {value => (item.path, value)}
    }
    dbobject.setMany(itemValues.toList)

    //log.debug("Successfully saved InfoItems to DB")
    true
  }

  /**
   * Function for handling sequences of path and value pairs.
   * @return true if the write was accepted.
   */
  private def handlePathValuePairs(pairs: Iterable[(Path, OdfValue)]): Try[Boolean] = Try{
    // save first to latest values and then db

    val items = pairs map {
      case (path, value) => OdfInfoItem(path, Iterable(value))
    }

    handleInfoItems(items) match {
      case Success(ret) =>
        log.debug("Successfully saved Path-TimedValue pairs to DB")
        ret
      case Failure(e) => throw e
    }

  }

  /**
   * Check metadata XML validity and O-DF validity
   */
  private def checkMetaData(metaO: Option[OdfMetaData]): Try[String] = metaO match {
    case Some(meta) => checkMetaData(meta.data)
    case None => Failure(new MatchError(None))
  }
  private def checkMetaData(metaStr: String): Try[String] = Try{
        val xml = XML.loadString(metaStr)
        val meta = xmlGen.scalaxb.fromXML[MetaData](xml)
        metaStr
      }
  /**
   * Function for handling sequences of path and MetaData(as String)  pairs.
   *
   */
  private def handlePathMetaDataPairs(pairs: Iterable[(Path, String)]):  Try[Boolean] = {
    
    val metaInfos = pairs map {
      case (path, metadata) => 
        OdfInfoItem(path, Iterable(), None, Some(OdfMetaData(metadata)))
    }

    val ret = handleInfoItems(metaInfos)
    
    log.debug("Successfully saved Path-MetaData pairs to DB")
    ret
  }

}

