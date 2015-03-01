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
case class NewSubscription(id: Int)




/**
 * TODO: Under development
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
    PriorityQueue()(TimedSubOrdering)

  private var eventSubs: Map[Path, EventSub] = HashMap()

  // Attach to db events
  SQLite.attachSetHook(this.checkEventSubs _)




  // TODO: load subscriptions at startup





  private def loadSub(id: Int): Unit = {
    getSub(id) match {
      case Some(dbsub) =>
        log.debug(s"Adding sub: $id")
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

      case None =>
        log.error(s"Tried to load nonexistent subscription: $id")
    }
  }


  override def receive = { 

    case NewSubscription(requestId) => loadSub(requestId)

    case Handle => handleIntervals()
  }


  def checkEventSubs(paths: Seq[Path]): Unit = {

    for (path <- paths) {
      eventSubs.get(path) match {

        case Some(EventSub(subscription, id)) => 

          def failed(reason: String) =
            log.warning(s"Callback failed; subscription id:$id  reason: $reason")

          val addr = subscription.callback.get // FIXME if no callback
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
    // failsafe
    if (intervalSubs.isEmpty) {
      log.error("handleIntervals shouldn't be called when there is no intervalSubs!")
      return
    }

    val checkTime = currentTimeMillis()

    while (intervalSubs.headOption.map(_.nextRunTime.getTime <= checkTime).getOrElse(false)){

      val TimedSub(sub, id, time) = intervalSubs.dequeue()

      log.debug(s"handleIntervals: delay:${checkTime-time.getTime}ms currentTime:$checkTime targetTime:${time.getTime} id:$id")


      // Check if ttl has ended, comparing to original check time
      val removeTime = sub.startTime.getTime + sub.ttlToMillis

      if (removeTime <= checkTime && sub.ttl != -1) {
        log.debug(s"Removing sub: id:$id ttl:${sub.ttlToMillis} delay:${checkTime-removeTime}ms")
        removeSub(id)

      } else {
        // FIXME: long TTLs may start accumulating some delay between calls, so maybe change
        // calculation to something like: startTime + interval * numOfCalls
        // , where numOfCalls = ((currentTime - startTime) / interval).toInt
        val newTime = new Timestamp(time.getTime + sub.intervalToMillis)
        intervalSubs += TimedSub(sub, id, newTime)

        Future{ // TODO: Maybe move this to wrap only the generation and sending
                // because they are the only things that can take some time

          //val dbSensors = SQLite.getSubData(id) right now subscription omi
          //generation uses the paths in the dbsub, not sure if getSubData-function
          //is needed as this looper loops through all subs in the interval already?
          //Also with the paths its easier to construct the OMI hierarchy

            // FIXME: cancel or ending subscription should be aken into account
          log.debug(s"generateOmi for id:$id")
          val omiMsg = generateOmi(id)
          val callbackAddr = sub.callback.get // FIXME: if no callback addr
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
      system.scheduler.scheduleOnce(nextRun.milliseconds, self, Handle)

      log.debug(s"Next subcription handling scheluded after ${nextRun}ms")
    }
  }



  def generateOmi(id: Int): xml.NodeSeq = {
    return OMISubscriptionResponse(id)
  }
}
