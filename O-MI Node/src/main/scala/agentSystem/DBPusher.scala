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

import java.lang.Iterable // => JavaIterable}

import akka.actor._
import akka.dispatch.{BoundedMessageQueueSemantics, RequiresMessageQueue}

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.XML
import database.SingleStores.valueShouldBeUpdated

import database._
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import responses.CallbackHandlers._
import responses.OmiGenerator.xmlFromResults
import responses.Results
import responses.NewDataEvent
import types.OdfTypes._
import types.Path
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection


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
class DBPusher(val dbobject: DB, val subHandler: ActorRef)
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
      Try((esub.endTime.getTime - parsing.OdfParser.currentTime().getTime).milliseconds)
        .toOption.getOrElse(Duration.Inf)
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
    // Aggregate events under same subscriptions (for optimized callbacks)
    val esubAggregation: Map[EventSub, Seq[(EventSub, OdfInfoItem)]] =
        esubLists groupBy {_._1}

    for ((esub, infoSeq) <- esubAggregation) {

        val infoItems = infoSeq map {_._2}

        sendEventCallback(esub, infoItems)
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

    // Collect metadata 
    val other = getOdfNodes(objects) collect {
      case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
    }
    val all = items ++ other
    if (all.nonEmpty) {

      val writeValues : Try[Boolean] = handleInfoItems(items, other)
      
      writeValues match {
        case Success(ret) =>
          log.debug("Successfully saved Odfs to DB")
        case _ => //noop
        //case Failure(e) => throw e // TODO: better ideas to pass Try result?
      }
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
    handleOdf(objs.asScala.foldLeft(OdfObjects()){
      case (a, o) => a union fromPath(o)
    })
  }

  /**
   * Creates values that are to be updated into the database for polled subscription.
   * Polling removes the related data from database, this method creates new data if the old value.
   * @param path
   * @param newValue
   * @param oldValueOpt
   * @return returns Sequence of SubValues to be added to database
   */
  private def handlePollData(path: Path, newValue: OdfValue, oldValueOpt: Option[OdfValue]): Set[SubValue] = {
    val relatedPollSubs = SingleStores.pollPrevayler execute GetSubsForPath(path)

    relatedPollSubs.collect {
      //if no old value found for path or start time of subscription is after last value timestamp
      //if new value is updated value. forall for option returns true if predicate is true or the value is None
      case sub if(oldValueOpt.forall(oldValue =>
        valueShouldBeUpdated(oldValue, newValue) && (oldValue.timestamp.before(sub.startTime) || oldValue.value != newValue.value))) => {
          SubValue(sub.id, path, newValue.timestamp, newValue.value,newValue.typeValue)
      }
    }
  }

  /**
   * Function for handling sequences of OdfInfoItem.
   * @return true if the write was accepted.
   */
  private def handleInfoItems(infoItems: Iterable[OdfInfoItem], objectMetaDatas: Vector[OdfObject] = Vector()): Try[Boolean] = Try{
    // save only changed values
    val pathValueOldValueTuples = for {
      info <- infoItems.toSeq
      path = info.path
      oldValueOpt = SingleStores.latestStore execute LookupSensorData(path)
      value <- info.values
    } yield (path, value, oldValueOpt)

    val newPollValues = pathValueOldValueTuples.flatMap{n =>
      handlePollData(n._1, n._2 ,n._3)}
      //handlePollData _ tupled n}
    if(!newPollValues.isEmpty) {
      dbobject.addNewPollData(newPollValues)
    }

    val callbackDataOptions = pathValueOldValueTuples.map(n=>SingleStores.processData _ tupled n)
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

        case Success(_) => // noop: exception on failure instead of filtering the valid
        case Failure(exp) =>
         log.error( exp, "InputPusher MetaData" )
         throw exp;
      }
    }

    val iiDescriptions = infoItems filter { _.hasDescription }

    val updatedStaticItems = metas ++ iiDescriptions ++ newItems ++ objectMetaDatas

    // Update our hierarchy data structures if needed
    if (updatedStaticItems.nonEmpty) {

        // aggregate all updates to single odf tree
        val updateTree: OdfObjects =
          (updatedStaticItems map fromPath).foldLeft(OdfObjects())(_ union _)

        SingleStores.hierarchyStore execute Union(updateTree)
    }

    // DB + Poll Subscriptions
    val itemValues = (triggeringEvents flatMap {event =>
      val item   = event.infoItem
      val values = item.values.toSeq
      values map {value => (item.path, value)}
    }).toSeq
    dbobject.setMany(itemValues)

    subHandler ! NewDataEvent(itemValues)

    //log.debug("Successfully saved InfoItems to DB")
    true
  }

  /**
   * Function for handling sequences of path and value pairs.
   * @return true if the write was accepted.
   */
  private def handlePathValuePairs(pairs: Iterable[(Path, OdfValue)]): Try[Boolean] = Try{
    // save first to latest values and then db

    val items: Iterable[OdfInfoItem] = pairs map {
      case (path, value) => OdfInfoItem(path, List(value))
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
        OdfInfoItem(path, List(), None, Some(OdfMetaData(metadata)))
    }

    val ret = handleInfoItems(metaInfos)
    
    log.debug("Successfully saved Path-MetaData pairs to DB")
    ret
  }

}

