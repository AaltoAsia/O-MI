package responses

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging

import responses._
import database.SQLite._
import database._
import parsing.Types.{SubLike, Path}
import OMISubscription.{getPaths, OMISubscriptionResponse}
import CallbackHandlers._

import scala.collection.mutable.PriorityQueue

import java.sql.Timestamp
import System.currentTimeMillis
import scala.math.Ordering
import scala.util.{Success,Failure}
import scala.collection.mutable.{Map, HashMap}

import xml._
import scala.concurrent.duration._
import scala.concurrent._

// MESSAGES
case object HandleIntervals
case class NewSubscription(id: Int)
case class RemoveSubscription(id: Int)




/**
 * Handles interval counting and event checking for subscriptions
 */
class SubscriptionHandlerActor extends Actor with ActorLogging {
  import ExecutionContext.Implicits.global
  import context.system

  implicit val timeout = Timeout(5.seconds)

  sealed trait SavedSub {
    val sub: DBSub
    val id: Int
  }

  case class TimedSub(sub: DBSub, id: Int, nextRunTime: Timestamp)
    extends SavedSub

  case class EventSub(sub: DBSub, id: Int)
    extends SavedSub


  object TimedSubOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) =
      a.nextRunTime.getTime compare b.nextRunTime.getTime
  }

  private var intervalSubs: PriorityQueue[TimedSub] =
    PriorityQueue()(TimedSubOrdering.reverse)

  private var eventSubs: Map[Path, EventSub] = HashMap()

  // Attach to db events
  SQLite.attachSetHook(this.checkEventSubs _)


  // load subscriptions at startup
  override def preStart() = {
    val subs = SQLite.getAllSubs(Some(true))
    for( sub <- subs ) loadSub(sub.id , sub)
  
  }




  private def loadSub(id: Int): Unit = {
    getSub(id) match {
      case Some(dbsub) =>
        loadSub(id, dbsub)
      case None =>
        log.error(s"Tried to load nonexistent subscription: $id")
    }
  }
  private def loadSub(id: Int, dbsub: DBSub): Unit = { 
    log.debug(s"Adding sub: $id")

    require(dbsub.callback.isDefined, "SubscriptionHandlerActor is not for buffered messages")

    if (dbsub.isIntervalBased){
      intervalSubs += TimedSub(
          dbsub,
          id,
          new Timestamp(currentTimeMillis())
        )

      handleIntervals()

    } else if (dbsub.isEventBased){

      for (path <- dbsub.paths)
        eventSubs += path -> EventSub(dbsub, id)
    }

  }

  /**
   * @return true on success
   */
  private def removeSub(id: Int): Boolean = {
    getSub(id) match {
      case Some(dbsub) => removeSub(dbsub)
      case None => false
    }
  }
  private def removeSub(sub: DBSub): Boolean = {
    if (sub.isEventBased) {
      sub.paths.foreach{ path =>
        eventSubs -= path
      }
    } else { 
      //remove from intervalSubs
      intervalSubs = intervalSubs.filterNot( sub.id == _.id ) 
    }
    SQLite.removeSub(sub.id)
  }

  override def receive = { 

    case HandleIntervals => handleIntervals()

    case NewSubscription(requestId) => loadSub(requestId)

    case RemoveSubscription(requestId) => sender() ! removeSub(requestId)
  }


  def checkEventSubs(paths: Seq[Path]): Unit = {

    for (path <- paths) {
      eventSubs.get(path) match {

        case Some(EventSub(subscription, id)) => 

          if (hasTTLEnded(subscription, currentTimeMillis())) {
            removeSub(subscription)
          }

          // Callback stuff
          def failed(reason: String) =
            log.warning(s"Callback failed; subscription id:$id  reason: $reason")

          val addr = subscription.callback // FIXME if no callback
          if(addr == None) return
          val callbackXml = OMISubscriptionResponse(id)

          val call = CallbackHandlers.sendCallback(addr.get, callbackXml)

          call onComplete {
            
            case Success(CallbackSuccess) =>
              log.debug(s"Callback sent; subscription id:$id addr:$addr")

            case Success(fail: CallbackFailure) =>
              failed(fail.toString)
            case Failure(e) =>
              failed(e.getMessage)
          }

        case None => // noop
      }
    }
  }



  private def hasTTLEnded(sub: DBSub, timeMillis: Long): Boolean = {
    val removeTime = sub.startTime.getTime + sub.ttlToMillis

    if (removeTime <= timeMillis && sub.ttl != -1) {
      log.debug(s"TTL ended for sub: id:${sub.id} ttl:${sub.ttlToMillis} delay:${timeMillis-removeTime}ms")
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

    // FIXME: only dequeue and dequeueAll returns elements in priority order
    while (intervalSubs.headOption.map(_.nextRunTime.getTime <= checkTime).getOrElse(false)){

      val TimedSub(sub, id, time) = intervalSubs.dequeue()

      log.debug(s"handleIntervals: delay:${checkTime-time.getTime}ms currentTime:$checkTime targetTime:${time.getTime} id:$id")


      // Check if ttl has ended, comparing to original check time
      if (hasTTLEnded(sub, checkTime)) {

        SQLite.removeSub(id)

      } else {
        val numOfCalls = ((checkTime - sub.startTime.getTime) / sub.intervalToMillis).toInt

        val newTime = new Timestamp(sub.startTime.getTime.toLong + sub.intervalToMillis * (numOfCalls+1))
        //val newTime = new Timestamp(time.getTime + sub.intervalToMillis) // OLD VERSION

        intervalSubs += TimedSub(sub, id, newTime)

        Future{ // TODO: Maybe move this to wrap only the generation and sending
                // because they are the only things that can take some time

          log.debug(s"generateOmi for id:$id")
          val omiMsg = generateOmi(id)
          val callbackAddr = sub.callback.get 
          val interval = sub.interval



          // Send, handle errors

          def failed(reason: String) =
              log.warning(
                s"Callback failed; subscription id:$id interval:$interval  reason: $reason"
              )

          log.debug(s"Sending callback $id to $callbackAddr...")
          val call = CallbackHandlers.sendCallback(callbackAddr, omiMsg)

          call onComplete {

            case Success(CallbackSuccess) => 
              log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:$interval")

            case Success(fail: CallbackFailure) =>
              failed(fail.toString)
              
            case Failure(e) =>
              failed(e.getMessage)
          }
        }.onFailure{case err: Throwable =>
          log.error(s"Error in callback handling of sub $id: $err")
        }
      }
    }

    // Schedule for next
    intervalSubs.headOption map { next =>

      val nextRun = next.nextRunTime.getTime - currentTimeMillis()
      system.scheduler.scheduleOnce(nextRun.milliseconds, self, HandleIntervals)

      log.debug(s"Next subcription handling scheluded after ${nextRun}ms")
    }
  }



  def generateOmi(id: Int): xml.NodeSeq = {
    return OMISubscriptionResponse(id)
  }
}
