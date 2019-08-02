package database


import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, FiniteDuration, MILLISECONDS}
import scala.util.{Failure, Success}
import collection.immutable.LongMap

import journal.Models.SaveSnapshot
import http.OmiConfigExtension
import types.Path
import types.odf._
import journal._
import types.OmiTypes.Version._
import types.OmiTypes.{ResponseRequest, RequestIDRequest}
import utils.SimpleMonad._
import utils.OptionT

import LatestStore._
import HierarchyStore._
import SubStore._
import PollDataStore._
import RequestInfoStore._

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

    log.debug("Taking journal snapshot")
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


  //LatestStore
  def writeValue(path: Path, value: Value[Any]): Future[Unit] =
    (latestStore ? SingleWriteCommand(path,value)).mapTo[Unit]

  def writeValues(values: Map[Path, Value[Any]]): Future[Unit] =
    (latestStore ? WriteCommand(values)).mapTo[Unit]

  def erasePathData(path: Path): Future[Unit] =
    (latestStore ? ErasePathCommand(path)).mapTo[Unit]

  def readValue(path: Path): Future[Option[Value[Any]]] =
    (latestStore ? SingleReadCommand(path)).mapTo[Option[Value[Any]]]

  def readValues(paths: Seq[Path]): Future[Seq[(Path,Value[Any])]] =
    (latestStore ? MultipleReadCommand(paths)).mapTo[Seq[(Path,Value[Any])]]

  def readAll(): Future[Map[Path, Value[Any]]] =
    (latestStore ? ReadAllCommand).mapTo[Map[Path,Value[Any]]]

  //HierarchyStore
  def updateHierarchyTree(tree: ImmutableODF): Future[Unit] =
    (hierarchyStore ? UnionCommand(tree)).mapTo[Unit]

  def getHierarchyTree(): Future[ImmutableODF] =
    (hierarchyStore ? GetTree).mapTo[ImmutableODF]

  def erasePathHierarchy(path: Path): Future[Unit] =
    (hierarchyStore ? ErasePathCommand(path)).mapTo[Unit]

  //SubStore
  def addSub(eventSub: EventSub): Future[Unit] =
    (subStore ? AddEventSub(eventSub)).mapTo[Unit]

  def addSub(intervalSub: IntervalSub): Future[Unit] =
    (subStore ? AddIntervalSub(intervalSub)).mapTo[Unit]

  def addSub(pollSub: PolledSub): Future[Unit] =
    (subStore ? AddPollSub(pollSub)).mapTo[Unit]

  def lookupEventSubs(path: Path): Future[Seq[NormalEventSub]] =
    (subStore ? LookupEventSubs(path)).mapTo[Seq[NormalEventSub]]

  def lookupNewEventSubs(path: Path): Future[Seq[NewEventSub]] =
    (subStore ? LookupNewEventSubs(path)).mapTo[Seq[NewEventSub]]

  def removeIntervalSub(id: Long): Future[Boolean] =
    (subStore ? RemoveIntervalSub(id)).mapTo[Boolean]

  def removeEventSub(id: Long): Future[Boolean] =
    (subStore ? RemoveEventSub(id)).mapTo[Boolean]

  def removePollSub(id: Long): Future[Boolean] =
    (subStore ? RemovePollSub(id)).mapTo[Boolean]

  def pollSubscription(id: Long): Future[Option[PolledSub]] =
    (subStore ? PollSubCommand(id)).mapTo[Option[PolledSub]]

  def getAllEventSubs(): Future[Set[EventSub]] =
    (subStore ? GetAllEventSubs).mapTo[Set[EventSub]]

  def getAllIntervalSubs(): Future[Set[IntervalSub]] =
    (subStore ? GetAllIntervalSubs).mapTo[Set[IntervalSub]]

  def getAllPollSubs(): Future[Set[PolledSub]] =
    (subStore ? GetAllPollSubs).mapTo[Set[PolledSub]]

  def getIntervalSub(id: Long): Future[Option[IntervalSub]] =
    (subStore ? GetIntervalSub(id)).mapTo[Option[IntervalSub]]

  def getSubsForPath(path: Path): Future[Set[NotNewEventSub]] =
    (subStore ? GetSubsForPath(path)).mapTo[Set[NotNewEventSub]]

  def getNewEventSubsForPath(path: Path): Future[Set[PollNewEventSub]] =
    (subStore ? GetNewEventSubsForPath(path)).mapTo[Set[PollNewEventSub]]

  //PollDataStore
  def addPollData(id: Long, path: Path, value: Value[Any]): Future[Unit] =
    (pollDataStore ? AddPollData(id,path,value)).mapTo[Unit]

  def pollEventSubscrpition(id: Long): Future[Map[Path,Seq[Value[Any]]]] =
    (pollDataStore ? PollEventSubscription(id)).mapTo[Map[Path,Seq[Value[Any]]]]

  def pollIntervalSubscription(id: Long): Future[Map[Path,Seq[Value[Any]]]] =
    (pollDataStore ? PollIntervalSubscription(id)).mapTo[Map[Path,Seq[Value[Any]]]]

  def removePollSubData(id: Long): Future[Unit] =
    (pollDataStore ? RemovePollSubData(id)).mapTo[Unit]

  def checkSubData(id: Long): Future[Map[Path,Seq[Value[Any]]]] =
    (pollDataStore ? CheckSubscriptionData(id)).mapTo[Map[Path,Seq[Value[Any]]]]

  def addRequestInfo(endTime: Long, omiVersion: OmiVersion, odfVersion: Option[OdfVersion]): Future[Long] =
    (requestInfoStore ? AddInfo(endTime, omiVersion, odfVersion)).mapTo[Long]

  val DefaultRequestInfo = PRequestInfo(omiVersion=1.0, odfVersion=None) // TODO: Default version to configuration?

  def getRequestInfo(response: ResponseRequest): Future[PRequestInfo] = {

    val preDefault = OptionT(Future.successful(response.requestInfo))

    if (response.requestToken.isEmpty) log.info("Response doesn't have a request token, O-DF/O-MI versions unknown (FIXME)")
    

    val subscriptionIdF = Future.successful(
      for {
        result <- response.results.headOption
        requestID <- result.requestIDs.headOption
      } yield requestID
    )


    val subscriptionInfo = for {
      requestID <- OptionT(subscriptionIdF)
      subInfo   <- OptionT(peekRequestInfo(requestID)) // do not remove
    } yield subInfo

    val requestInfo = for {
      requestID    <- OptionT(subscriptionIdF map Option.apply) // optional
      requestToken <- OptionT(Future.successful(response.requestToken))
      requestInfo  <- OptionT{
        if (requestID exists (_ == requestToken)) peekRequestInfo(requestToken) // do not remove
        else getRequestInfo(requestToken) // remove
      }
    } yield requestInfo
    
    val result = requestInfo.flatMap{
      case ri if ri.odfVersion.isEmpty =>
        subscriptionInfo.map{si =>
          ri.copy(odfVersion=si.odfVersion)
        } orElse OptionT.some(ri)
      case ri => OptionT.some(ri)
    } orElse preDefault orElse subscriptionInfo getOrElse DefaultRequestInfo

    //for {
    //  a <- requestInfo.run
    //  b <- subscriptionInfo.run
    //  c <- result
    //  _ = println(s"GET REQUEST INFO: ${response.requestToken} $a $b $c")
    //} yield c
    result
  }

  def getRequestInfo(requestId: Long): Future[Option[PRequestInfo]] = {
    (requestInfoStore ? GetInfo(requestId)).mapTo[Option[PRequestInfo]]}

  def peekRequestInfo(requestId: Long): Future[Option[PRequestInfo]] =
    (requestInfoStore ? PeekInfo(requestId)).mapTo[Option[PRequestInfo]]

  def peekAllRequestInfo(): Future[LongMap[PRequestInfo]] =
    (requestInfoStore ? PeekAll()).mapTo[LongMap[PRequestInfo]]

  val latestStore: ActorRef
  val hierarchyStore: ActorRef
  val subStore: ActorRef
  val pollDataStore: ActorRef
  val requestInfoStore: ActorRef

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
            info.copy(values = Vector(value))
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
    val requestInfoStore: ActorRef = system.actorOf(RequestInfoStore.props())
  }
} 
