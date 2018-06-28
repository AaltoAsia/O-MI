package database

import java.lang.{Iterable => JavaIterable}

import parsing.xmlGen._
import responses.CallbackHandler._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OmiTypes._
import types.Path
import types.odf._

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal
import akka.pattern.ask
import akka.util.Timeout
import journal.Models.GetTree
import journal.Models.RemoveEventSub
import journal.Models.GetNewEventSubsForPath
import journal.Models.GetSubsForPath
import journal.Models.AddPollData
import journal.Models.SingleReadCommand
import journal.Models.UnionCommand
import journal.Models.SingleWriteCommand
import journal.Models.LookupNewEventSubs
import journal.Models.LookupEventSubs

import scala.collection.immutable
trait DBWriteHandler extends DBHandlerBase {

  //, dispatcher}
  private def sendEventCallback(esub: EventSub, infoItems: Seq[InfoItem]): Unit = {
    val odf = ImmutableODF(infoItems)
    esub match {
      case ne: NewEventSub =>
        log.debug(s"NEWEVENTSUB iis: ${infoItems.mkString("\n")}")
        log.debug(s"NEWEVENTSUB get nodes: ${odf.getNodes.mkString("\n")}")
      case _ => 
    }
    sendEventCallback(esub,
      odf
    )
  }

  private def sendEventCallback(esub: EventSub, odfWithoutTypes: ImmutableODF) : Unit = {
    log.debug("Sending event callbacks")
    implicit val timeout: Timeout = 2 minutes
    val id = esub.id
    val callbackAddr = esub.callback
    val fhTree: Future[ImmutableODF] = (singleStores.hierarchyStore ? GetTree).mapTo[ImmutableODF]

    val fodf: Future[ODF] = fhTree.map(hTree =>
      hTree.selectSubTree(odfWithoutTypes.getLeafPaths).descriptionsRemoved.metaDatasRemoved.union(odfWithoutTypes))
    //union with odfWithoutTypes to make sure that we don't lose odf branches that are not in hierarchy yet
    //and then intersect to get correct typeValues etc. from hierarchyTree
    //val odf = hTree.union(odfWithoutTypes.valuesRemoved).intersection(odfWithoutTypes)
    val responseTTL =
      Try((esub.endTime.getTime - parsing.OdfParser.currentTime().getTime).milliseconds)
        .toOption.getOrElse(Duration.Inf)

    log.debug(s"Sending data to event sub: $id.")
    val fresponseRequest: Future[ResponseRequest] = fodf.map(odf => Responses.Poll(id, odf, responseTTL))
    log.debug(s"Sending in progress; Subscription subId:$id addr:$callbackAddr interval:-1")
    //log.debug("Send msg:\n" + xmlMsg)

    def failed(reason: String): Unit =
      log.warning(
        s"Callback failed; subscription id:$id interval:-1  reason: $reason")


    val callbackF : Future[Unit] = fresponseRequest.map(responseRequest => callbackHandler.sendCallback(esub.callback, responseRequest))

    callbackF.onSuccess {
      case () =>
        log.debug(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1")
    }
    callbackF.onFailure{
      case fail @ MissingConnection(callback) =>
        log.warning(
          s"Callback failed; subscription id:${esub.id}, reason: ${fail.toString}, subscription is removed.")
        singleStores.subStore ! RemoveEventSub(esub.id)
      case fail: CallbackFailure =>
        failed(fail.toString)
      case e: Throwable =>
        failed(e.getMessage)
    }
  }

  private def processEvents(events: Seq[InfoItemEvent]): Unit = {
    log.debug("Processing events...")

    val esubListsF: Future[Seq[(EventSub, InfoItem)]] = Future.sequence(events.collect{//: Seq[(EventSub, InfoItem)] = events.collect{
      case AttachEvent(infoItem) =>
       // val fpollNewSubs  = (singleStores.subStore ? GetNewEventSubsForPath(infoItem.path)).mapTo[Set[PollNewEventSub]]
          //.map(pollNewSubs => infoItem.values.headOption.map(value => pollNewSubs.map(pnes => (singleStores.pollDataPrevayler ? AddPollData(pnes.id, infoItem.path, value)))))
        val pollnewSubs: Option[Future[Set[Any]]] = infoItem.values.headOption.map(value =>
        for{
          pollNewSubs <- (singleStores.subStore ? GetNewEventSubsForPath(infoItem.path)).mapTo[Set[PollNewEventSub]]
          result <- Future.sequence(pollNewSubs.map(pnes =>
            (singleStores.pollDataPrevayler ? AddPollData(pnes.id, infoItem.path, value))))
        } yield result)
        val fnesubs = (singleStores.subStore ? LookupNewEventSubs(infoItem.path)).mapTo[Seq[NewEventSub]]
        val fesubs = (singleStores.subStore ? LookupEventSubs(infoItem.path)).mapTo[Seq[NormalEventSub]]
        for{
          nesubs: Seq[NewEventSub] <-fnesubs
          esubs: Seq[NormalEventSub] <- fesubs
          resp: Seq[(EventSub, InfoItem)] = (esubs ++ nesubs).map{ (_, infoItem)}
        } yield resp
      case ChangeEvent(infoItem) =>  // note: AttachEvent extends ChangeEvent
        val fesubs: Future[Seq[NormalEventSub]] = (singleStores.subStore ? LookupEventSubs(infoItem.path)).mapTo[Seq[NormalEventSub]]
        val res: Future[Seq[(NormalEventSub, InfoItem)]] = fesubs.map(esubs => esubs map { (_, infoItem) }) // make tuplesres
        res
    }).map(_.flatten)//.flatten
    // Aggregate events under same subscriptions (for optimized callbacks)
    val esubAggregationF: Future[Map[EventSub, Seq[(EventSub, InfoItem)]]] /*: Map[EventSub, Seq[(EventSub, OdfInfoItem)]]*/ =
        esubListsF.map(_.groupBy{ case (eventSub, _) => eventSub })
    esubAggregationF.foreach(esubAggregation =>
    for ((esub, infoSeq) <- esubAggregation) {

        val infoItems = infoSeq map { case (_, infoItem) =>  infoItem}
        log.debug(s"$esub -> ${infoItems.mkString("\n")}")

        sendEventCallback(esub, infoItems)
      })

  }

  /**
   * Adds data for polled subscriptions to the Prevayler store.
   * Polling removes the related data from database
   * (except for polled interval subscriptions, in which case it leaves the latest value in the database)
   * @param path
   * @param newValue
   * @param oldValueOpt
   * @return returns Sequence of SubValues to be added to database
   */
  private def handlePollData(path: Path, newValue: Value[Any], oldValueOpt: Option[Value[Any]]) = {
    //log.debug(s"Handling poll data... for $path")
    //TODO: Do this for multiple paths at the same time
    val relatedPollSubsF: Future[Set[NotNewEventSub]] = (singleStores.subStore ? GetSubsForPath(path)).mapTo[Set[NotNewEventSub]]
    for {
      relatedPollSubs <- relatedPollSubsF
      readyFuture <- Future.sequence(relatedPollSubs.collect{
        case sub if oldValueOpt.forall(oldValue =>
          singleStores.valueShouldBeUpdated(oldValue, newValue) &&
            (oldValue.timestamp.before(sub.startTime) || oldValue.value != newValue.value)) => {
          (singleStores.pollDataPrevayler ? AddPollData(sub.id, path, newValue))
        }
      })
    }yield readyFuture
   // log.debug(s"Related poll subs: ${relatedPollSubs.size} ")
   // frelatedPollSubs.foreach(relatedPollSubs =>
   //   relatedPollSubs.collect {
   //     //if no old value found for path or start time of subscription is after last value timestamp
   //     //if new value is updated value. forall for option returns true if predicate is true or the value is None
   //     case sub if oldValueOpt.forall(oldValue =>
   //       singleStores.valueShouldBeUpdated(oldValue, newValue) &&
   //         (oldValue.timestamp.before(sub.startTime) || oldValue.value != newValue.value)) => {
   //       (singleStores.pollDataPrevayler ? AddPollData(sub.id, path, newValue))
   //     }
   //   }
   // )
  }

  protected def handleWrite( write: WriteRequest ) : Future[ResponseRequest] = {
            //                   infoItems: Iterable[InfoItem],
            //                   objectMetadatas: Vector[Object] = Vector()
            //
    log.debug("HandleWrite...")
    val odf =write.odf
    // save only changed values
    log.debug("Check old values")
    val pathValueOldValueTuples = for {
      info <- odf.getInfoItems.toSeq
      path = info.path
      oldValueOpt = (singleStores.latestStore ? SingleReadCommand(path)).mapTo[Option[Value[Any]]]
      value <- info.values
    } yield (path, value, oldValueOpt)

    val pollFuture = Future.sequence(pathValueOldValueTuples.map{
      case (path, oldValue, fvalue) =>
        fvalue.flatMap(value =>
        handlePollData(path, oldValue ,value))}) //Add values to pollsubs in this method


    //pollFuture.onFailure{
    //  case t: Throwable => log.error(t, "Error when adding poll values to database")
    //} TODO ERROR HANDLING SOMEWHERE ELSE

    val callbackDataOptions: Future[Seq[Option[InfoItemEvent]]] = Future.sequence(pathValueOldValueTuples.map{
      case (path,value, foldValueO) => foldValueO.map(oldValueO => singleStores.processData(path,value,oldValueO))})
    val ftriggeringEvents: Future[Seq[InfoItemEvent]] = callbackDataOptions.map(_.flatten)

    // TODO: implement responsible agent check here or processEvents method
    // return false  // command was not accepted or failed in agent or physical world but no internal server errors

    // Send all callbacks
    ftriggeringEvents.foreach(events => processEvents(events))

    // Save new/changed stuff to transactional in-memory singleStores and then DB

    val fnewItems: Future[Seq[InfoItem]] = ftriggeringEvents.map(_.collect {
        case AttachEvent(item) => item
    })

    val staticData = odf.valuesRemoved.nodesWithStaticData
    /*
      infoItems filter { 
      ii: InfoItem =>
      ii.names.nonEmpty ||
      ii.metaData.nonEmpty || 
      ii.descriptions.nonEmpty ||
      ii.typeAttribute.nonEmpty ||
      ii.attributes.nonEmpty 
    }*/

    log.debug(s"Static data with attributes:\n${staticData.mkString("\n")}")
    val updatedStaticItemsF = fnewItems.map(newItems =>  staticData ++ newItems)

    // DB + Poll Subscriptions
    val infosToBeWrittenInDBF: Future[Seq[InfoItem]] =
      ftriggeringEvents.map(triggeringEvents => //InfoItems contain single value
        triggeringEvents
          .map(_.infoItem) //map type to OdfInfoItem
          .groupBy(_.path) //combine same paths
          .flatMap{
          case (path: Path, iis: Vector[InfoItem]) => //flatMap to remove None values
            iis.reduceOption(_.union(_)) //Combine infoitems with same paths to single infoitem
        }(collection.breakOut)) // breakOut to correct collection type

    log.debug("Writing infoitems to db")
    val dbWriteFuture = infosToBeWrittenInDBF.flatMap(
      infosToBeWrittenInDB => dbConnection.writeMany(infosToBeWrittenInDB))

    dbWriteFuture.onFailure{
      case t: Throwable => log.error(t, "Error when writing values for paths $paths")
    }

    val writeFuture: Future[Seq[Any]] = dbWriteFuture.flatMap{
      _ =>
        // Update our hierarchy data structures if needed
        log.debug("Writing finished.")

        log.debug("Update cache...")
        for{
          updatedStaticItems <- updatedStaticItemsF
          if updatedStaticItems.nonEmpty
          updateTree:ImmutableODF = ImmutableODF(updatedStaticItems)
          updatedCache <- (singleStores.hierarchyStore ? UnionCommand(updateTree))
          triggeringEvents <- ftriggeringEvents
          latestF <- Future.sequence(triggeringEvents.flatMap(iie =>
            iie.infoItem.values.headOption
              .map(newValue => (singleStores.latestStore ? SingleWriteCommand(iie.infoItem.path, newValue)))))

        } yield latestF
        /*unionF.flatMap(_ =>
        ftriggeringEvents.foreach { triggeringEvents =>
          log.debug(s"Triggering ${triggeringEvents.length} events.")
          triggeringEvents.foreach(
            iie =>
              iie.infoItem.values.headOption.map(
                newValue =>
                  (singleStores.latestStore ? SingleWriteCommand(iie.infoItem.path, newValue))
              )
          )
        })*/
    }

    writeFuture.onFailure{
      case t: Exception => log.error(t, "Error when trying to update hierarchy.")
      case t: Throwable => log.error(t, "Error when trying to update hierarchy.")
    }

    val resultFuture = for{
      _ <- pollFuture
      _ <- writeFuture
      res = odf.getInfoItems.map(_.path) ++ staticData.map(_.path)
    } yield res.toSet
    resultFuture.map{ 
      paths => 
        log.debug("Successfully saved Odfs to DB")
        Responses.Success()
    }.recover{
      case NonFatal(t) =>
        log.error(t, "Error while handling write.")
        t.printStackTrace()
        Responses.InternalError(t)
    }

  }
}
