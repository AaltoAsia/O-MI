package database

import java.lang.{Iterable => JavaIterable}



import scala.collection.immutable.IndexedSeq
import scala.collection.mutable.{Map => MutableMap}
import scala.collection.immutable.Map
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

import akka.actor.{Actor, ActorRef, ActorSystem, ActorLogging}
import akka.pattern.ask
import akka.util.Timeout
import database._
import parsing.xmlGen
import parsing.xmlGen._
import parsing.xmlGen.xmlTypes.MetaDataType
import responses.CallbackHandler
import responses.CallbackHandler._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types.OmiTypes._
import types.Path
import analytics.{AddWrite, AnalyticsStore}
import agentSystem.{AgentName}

trait DBWriteHandler extends DBHandlerBase {



  import context.{system}//, dispatcher}
  protected def handleWrite( write: WriteRequest ) : Future[ResponseRequest] = {
    val odfObjects = write.odf
    val infoItems : Seq[OdfInfoItem] = odfObjects.infoItems // getInfoItems(odfObjects)

    // Collect metadata 
    val objectsWithMetadata = odfObjects.objectsWithMetadata
    writeValues(infoItems, objectsWithMetadata).recover{
      case t: Exception =>
        log.error(t, "Error while handling write.")
        Responses.InternalError(t)
      case t: Throwable =>
        log.error(t, "Error while handling write.")
        Responses.InternalError(t)
    }
  }

  private def sendEventCallback(esub: EventSub, infoItems: Seq[OdfInfoItem]): Unit = {
    sendEventCallback(esub,
      (infoItems map createAncestors).foldLeft(OdfObjects())(_ union _)
    )
  }

  private def sendEventCallback(esub: EventSub, odfWithoutTypes: OdfObjects) : Unit = {
    val id = esub.id
    val callbackAddr = esub.callback
    val hTree = singleStores.hierarchyStore execute GetTree()
    //union with odfWithoutTypes to make sure that we don't lose odf branches that are not in hierarchy yet
    //and then intersect to get correct typeValues etc. from hierarchyTree
    val odf = hTree.union(odfWithoutTypes.valuesRemoved).intersect(odfWithoutTypes)
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


    val callbackF : Future[Unit] = callbackHandler.sendCallback(esub.callback, responseRequest) // FIXME: change xmlMsg to ResponseRequest(..., responseTTL)

    callbackF.onSuccess {
      case () =>
        log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:-1")
    }
    callbackF.onFailure{
      case fail @ MissingConnection(callback) =>
        log.warning(
          s"Callback failed; subscription id:${esub.id}, reason: ${fail.toString}, subscription is remowed.")
        singleStores.subStore execute RemoveEventSub(esub.id)
      case fail: CallbackFailure =>
        failed(fail.toString)
      case e: Throwable =>
        failed(e.getMessage)
    }
  }

  private def processEvents(events: Seq[InfoItemEvent]) = {
    //Add write data to analytics if wanted
    analyticsStore.foreach{store =>
      events
        .map(event => (event.infoItem.path, event.infoItem.values.map(_.timestamp.getTime())))
        .foreach(pv => store ! AddWrite(pv._1, pv._2))
    }
    val esubLists: Seq[(EventSub, OdfInfoItem)] = events.collect{
      case ChangeEvent(infoItem) =>  // note: AttachEvent extends Changeevent

        val esubs = singleStores.subStore execute LookupEventSubs(infoItem.path)
        esubs map { (_, infoItem) }  // make tuples
    }.flatten
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
  )(implicit system: ActorSystem): Future[ResponseRequest] ={
    if( infoItems.nonEmpty || objectMetadatas.nonEmpty ) {
      val future = handleInfoItems(infoItems, objectMetadatas)
      future.onSuccess{
         case u : Iterable[Path] =>
          log.debug("Successfully saved Odfs to DB")
      }
      future.map{ 
          paths => Responses.Success()
      }
    } else {
      Future.successful{
        Responses.Success()
      }  
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
  private def handlePollData(path: Path, newValue: OdfValue[Any], oldValueOpt: Option[OdfValue[Any]]) = {
    val relatedPollSubs = singleStores.subStore execute GetSubsForPath(path)

    relatedPollSubs.collect {
      //if no old value found for path or start time of subscription is after last value timestamp
      //if new value is updated value. forall for option returns true if predicate is true or the value is None
      case sub if(oldValueOpt.forall(oldValue =>
        singleStores.valueShouldBeUpdated(oldValue, newValue) &&
          (oldValue.timestamp.before(sub.startTime) || oldValue.value != newValue.value))) => {
        singleStores.pollDataPrevayler execute AddPollData(sub.id, path, newValue)
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
    val triggeringEvents = callbackDataOptions.flatten
    
    if (triggeringEvents.nonEmpty) {  // (unnecessary if?)
      // TODO: implement responsible agent check here or processEvents method
      // return false  // command was not accepted or failed in agent or physical world but no internal server errors

      // Send all callbacks
      processEvents(triggeringEvents)
    }

    // Save new/changed stuff to transactional in-memory singleStores and then DB

    val newItems = triggeringEvents collect {
        case AttachEvent(item) => item
    }

    val metas = infoItems filter { _.hasMetadata }

    val iiDescriptions = infoItems filter { _.hasDescription }
    val iiTypes = infoItems filter { _.typeValue.nonEmpty }
    val iiAttr = infoItems filter { _.attributes.nonEmpty }

    val updatedStaticItems ={
      metas ++ 
      iiDescriptions ++ 
      iiTypes ++ 
      iiAttr ++ 
      newItems ++ 
      objectMetadatas
    }



    // DB + Poll Subscriptions
    val infosToBeWrittenInDB: Seq[OdfInfoItem] =
      triggeringEvents //InfoItems contain single value
      .map(_.infoItem) //map type to OdfInfoItem
      .groupBy(_.path) //combine same paths
      .flatMap( pathValues => //flatMap to remove None values
        pathValues._2.reduceOption(_.combine(_)) //Combine infoitems with same paths to single infoitem
      )(collection.breakOut) // breakOut to correct collection type

    val dbWriteFuture = dbConnection.writeMany(infosToBeWrittenInDB)

    dbWriteFuture.onFailure{
      case t: Throwable => log.error(t, "Error when writing values for paths $paths")
    }

    val writeFuture = dbWriteFuture.map{ 
      n =>
        // Update our hierarchy data structures if needed

        if (updatedStaticItems.nonEmpty) {
          // aggregate all updates to single odf tree
          val updateTree: OdfObjects =
            (updatedStaticItems map createAncestors).foldLeft(OdfObjects())(_ union _)

          singleStores.hierarchyStore execute Union(updateTree)
        }
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




    for{
      _ <- pollFuture
      _ <- writeFuture
      res = infoItems.map(_.path) ++ objectMetadatas.map(_.path)
    } yield res

  }
}
