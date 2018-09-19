package database


import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import journal.Models.{GetTree, SaveSnapshot, SingleReadCommand}
import http.OmiConfigExtension
import types.Path
import types.odf._
import journal._

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration, MILLISECONDS}
import scala.util.{Failure, Success}
trait SingleStores {
  implicit val system: ActorSystem
  import system.dispatcher
  protected val settings: OmiConfigExtension
  implicit val timeout: Timeout = Timeout(settings.journalTimeout)
  val log: LoggingAdapter = system.log
  def takeSnapshot: Future[Any] = {
    val start: FiniteDuration = Duration(System.currentTimeMillis(), MILLISECONDS)

    def trySnapshot(p: ActorRef, errorName: String): Future[Unit] = {
      val start: FiniteDuration = Duration(System.currentTimeMillis(), MILLISECONDS)

      val snapshotF = (p ? SaveSnapshot()).mapTo[Unit]
      snapshotF.onComplete {
        case Success(s) => {
          val end: FiniteDuration = Duration(System.currentTimeMillis(), MILLISECONDS)
          val duration: FiniteDuration = end - start
          log.debug(s"Taking Snapshot for $errorName took $duration")
        }
        case Failure(ex) => log.error(ex, s"Failed to take Snapshot of $errorName")
      }
      snapshotF
    }

    log.info("Taking journal snapshot")
    val res: Future[Seq[Unit]] = Future.sequence(Seq(
      trySnapshot(latestStore, "latestStore"),
      trySnapshot(hierarchyStore, "hierarchyStore"),
      trySnapshot(subStore, "subStore"),
      trySnapshot(pollDataStore, "pollData"))
    )
    res.onComplete {
      case Success(s) => {
        val end: FiniteDuration = Duration(System.currentTimeMillis(), MILLISECONDS)
        val duration: FiniteDuration = end - start
        log.info(s"Taking Snapshot took $duration")
      }
      case Failure(ex) => log.error(ex, "Taking snapshot failed")
    }
    res
  }

  val latestStore: ActorRef   
  val hierarchyStore: ActorRef   
  val subStore: ActorRef    
  val pollDataStore: ActorRef    

  def buildODFFromValues(items: Seq[(Path, Value[Any])]): ODF = {
    ImmutableODF(items map { case (path, value) =>
      InfoItem(path, Vector(value))
    })
  }


  /**
    * Logic for updating values based on timestamps.
    * If timestamp is same or the new value timestamp is after old value return true else false
    *
    * @param oldValue old value(from latestStore)
    * @param newValue the new value to be added
    * @return
    */
  def valueShouldBeUpdated(oldValue: Value[Any], newValue: Value[Any]): Boolean = {
    oldValue.timestamp before newValue.timestamp
  }


  /**
    * Main function for handling incoming data and running all event-based subscriptions.
    * As a side effect, updates the internal latest value store.
    * Event callbacks are not sent for each changed value, instead event results are returned
    * for aggregation and other extra functionality.
    *
    * @param path     Path to incoming data
    * @param newValue Actual incoming data
    * @return Triggered responses
    */
  def processData(path: Path, newValue: Value[Any], oldValueOpt: Option[Value[Any]]): Option[InfoItemEvent] = {

    // TODO: Replace metadata and description if given

    oldValueOpt match {
      case Some(oldValue) =>
        if (valueShouldBeUpdated(oldValue, newValue)) {
          // NOTE: This effectively discards incoming data that is older than the latest received value
          if (oldValue.value != newValue.value) {
            Some(ChangeEvent(InfoItem(path, Vector(newValue))))
          } else {
            // Value is same as the previous
            Some(SameValueEvent(InfoItem(path, Vector(newValue))))
          }

        } else None // Newer data found

      case None => // no data was found => new sensor
        val newInfo = InfoItem(path, Vector(newValue))
        Some(AttachEvent(newInfo))
    }

  }


  def getMetaData(path: Path)(implicit timeout: Timeout): Future[Option[MetaData]] = {
    (hierarchyStore ? GetTree).mapTo[ImmutableODF].map(_.get(path).collect {
      case ii: InfoItem if ii.metaData.nonEmpty => ii.metaData
    }.flatten)
  }

  def getSingle(path: Path)(implicit timeout: Timeout): Future[Option[Node]] = {
    val ftree = (hierarchyStore ? GetTree).mapTo[ImmutableODF]
    ftree.flatMap(tree => tree.get(path).map {
      case info: InfoItem =>
        (latestStore ? SingleReadCommand(path)).mapTo[Option[Value[Any]]].map {
          case Some(value) =>
            InfoItem(path.last, path, values = Vector(value), attributes = info.attributes)
          case None =>
            info
        }
      case objs: Objects => Future.successful(objs)
      case obj: Object => Future.successful(obj)
    } match {
      case Some(f) => f.map(Some(_))
      case None => Future.successful(None)
    }
    )
  }
} 

object SingleStores{
  def apply( settings: OmiConfigExtension)(implicit system: ActorSystem): SingleStores = {
    new SingleStoresImpl( settings)(system)
  }
  /**
    * Contains all stores that requires only one instance for interfacing
    */
  class SingleStoresImpl private[SingleStores] (
    protected val settings: OmiConfigExtension)(implicit val system: ActorSystem) extends SingleStores{

    val latestStore: ActorRef = system.actorOf(LatestStore.props())
    val hierarchyStore: ActorRef = system.actorOf(HierarchyStore.props())
    val subStore: ActorRef = system.actorOf(SubStore.props())
    val pollDataStore: ActorRef = system.actorOf(PollDataStore.props())
  }
} 
