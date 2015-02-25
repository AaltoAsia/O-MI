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



  // FIXME: why not contain also the whole Subscription
  type TimedSub = (Timestamp,Int) 

  type EventSub = (Subscription,Int)


  object SubscriptionOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) = a._1.getTime compare b._1.getTime
  }

  private var intervalSubs: PriorityQueue[TimedSub] =
    PriorityQueue()(SubscriptionOrdering)

  private var eventSubs: Map[Path, EventSub] = HashMap()

  // Attach to db events
  SQLite.attachSetHook(this.checkEventSubs _)

  // TODO: load subscriptions at startup




  override def receive = { 

    case NewSubscription(requestId, subscription) => {
      //val (requestId, xmlanswer) = setSubscription(subscription)
      //sender() ! xmlanswer
      //// maybe do these in OmiService

      if (subscription.hasInterval){
        intervalSubs += ((new Timestamp(currentTimeMillis()), requestId))
        handleIntervals()

      }else if(subscription.isEventBased){
        for (path <- getPaths(subscription.sensors))
          eventSubs += path -> (subscription, requestId)
      } 
    }

    case Handle => handleIntervals()
  }


  def checkEventSubs(paths: Seq[Path]): Unit = {

    for (path <- paths) {
      eventSubs.get(path) match {

        case Some((subscription, id)) => 

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
    val activeSubs = intervalSubs.takeWhile(_._1.getTime <= currentTimeMillis())

    for ( (time, id) <- activeSubs) {
      Future{
        //val dbSensors = SQLite.getSubData(id) right now subscription omi
        //generation uses the paths in the dbsub, not sure if getSubData-function
        //is needed as this looper loops through all subs in the interval already?
        //Also with the paths its easier to construct the OMI hierarchy

        val sub = SQLite.getSub(id).get 
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
            log.info(s"Callback sent; subscription id:$id addr:$callbackAddr interval:$interval")

          case Success(fail: CallbackFailure) =>
            failed(fail.toString)
            
          case Failure(e) =>
            failed(e.getMessage)
        }



        // New time
        time.setTime(time.getTime + sub.interval)
        intervalSubs += Tuple2( time , id)
      }
    }

    // Schedule for next
    intervalSubs.headOption map { next =>
      val nextRun = next._1.getTime - currentTimeMillis()
      system.scheduler.scheduleOnce(nextRun.milliseconds, self, Handle)
      log.info(s"Next subcription handling scheluded to $nextRun.")
    }
  }



  def generateOmi(id: Int): xml.Node = {
    return OMISubscriptionResponse(id)
  }
}
