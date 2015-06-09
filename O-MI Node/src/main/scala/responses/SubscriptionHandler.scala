package responses

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging

import responses._
import database._
import database._
import parsing.Types.Path
import parsing.Types.OmiTypes.{ SubLike, SubDataRequest }
import CallbackHandlers._
import parsing.Types.OmiTypes._
import parsing.Types.OdfTypes._
import parsing.xmlGen
import parsing.xmlGen.scalaxb

import scala.collection.mutable.PriorityQueue

import java.sql.Timestamp
import java.util.Date
import System.currentTimeMillis
import scala.math.Ordering
import scala.util.{ Success, Failure }
import scala.collection.mutable.{ Map, HashMap }

import xml._
import scala.concurrent.duration._
import scala.concurrent._
import scala.collection.JavaConversions.iterableAsScalaIterable

// MESSAGES
case object HandleIntervals
case object CheckTTL
case class RegisterRequestHandler(reqHandler: RequestHandler)
case class NewSubscription(subscription: SubscriptionRequest)
case class RemoveSubscription(id: Int)

/**
 * Handles interval counting and event checking for subscriptions
 */
class SubscriptionHandler(implicit dbConnection : DB ) extends Actor with ActorLogging {
  import ExecutionContext.Implicits.global
  import context.system

  private def date = new Date()
  implicit val timeout = Timeout(5.seconds)

  private var requestHandler = new RequestHandler(self)

  sealed trait SavedSub {
    val sub: DBSub
    val id: Int
  }

  case class TimedSub(sub: DBSub, id: Int, nextRunTime: Timestamp)
    extends SavedSub

  case class EventSub(sub: DBSub, id: Int, lastValue: String)
    extends SavedSub

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
    for (sub <- subs) loadSub(sub.id, sub)

  }

  private def loadSub(id: Int): Unit = {
    dbConnection.getSub(id) match {
      case Some(dbsub) =>
        loadSub(id, dbsub)
      case None =>
        log.error(s"Tried to load nonexistent subscription: $id")
    }
  }
  private def loadSub(id: Int, dbsub: DBSub): Unit = {
    log.debug(s"Adding sub: $id")

    if (dbsub.hasCallback){
      if (dbsub.isIntervalBased) {
        intervalSubs += TimedSub(
          dbsub,
          id,
          new Timestamp(currentTimeMillis()))

        handleIntervals()
        log.debug(s"Added sub as TimedSub: $id")

      } else if (dbsub.isEventBased) {

        dbConnection.getSubscribtedItems(dbsub.id).foreach{
          case item: SubscriptionItem =>
            eventSubs.get(item.path.toString) match {
              case Some( ses : Seq[EventSub] ) => 
                eventSubs = eventSubs.updated( item.path.toString, ses ++ Seq( EventSub(dbsub, id, item.lastValue)))
              case None => 
                eventSubs += item.path.toString -> Seq( EventSub(dbsub, id, item.lastValue))
            }
          case x =>
            log.warning(s"$x not implemented in SubscriptionHandlerActor for Interval=-1")
        }

        log.debug(s"Added sub as EventSub: $id")
      }
    }else{
      ttlQueue.enqueue((dbsub.id, (dbsub.ttl.toInt * 1000).toLong + dbsub.startTime.getTime))
    }
  }

  /**
   * @param id The id of subscription to remove
   * @return true on success
   */
  private def removeSub(id: Int): Boolean = {
    dbConnection.getSub(id) match {
      case Some(dbsub) => removeSub(dbsub)
      case None => false
    }
  }
  
  /**
   * @param sub The subscription to remove
   * @return true on success
   */
  private def removeSub(sub: DBSub): Boolean = {
    if (sub.isEventBased) {
      eventSubs.foreach{ 
        case (path: String, ses: Seq[EventSub])  =>
        eventSubs = eventSubs.updated(path, ses.filter( _.id != sub.id ))    
      }
    } else {
      //remove from intervalSubs
      if(sub.hasCallback)
        intervalSubs = intervalSubs.filterNot(sub.id == _.id)
      else
        ttlQueue = ttlQueue.filterNot( sub.id == _._1)
    }
    dbConnection.removeSub(sub.id)
  }

  override def receive = {

    case HandleIntervals => handleIntervals()

    case CheckTTL => checkTTL()

    case NewSubscription(subscription) => sender() ! setSubscription(subscription)

    case RemoveSubscription(requestId) => sender() ! removeSub(requestId)
  
    case RegisterRequestHandler(reqHandler: RequestHandler) => requestHandler = reqHandler
  }

  def checkEventSubs(paths: Seq[Path]): Unit = {

    for (path <- paths) {
      var newestValue: Option[String] = None

      eventSubs.get(path.toString) match {

        case Some(ses : Seq[EventSub]) => {
          ses.foreach{
            case EventSub(subscription, id, lastValue) => 
              if (hasTTLEnded(subscription, currentTimeMillis())) {
                removeSub(subscription)
              } else {
                if (newestValue.isEmpty)
                  newestValue = dbConnection.get(path).map{
                    case OdfInfoItem(_, v, _, _) => iterableAsScalaIterable(v).headOption.map{
                      case value : OdfValue =>
                        value.value
                    }.getOrElse("")
                    case _ => ""// noop, already logged at loadSub
                  }
                
                if (lastValue != newestValue.getOrElse("")){

                  //def failed(reason: String) =
                  //  log.warning(s"Callback failed; subscription id:$id  reason: $reason")

                  val addr = subscription.callback 
                  if (addr == None) return

                  requestHandler.handleRequest(SubDataRequest(subscription))

                }
              }
          }
        }

        case None => // noop
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

      val TimedSub(sub, id, time) = intervalSubs.dequeue()

      log.debug(s"handleIntervals: delay:${checkTime - time.getTime}ms currentTime:$checkTime targetTime:${time.getTime} id:$id")

      // Check if ttl has ended, comparing to original check time
      if (hasTTLEnded(sub, checkTime)) {

        dbConnection.removeSub(id)

      } else {
        val numOfCalls = ((checkTime - sub.startTime.getTime) / sub.intervalToMillis).toInt

        val newTime = new Timestamp(sub.startTime.getTime.toLong + sub.intervalToMillis * (numOfCalls + 1))
        //val newTime = new Timestamp(time.getTime + sub.intervalToMillis) // OLD VERSION

        intervalSubs += TimedSub(sub, id, newTime)


        log.debug(s"generateOmi for id:$id")
        requestHandler.handleRequest(SubDataRequest(sub))
        val interval = sub.interval
        val callbackAddr = sub.callback.get
        log.info(s"Sending in progress; Subscription id:$id addr:$callbackAddr interval:$interval")

//XXX: WHAT WAS THIS FOR?
          /*
          def failed(reason: String) =
            log.warning(
              s"Callback failed; subscription id:$id interval:$interval  reason: $reason")


            case Success(CallbackSuccess) =>
              log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:$interval")

            case Success(fail: CallbackFailure) =>
              failed(fail.toString)

            case Failure(e) =>
              failed(e.getMessage)
          }
        }.onFailure {
          case err: Throwable =>
            log.error(s"Error in callback handling of sub $id: ${err.getStackTrace.mkString("\n")}")
        }
        */
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
  type SubTuple = (Int, Long)

  /**
   * define ordering for priorityQueue this needs to be reversed when used, so that sub with earliest timeout is first.
   */
  val subOrder: Ordering[SubTuple] = Ordering.by(_._2)

  /**
   * PriorityQueue with subOrder ordering. value with earliest timeout is first.
   * This val is lazy and is computed when needed for the first time
   *
   * This queue contains only subs that have no callback address defined and have ttl > 0.
   */
  private var ttlQueue: PriorityQueue[SubTuple] = new PriorityQueue()(subOrder.reverse)
  var scheduledTimes: Option[(akka.actor.Cancellable, Long)] = None
  
  def setSubscription(subscription: SubscriptionRequest)(implicit dbConnection: DB) : Int = {
    var requestIdInt: Int = -1
    val paths = getPaths(subscription)

    if (paths.nonEmpty) {
      lazy val ttlInt = subscription.ttl.toInt
      lazy val interval = subscription.interval.toInt
      lazy val callback = subscription.callback match {
        case Some(uri) => Some(uri.toString)
        case None => None 
      }
      lazy val timeStamp = new Timestamp(date.getTime())
      val dbsub = dbConnection.saveSub( NewDBSub(interval, timeStamp, ttlInt, callback), getLeafs(subscription.odf).map{ _.path }.toSeq)
      requestIdInt = dbsub.id 
      Future{
      if (callback.isEmpty && ttlInt > 0) {
        ttlQueue.enqueue((requestIdInt, (ttlInt * 1000).toLong + timeStamp.getTime))
        self ! CheckTTL
      }else {
        if (dbsub.isIntervalBased) {
          intervalSubs += TimedSub(
            dbsub,
            requestIdInt,
            new Timestamp(currentTimeMillis()))

          self ! HandleIntervals
          log.debug(s"Added sub as TimedSub: $requestIdInt")

        } else if (dbsub.isEventBased) {
          dbConnection.getSubscribtedItems(dbsub.id).foreach{
            case item: SubscriptionItem =>
              eventSubs.get(item.path.toString) match {
                case Some( ses : Seq[EventSub] ) => 
                  eventSubs = eventSubs.updated( item.path.toString, ses ++ Seq( EventSub(dbsub, dbsub.id, item.lastValue)))
                case None => 
                  eventSubs += item.path.toString -> Seq( EventSub(dbsub, dbsub.id, item.lastValue))
              }
            case x =>
              log.warning(s"$x not implemented in SubscriptionHandlerActor for Interval=-1")
          }

          log.debug(s"Added sub as EventSub: $requestIdInt")
        }
      }
      }
    }
    requestIdInt
  }

  def getSensors(request: OdfRequest): Array[DBSensor] = {
    getPaths(request).map{path => dbConnection.get(path) }.collect{ case Some(sensor:DBSensor) => sensor }.toArray
  }

  def getPaths(request: OdfRequest) = getInfoItems( request.odf.objects ).map( info => info.path )

  
  def getInfoItems( objects: Iterable[OdfObject] ) : Iterable[OdfInfoItem] = {
    objects.flatten{
        obj => 
        obj.infoItems ++ getInfoItems(obj.objects)
    }
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
    while (ttlQueue.headOption.exists(_._2 <= currentTime)) {
      dbConnection.removeSub(ttlQueue.dequeue()._1)
    }
    
    //foreach does nothing if Option is None
    ttlQueue.headOption.foreach { n =>
      val nextRun = ((n._2) - currentTime)
      val cancellable = system.scheduler.scheduleOnce(nextRun.milliseconds, self, CheckTTL)
      if (scheduledTimes.forall(_._1.isCancelled)) {
        scheduledTimes = Some((cancellable, currentTime + nextRun))
      } else if (scheduledTimes.exists(_._2 > (currentTime + nextRun))) {
        scheduledTimes.foreach(_._1.cancel())
        scheduledTimes=Some((cancellable,currentTime+nextRun))
      }
      else if (scheduledTimes.exists(n =>((n._2 > currentTime) && (n._2 < (currentTime + nextRun))))) {
        cancellable.cancel()
      }
    }
  }
}
