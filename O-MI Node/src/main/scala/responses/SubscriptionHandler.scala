package responses

import akka.actor.{ Actor, ActorLogging }

import akka.util.Timeout

import database._

import CallbackHandlers._
import types.OmiTypes.{SubscriptionRequest, getPaths, SubDataRequest}
import types.OdfTypes.{OdfValue, OdfInfoItem,fromPath, OdfObjects}

import java.sql.Timestamp
import java.util.Date
import System.currentTimeMillis

import scala.util.{ Try, Success, Failure }
import scala.collection.mutable.{ PriorityQueue, Map, HashMap }

import scala.concurrent._
import duration._

import scala.collection.JavaConversions.iterableAsScalaIterable
import ExecutionContext.Implicits.global

// MESSAGES
case object HandleIntervals
case object CheckTTL
case class RegisterRequestHandler(reqHandler: RequestHandler)
case class NewSubscription(subscription: SubscriptionRequest)
case class RemoveSubscription(id: Int)

/**
 * Handles interval counting and event checking for subscriptions
 */
class SubscriptionHandler(implicit dbConnection: DB) extends Actor with ActorLogging {

  private def date = new Date()
  implicit val timeout = Timeout(5.seconds)

  private var requestHandler = new RequestHandler(self)

  sealed trait SavedSub {
    val sub: DBSub
    val id: Int
  }

  case class TimedSub(sub: DBSub, nextRunTime: Timestamp)
    extends SavedSub {
    val id = sub.id
  }

  case class EventSub(sub: DBSub, lastValue: OdfValue)
    extends SavedSub {
    val id = sub.id
  }

  object TimedSubOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) =
      a.nextRunTime.getTime compare b.nextRunTime.getTime
  }

  private var intervalSubs: PriorityQueue[TimedSub] = {
    PriorityQueue()(TimedSubOrdering.reverse)
  }
  def getIntervalSubs = intervalSubs

  //var eventSubs: Map[Path, EventSub] = HashMap()
  private var eventSubs: Map[String, Seq[EventSub]] = HashMap()
  def getEventSubs = eventSubs

  // Attach to db events
  database.attachSetHook(this.checkEventSubs _)

  // load subscriptions at startup
  override def preStart() = {
    val subs = dbConnection.getAllSubs(Some(true))
    for (sub <- subs) loadSub(sub)

  }

  private def loadSub(id: Int): Unit = {
    dbConnection.getSub(id) match {
      case Some(dbsub) =>
        loadSub(dbsub)
      case None =>
        log.error(s"Tried to load nonexistent subscription: $id")
    }
  }
  private def loadSub(dbsub: DBSub): Unit = {
    log.debug(s"Adding sub: $dbsub")

    dbsub.hasCallback match {
      case true =>
        dbsub.isIntervalBased match {
          case true =>
            intervalSubs += TimedSub(
              dbsub,
              new Timestamp(currentTimeMillis()))

            // FIXME: schedules many times
            handleIntervals()

            log.debug(s"Added sub as TimedSub: $dbsub")

          case false =>
            dbsub.isEventBased match {
              case true =>
                dbConnection.getSubscribedItems(dbsub.id) foreach {
                  case SubscriptionItem(_, path, lastValueO) =>
                    val lastValue: OdfValue = lastValueO match {
                      case Some(v) => OdfValue(v, "")
                      case None => (for {
                        infoItem <- dbConnection.get(path)
                        last <- infoItem match {
                          case info: OdfInfoItem => info.values.headOption
                          case o => //noop
                            log.error(s"Didn't expect other than InfoItem: $o")
                            None
                        }
                      } yield last).getOrElse(OdfValue("", "", None))
                    }
                    eventSubs.get(path.toString) match {

                      case Some(ses: Seq[EventSub]) =>
                        eventSubs = eventSubs.updated(
                          path.toString, EventSub(dbsub, lastValue) :: ses.toList)

                      case None =>
                        eventSubs += path.toString -> Seq(EventSub(dbsub, lastValue))
                    }
                }

                log.debug(s"Added sub as EventSub: $dbsub")
              case false =>
            }
        }
      case false =>
        dbsub.isImmortal match {
          case false =>
            log.debug(s"Added sub as polled sub: $dbsub")
            ttlQueue.enqueue(PolledSub(dbsub.id, (dbsub.ttlToMillis + dbsub.startTime.getTime)))
            self ! CheckTTL
          case true => //noop
        }
    }
  }

  /**
   * @param id The id of subscription to remove
   * @return true on success
   */
  private def removeSub(id: Int): Boolean = {
    log.debug(s"Removing sub $id...")
    dbConnection.getSub(id) match {
      case Some(dbsub) => removeSub(dbsub)
      case None        => false
    }
  }

  /**
   * @param sub The subscription to remove
   * @return true on success
   */
  private def removeSub(sub: DBSub): Boolean = {
    sub.isEventBased match {
      case true =>
        val removedPaths = dbConnection.getSubscribedPaths(sub.id)
        removedPaths foreach { removedPath =>
          val path = removedPath.toString
          eventSubs get path match {
            case Some(ses: Seq[EventSub]) if ses.length > 1 =>
              eventSubs.updated(path, ses.filter(_.sub.id != sub.id))
            case Some(ses: Seq[EventSub]) if ses.length == 1 &&
              ses.headOption.map(_.sub.id == sub.id).getOrElse(false) =>
              // remove the whole entry to keep empty lists from piling up in the map
              eventSubs -= path
            case None => // noop
          }
        }
      case false =>
        //remove from intervalSubs
        sub.hasCallback match {
          case true =>
            intervalSubs = intervalSubs.filterNot(sub.id == _.id)
          case false =>
            ttlQueue = ttlQueue.filterNot(sub.id == _.id)
        }
    }
    dbConnection.removeSub(sub.id)
  }

  override def receive = {

    case HandleIntervals => handleIntervals()

    case CheckTTL => checkTTL()

    case NewSubscription(subscription) => sender() ! setSubscription(subscription)

    case RemoveSubscription(requestID) => sender() ! removeSub(requestID)

    case RegisterRequestHandler(reqHandler: RequestHandler) => requestHandler = reqHandler
  }

  /**
   * @param paths Paths of modified InfoItems.
   */
  def checkEventSubs(items: Seq[OdfInfoItem]): Unit = {
    //log.debug("EventCheck for:\n" + items.map(_.path.toString).mkString("\n"))
    //log.debug("EventCheck against:\n" + eventSubs.keys.mkString("\n"))

    val idItemLastVal = items.flatMap { item =>
      val itemPaths = item.path.getParentsAndSelf.map(_.toString)
      val subItemTuples = eventSubs.collect {
        case (path, subs) if itemPaths.contains(path) =>
          subs
      }.flatten

      //log.debug("subItemTuples are nonempty: " + subItemTuples.nonEmpty)
      subItemTuples.map { eventsub => (eventsub.sub, item, eventsub.lastValue) }
    }
    //log.debug("idItemLastVal are nonempty: " + idItemLastVal.nonEmpty)

    val idToItems = idItemLastVal.groupBy {
      case (sub, item, lastValue) =>
        sub
    }.mapValues {
      _.flatMap {
        case (sub, item, lastValue) =>
          val newVals = item.values.filter { odfvalue: OdfValue =>
            (lastValue.timestamp, odfvalue.timestamp) match {
              case (_, None) =>
                log.error("Event caused by timeless value " + item.path)
                false
              case (None, Some(_)) =>
                true
              case (Some(time: Timestamp), Some(itemTime: Timestamp)) =>
                itemTime.after(time)
            }
          }.dropWhile {
            odfvalue: OdfValue =>
              odfvalue.value == lastValue.value
          }
          if (newVals.isEmpty)
            return Seq.empty

          val eventSubsO = eventSubs.get(item.path.toString)
          eventSubsO match {
            case Some(subs) =>
              eventSubs = eventSubs.updated(
                item.path.toString,
                subs.map {
                  esub => EventSub(esub.sub, newVals.lastOption.getOrElse(lastValue))
                })
            case None =>
              eventSubs += item.path.toString -> Seq(EventSub(sub, newVals.lastOption.getOrElse(lastValue)))
          }
          Seq(fromPath(OdfInfoItem(item.path, collection.JavaConversions.asJavaIterable(newVals))))
      }.foldLeft(OdfObjects())(_ combine _)
    }
    //log.debug("idToItems are nonempty: " + idToItems.nonEmpty)
    idToItems.foreach {
      case (sub, odf) =>
        val id = sub.id
        log.debug(s"Sending data to event sub: $id.")
        val callbackAddr = sub.callback.getOrElse("")
        val xmlMsg = requestHandler.xmlFromResults(
          1.0,
          Result.pollResult(id.toString, odf))
        log.info(s"Sending in progress; Subscription subId:${id} addr:$callbackAddr interval:-1")
        //log.debug("Send msg:\n" + xmlMsg)

        def failed(reason: String) =
          log.warning(
            s"Callback failed; subscription id:${id} interval:-1  reason: $reason")

        sendCallback(callbackAddr, xmlMsg) onComplete {
          case Success(CallbackSuccess) =>
            log.info(s"Callback sent; subscription id:${id} addr:$callbackAddr interval:-1")

          case Success(fail: CallbackFailure) =>
            failed(fail.toString)
          case Failure(e) =>
            failed(e.getMessage)
        }
    }
  }

  private def hasTTLEnded(sub: DBSub, timeMillis: Long): Boolean = {
    val removeTime = sub.startTime.getTime + sub.ttlToMillis

    if (removeTime <= timeMillis && !sub.isImmortal) {
      log.debug(s"TTL ended for sub: id:${sub.id} ttl:${sub.ttlToMillis} delay:${timeMillis - removeTime}ms")
      true
    } else
      false
  }

  def handleIntervals(): Unit = {
    // failsafe
    if (intervalSubs.isEmpty) {
      log.error("handleIntervals shouldn't be called when there is no intervalSubs!")
      return
    }

    val checkTime = currentTimeMillis()

    while (intervalSubs.headOption.exists(_.nextRunTime.getTime <= checkTime)) {

      val TimedSub(sub, time) = intervalSubs.dequeue()

      log.debug(s"handleIntervals: delay:${checkTime - time.getTime}ms currentTime:$checkTime targetTime:${time.getTime} id:${sub.id}")

      // Check if ttl has ended, comparing to original check time
      if (hasTTLEnded(sub, checkTime)) {

        dbConnection.removeSub(sub.id)

      } else {
        val numOfCalls = ((checkTime - sub.startTime.getTime) / sub.intervalToMillis).toInt

        val newTime = new Timestamp(sub.startTime.getTime.toLong + sub.intervalToMillis * (numOfCalls + 1))
        //val newTime = new Timestamp(time.getTime + sub.intervalToMillis) // OLD VERSION

        intervalSubs += TimedSub(sub, newTime)

        log.debug(s"generateOmi for subId:${sub.id}")
        requestHandler.handleRequest(SubDataRequest(sub)) //Returns tuple, second is return status
      }
    }

    // Schedule for next
    intervalSubs.headOption foreach { next =>

      val nextRun = next.nextRunTime.getTime - currentTimeMillis()
      system.scheduler.scheduleOnce(nextRun.milliseconds, self, HandleIntervals)

      log.debug(s"Next subcription handling scheluded after ${nextRun}ms")
    }
  }

  /**
   * typedef for (Int,Long) tuple where values are (subID,ttlInMilliseconds + startTime).
   */
  case class PolledSub(id: Int, ttlMillis: Long)

  /**
   * define ordering for priorityQueue this needs to be reversed when used, so that sub with earliest timeout is first.
   */
  val subOrder: Ordering[PolledSub] = Ordering.by(_.ttlMillis)

  /**
   * PriorityQueue with subOrder ordering. value with earliest timeout is first.
   * This val is lazy and is computed when needed for the first time
   *
   * This queue contains only subs that have no callback address defined and have ttl > 0.
   */
  private var ttlQueue: PriorityQueue[PolledSub] = new PriorityQueue()(subOrder.reverse)
  var scheduledTimes: Option[(akka.actor.Cancellable, Long)] = None

  /**
   * @return Either Failure(exception) or the request (subscription) id as Success(Int)
   */
  def setSubscription(subscription: SubscriptionRequest)(implicit dbConnection: DB): Try[Int] = Try {
    require(subscription.ttl > 0.seconds, "Zero time-to-live not supported")

    val paths = getPaths(subscription)

    require(paths.nonEmpty, "Subscription request should have some items to subscribe.")

    val interval = subscription.interval
    val ttl = subscription.ttl
    val callback = subscription.callback
    val timeStamp = new Timestamp(date.getTime())

    val newSub = NewDBSub(interval, timeStamp, ttl, callback)
    val dbsub = dbConnection.saveSub(newSub, paths)

    val requestID = dbsub.id
    Future {
      loadSub(dbsub)
    } onComplete {
      case Success(v) => println("SUCCESS")
      case Failure(e) => log.error(e.getStackTrace().mkString("\n"))
    }
    requestID
  }

  /**
   * This method is called by scheduler and when new sub is added to subQueue.
   *
   * This method removes all subscriptions that have expired from the priority queue and
   * schedules new checkSub method call.
   */
  def checkTTL()(implicit dbConnection: DB): Unit = {

    val currentTime = date.getTime

    //exists returns false if Option is None
    while (ttlQueue.headOption.exists(_.ttlMillis <= currentTime)) {
      dbConnection.removeSub(ttlQueue.dequeue().id)
    }

    //foreach does nothing if Option is None
    ttlQueue.headOption.foreach { polledSub =>
      
      //time until checkTTL will be next called
      val nextRun = ((polledSub.ttlMillis) - currentTime)
      
      //cancellable event of the next checkTTL method call
      val cancellable = system.scheduler.scheduleOnce(nextRun.milliseconds, self, CheckTTL)
      
      /*
       * logic for updating the cancellable:
       * if event is cancelled or empty, then add the previous cancellable to queue
       * else check if the scheduledTimes variable is going to be run before the cancellable and update variable accordingly
       * so that only 1 event is scheduled
       */
      
      
      //forall returns true is Option is None
      scheduledTimes.forall { case (scheduledEvent, _) => scheduledEvent.isCancelled } match {
        case true =>
          scheduledTimes = Some((cancellable, currentTime + nextRun))

        case false =>
          scheduledTimes.exists { case (_, eventRunTime) => eventRunTime > (currentTime + nextRun) } match {
            case true =>
              scheduledTimes.foreach { case (scheduledEvent, _) => scheduledEvent.cancel() }
              scheduledTimes = Some((cancellable, currentTime + nextRun))
            case false =>
              
              //filter returns None if Option is None or predicate is false
              scheduledTimes.filter {
                case (_, eventRunTime) => ((eventRunTime > currentTime) && (eventRunTime < (currentTime + nextRun)))
              }.foreach { case (scheduledEvent, _) => scheduledEvent.cancel() }
          }
      }
    }
  }
}
