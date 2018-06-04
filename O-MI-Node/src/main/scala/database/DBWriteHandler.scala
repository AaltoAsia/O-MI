package database

import java.lang.{Iterable => JavaIterable}

import analytics.AddWrite
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
    val id = esub.id
    val callbackAddr = esub.callback
    val hTree = singleStores.hierarchyStore execute GetTree()
    val odf = hTree.selectSubTree(odfWithoutTypes.getLeafPaths).descriptionsRemoved.metaDatasRemoved.union(odfWithoutTypes)
    //union with odfWithoutTypes to make sure that we don't lose odf branches that are not in hierarchy yet
    //and then intersect to get correct typeValues etc. from hierarchyTree
    //val odf = hTree.union(odfWithoutTypes.valuesRemoved).intersection(odfWithoutTypes)
    esub match {
      case ne: NewEventSub =>
    log.debug(s"NEWEVENTSUB total mystery: ${odfWithoutTypes.getNodes.mkString("\n")}")
    log.debug(s"NEWEVENTSUB got nodes: ${odf.getNodes.mkString("\n")}")
      case _ => 
    }
    val responseTTL =
      Try((esub.endTime.getTime - parsing.OdfParser.currentTime().getTime).milliseconds)
        .toOption.getOrElse(Duration.Inf)

    log.debug(s"Sending data to event sub: $id.")
    val responseRequest = Responses.Poll(id, odf, responseTTL)
    log.debug(s"Sending in progress; Subscription subId:$id addr:$callbackAddr interval:-1")
    //log.debug("Send msg:\n" + xmlMsg)

    def failed(reason: String): Unit =
      log.warning(
        s"Callback failed; subscription id:$id interval:-1  reason: $reason")


    val callbackF : Future[Unit] = callbackHandler.sendCallback(esub.callback, responseRequest) // FIXME: change xmlMsg to ResponseRequest(..., responseTTL)

    callbackF.onSuccess {
      case () =>
        log.debug(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1")
    }
    callbackF.onFailure{
      case fail @ MissingConnection(callback) =>
        log.warning(
          s"Callback failed; subscription id:${esub.id}, reason: ${fail.toString}, subscription is removed.")
        singleStores.subStore execute RemoveEventSub(esub.id)
      case fail: CallbackFailure =>
        failed(fail.toString)
      case e: Throwable =>
        failed(e.getMessage)
    }
  }

  private def processEvents(events: Seq[InfoItemEvent]): Unit = {
    log.debug("Processing events...")
    //Add write data to analytics if wanted
    analyticsStore.foreach{ store =>
      events
        .map(event => (event.infoItem.path, event.infoItem.values.map(_.timestamp.getTime())))
        .foreach(pv => store ! AddWrite(pv._1, pv._2))
    }
    val esubLists: Seq[(EventSub, InfoItem)] = events.collect{
      case AttachEvent(infoItem) =>
        val pollNewSubs = singleStores.subStore execute GetNewEventSubsForPath(infoItem.path)
        infoItem.values.headOption.foreach(value => pollNewSubs.foreach(pnes => singleStores.pollDataPrevayler execute AddPollData(pnes.id, infoItem.path, value)))
        val nesubs: Seq[NewEventSub] = singleStores.subStore execute LookupNewEventSubs(infoItem.path)
        val esubs: Seq[NormalEventSub] = singleStores.subStore execute LookupEventSubs(infoItem.path)
        if( nesubs.nonEmpty)
          log.debug(s"${nesubs.mkString("\n")}")
        (esubs ++ nesubs) map { (_, infoItem) }  // make tuples
      case ChangeEvent(infoItem) =>  // note: AttachEvent extends ChangeEvent

        val esubs: Seq[NormalEventSub] = singleStores.subStore execute LookupEventSubs(infoItem.path)
        esubs map { (_, infoItem) }  // make tuples
    }.flatten
    // Aggregate events under same subscriptions (for optimized callbacks)
    val esubAggregation /*: Map[EventSub, Seq[(EventSub, OdfInfoItem)]]*/ =
        esubLists groupBy { case (eventSub, _) => eventSub }

    for ((esub, infoSeq) <- esubAggregation) {

        val infoItems = infoSeq map { case (_, infoItem) =>  infoItem}
        log.debug(s"$esub -> ${infoItems.mkString("\n")}")

        sendEventCallback(esub, infoItems)
    }

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
    val relatedPollSubs: Set[NotNewEventSub] = singleStores.subStore execute GetSubsForPath(path)

   // log.debug(s"Related poll subs: ${relatedPollSubs.size} ")
    relatedPollSubs.collect {
      //if no old value found for path or start time of subscription is after last value timestamp
      //if new value is updated value. forall for option returns true if predicate is true or the value is None
      case sub if oldValueOpt.forall(oldValue =>
        singleStores.valueShouldBeUpdated(oldValue, newValue) &&
          (oldValue.timestamp.before(sub.startTime) || oldValue.value != newValue.value)) => {
        singleStores.pollDataPrevayler execute AddPollData(sub.id, path, newValue)
      }
    }
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
      oldValueOpt = singleStores.latestStore execute LookupSensorData(path)
      value <- info.values
    } yield (path, value, oldValueOpt)


    val pollFuture = Future{pathValueOldValueTuples.foreach{
      case (path, oldValue, value) =>
        handlePollData(path, oldValue ,value)} //Add values to pollsubs in this method
    }

    pollFuture.onFailure{
      case t: Throwable => log.error(t, "Error when adding poll values to database")
    }

    val callbackDataOptions = pathValueOldValueTuples.map{
      case (path,value, oldValueO) => singleStores.processData(path,value,oldValueO)}
    val triggeringEvents: Seq[InfoItemEvent] = callbackDataOptions.flatten
    
    if (triggeringEvents.nonEmpty) {  // (unnecessary if?)
      // TODO: implement responsible agent check here or processEvents method
      // return false  // command was not accepted or failed in agent or physical world but no internal server errors

      // Send all callbacks
      processEvents(triggeringEvents)
    }

    // Save new/changed stuff to transactional in-memory singleStores and then DB

    val newItems: Seq[InfoItem] = triggeringEvents collect {
        case AttachEvent(item) => item
    }

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
    val updatedStaticItems = staticData ++ newItems

    // DB + Poll Subscriptions
    val infosToBeWrittenInDB: Seq[InfoItem] =
      triggeringEvents //InfoItems contain single value
      .map(_.infoItem) //map type to OdfInfoItem
      .groupBy(_.path) //combine same paths
      .flatMap{
        case (path: Path, iis: Vector[InfoItem]) => //flatMap to remove None values
        iis.reduceOption(_.union(_)) //Combine infoitems with same paths to single infoitem
      }(collection.breakOut) // breakOut to correct collection type

    log.debug("Writing infoitems to db")
    val dbWriteFuture = dbConnection.writeMany(infosToBeWrittenInDB)

    dbWriteFuture.onFailure{
      case t: Throwable => log.error(t, s"Error when writing values for paths ${infosToBeWrittenInDB.map(_.path)}")
    }

    val writeFuture = dbWriteFuture.map{
      _ =>
        // Update our hierarchy data structures if needed

        log.debug("Writing finished.")
        if (updatedStaticItems.nonEmpty) {
          log.debug("Update cache...")
          // aggregate all updates to single odf tree
          val updateTree: ImmutableODF = ImmutableODF(updatedStaticItems)

          singleStores.hierarchyStore execute Union(updateTree)
        }
        log.debug(s"Triggering ${triggeringEvents.length} events.")
        triggeringEvents.foreach(
          iie =>
            iie.infoItem.values.headOption.map(
              newValue=>
                singleStores.latestStore execute SetSensorData(iie.infoItem.path, newValue)
            )
        )
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
