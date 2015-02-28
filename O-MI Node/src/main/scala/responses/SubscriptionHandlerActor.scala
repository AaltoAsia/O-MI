package responses

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging

import responses._
import database._
import parsing.Types.{Subscription, Path}
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
case object Handle
case class NewSubscription(id: Int, sub: Subscription)




/**
 * TODO: Under development
 */
class SubscriptionHandlerActor extends Actor with ActorLogging {
  import ExecutionContext.Implicits.global
  import context.system

  implicit val timeout = Timeout(5.seconds)

  sealed trait SavedSub {
    val sub: Subscription
    val id: Int
  }

  case class TimedSub(sub: Subscription, id: Int, nextRunTime: Timestamp)
    extends SavedSub

  case class EventSub(sub: Subscription, id: Int)
    extends SavedSub


  object TimedSubOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) =
      a.nextRunTime.getTime compare b.nextRunTime.getTime
  }

  private var intervalSubs: PriorityQueue[TimedSub] =
    PriorityQueue()(TimedSubOrdering)

  private var eventSubs: Map[Path, EventSub] = HashMap()

  // Attach to db events
  SQLite.attachSetHook(this.checkEventSubs _)




  // TODO: load subscriptions at startup




  override def receive = { 

    case NewSubscription(requestId, subscription) => {

      if (subscription.hasInterval){

        intervalSubs += TimedSub(
            subscription,
            requestId,
            new Timestamp(currentTimeMillis())
          )

        handleIntervals()

      }else if(subscription.isEventBased){

        for (path <- getPaths(subscription.sensors))
          eventSubs += path -> EventSub(subscription, requestId)

      } 
    }

    case Handle => handleIntervals()
  }


  def checkEventSubs(paths: Seq[Path]): Unit = {

    for (path <- paths) {
      eventSubs.get(path) match {

        case Some(EventSub(subscription, id)) => 

          def failed(reason: String) =
            log.warning(s"Callback failed; subscription id:$id  reason: $reason")

          val addr = subscription.callback.get
          val callbackXml = OMISubscriptionResponse(id)

          val call = CallbackHandlers.sendCallback(addr, callbackXml)

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



  def handleIntervals(): Unit = {

    while (intervalSubs.head.nextRunTime.getTime <= currentTimeMillis()){
      val TimedSub(sub, id, time) = intervalSubs.dequeue()
      Future{
        //val dbSensors = SQLite.getSubData(id) right now subscription omi
        //generation uses the paths in the dbsub, not sure if getSubData-function
        //is needed as this looper loops through all subs in the interval already?
        //Also with the paths its easier to construct the OMI hierarchy

          // FIXME: cancel or ending subscription should be aken into account
        val omiMsg = generateOmi(id)
        val callbackAddr = sub.callback.get
        val interval = sub.interval



        // Send, handle errors

        def failed(reason: String) =
            log.warning(
              s"Callback failed; subscription id:$id interval:$interval  reason: $reason"
            )

        val call = CallbackHandlers.sendCallback(callbackAddr, omiMsg)

        call onComplete {

          case Success(CallbackSuccess) => 
            log.debug(s"Callback sent; subscription id:$id addr:$callbackAddr interval:$interval")

          case Success(fail: CallbackFailure) =>
            failed(fail.toString)
            
          case Failure(e) =>
            failed(e.getMessage)
        }



        val newTime = new Timestamp(time.getTime + interval)
        intervalSubs += TimedSub(sub, id, newTime)
      }
    }

    // Schedule for next
    intervalSubs.headOption map { next =>

      val nextRun = next.nextRunTime.getTime - currentTimeMillis()
      system.scheduler.scheduleOnce(nextRun.milliseconds, self, Handle)

      log.debug(s"Next subcription handling scheluded to $nextRun, current $currentTimeMillis")
    }
  }



  def generateOmi(id: Int): xml.NodeSeq = {
    return OMISubscriptionResponse(id)
  }
}
