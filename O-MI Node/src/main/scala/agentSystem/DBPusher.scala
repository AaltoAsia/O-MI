/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package agentSystem

import java.lang.{Iterable => JavaIterable}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import akka.actor.{ActorRef, ActorSystem}
import database.SingleStores.valueShouldBeUpdated
import database._
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes.MetaData
import responses.CallbackHandlers._
import types.OdfTypes._
import types.OmiTypes._
import types.Path

trait  InputPusher  extends BaseAgentSystem{
  protected def writeValues(
    infoItems: Iterable[OdfInfoItem],
    objectMetadatas: Vector[OdfObject] = Vector()
  )(implicit system: ActorSystem): Future[SuccessfulWrite] 
}
trait  DBPusher  extends BaseAgentSystem{
  def dbobject: DB
  def subHandler: ActorRef
  import context.dispatcher

  private def sendEventCallback(esub: EventSub, infoItems: Seq[OdfInfoItem]): Unit = {
    sendEventCallback(esub,
      (infoItems map createAncestors).foldLeft(OdfObjects())(_ union _)
    )
  }

  private def sendEventCallback(esub: EventSub, odf: OdfObjects) : Unit = {
    val id = esub.id
    val callbackAddr = esub.callback
    val responseTTL =
      Try((esub.endTime.getTime - parsing.OdfParser.currentTime().getTime).milliseconds)
        .toOption.getOrElse(Duration.Inf)

    log.debug(s"Sending data to event sub: $id.")
    val responseRequest = Responses.Poll(id, odf, responseTTL)
    log.info(s"Sending in progress; Subscription subId:$id addr:$callbackAddr interval:-1")
    //log.debug("Send msg:\n" + xmlMsg)

    def failed(reason: String) =
      log.warning(
        s"Callback failed; subscription id:$id interval:-1  reason: $reason")


    val callbackF : Future[Unit] = esub.callback.send(responseRequest) // FIXME: change xmlMsg to ResponseRequest(..., responseTTL)

    callbackF.onSuccess {
      case CallbackSuccess =>
        log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1")
      case success : CallbackResult =>
        log.error(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1, default math, The impossible happened?")
    }
    callbackF.onFailure{
      case fail: CallbackFailure =>
        failed(fail.toString)
      case e: Throwable =>
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
    val esubAggregation /*: Map[EventSub, Seq[(EventSub, OdfInfoItem)]]*/ =
        esubLists groupBy { case (eventSub, _) => eventSub }

    for ((esub, infoSeq) <- esubAggregation) {

        val infoItems = infoSeq map { case (_, infoItem) =>  infoItem}

        sendEventCallback(esub, infoItems)
    }

  }

  /**
   * Function for handling OdfObjects.
   *
   */
  protected def writeValues(
    infoItems: Iterable[OdfInfoItem],
    objectMetadatas: Vector[OdfObject] = Vector()
  )(implicit system: ActorSystem): Future[SuccessfulWrite] ={
    if( infoItems.nonEmpty || objectMetadatas.nonEmpty ) {
      val future = handleInfoItems(infoItems, objectMetadatas)
      future.onSuccess{
         case u : Iterable[Path] =>
          log.debug("Successfully saved Odfs to DB")
      }
      future.map{ 
          paths => SuccessfulWrite( paths.toVector )
      }
    } else {
      Future.successful{
         SuccessfulWrite( Vector.empty )
      }  
    }
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
  private def handleInfoItems(
                               infoItems: Iterable[OdfInfoItem],
                               objectMetadatas: Vector[OdfObject] = Vector()
                               )(implicit system: ActorSystem): Future[Iterable[Path]] = {
    // save only changed values
    val pathValueOldValueTuples = for {
      info <- infoItems.toSeq
      path = info.path
      oldValueOpt = SingleStores.latestStore execute LookupSensorData(path)
      value <- info.values
    } yield (path, value, oldValueOpt)
    //for debugging
    //log.debug(s"\n\nXXXXXXXXXXXXXXXXXXXXXXXXXXXxxx\n${
    //pathValueOldValueTuples.map(n =>n._1.toString + "-> " + n._2.value + "old: " + n._3.map(_.value).toString()).mkString("\n")
    //}")
    val newPollValues = pathValueOldValueTuples.flatMap{
      case (path, oldValue, value) =>
      handlePollData(path, oldValue ,value)
    }
    val pollFuture: Future[Option[Int]] = if(!newPollValues.isEmpty) {
      dbobject.addNewPollData(newPollValues)
      } else {
        Future.successful(Option(0))
      }

    pollFuture.onFailure{
        case t: Throwable => log.error(t, "Error when adding poll values to database")
      }

    val callbackDataOptions = pathValueOldValueTuples.map{
      case (path,value, oldValueO) => SingleStores.processData(path,value,oldValueO)}
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

    val updatedStaticItems = metas ++ iiDescriptions ++ newItems ++ objectMetadatas

    // Update our hierarchy data structures if needed
    if (updatedStaticItems.nonEmpty) {

        // aggregate all updates to single odf tree
        val updateTree: OdfObjects =
          (updatedStaticItems map createAncestors).foldLeft(OdfObjects())(_ union _)

        SingleStores.hierarchyStore execute Union(updateTree)
    }

    // DB + Poll Subscriptionst
    val itemValues = triggeringEvents flatMap {event =>
      val item   = event.infoItem
      val values = item.values.toSeq
      values map {value => (item.path, value)}
    }

    val writeFuture = dbobject.writeMany(itemValues)

    writeFuture.onFailure{
      case t: Throwable => log.error(t, "Error when writing values for paths $paths")
    }

    for{
      _ <- pollFuture
      _ <- writeFuture
      res = infoItems.map(_.path) ++ objectMetadatas.map(_.path)
    } yield res

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
  
}
