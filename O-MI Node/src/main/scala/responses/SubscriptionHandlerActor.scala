package response

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging

import responses._
import database._
import parsing.Types.Subscription

import scala.collection.mutable.PriorityQueue

import java.sql.Timestamp
import System.currentTimeMillis
import scala.math.Ordering
import scala.util.{Success,Failure}

import xml._
import scala.concurrent.duration._

class SubscriptionHandlerActor extends Actor with ActorLogging {
  /**
    *
    *
    *
    *
    *
    *
    *
    * TODO: Under development, think as pseudo code more than actual.
    *
    *
    *
    *
    *
    *
    *
    */
import scala.concurrent.ExecutionContext.Implicits.global
  implicit val timeout = Timeout(5.seconds)
  import context.system
  type TimedSub = (Timestamp,Int) 
  object SubscriptionOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) = a._1.getTime compare b._1.getTime
  }

  private var intervalSubs: PriorityQueue[ TimedSub ] =
    new PriorityQueue[TimedSub]()(SubscriptionOrdering)


  override def receive = { 

    case subscription: Subscription => {
      val (requestid, xmlanswer) = OMISubscription.setSubscription(subscription)

      intervalSubs += ((new java.sql.Timestamp(System.currentTimeMillis()), requestid))

      // TODO: send acception xmlanswer to the address this came from?
    }
  }
  

  def handleIntervals = {
    val activeSubs = intervalSubs.takeWhile(_._1.getTime <= System.currentTimeMillis())

    for ( (time, id) <- activeSubs) {

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
          log.debug(s"Callback sent; subscription id:$id interval:$interval")

        case Success(fail: CallbackFailure) =>
          failed(fail.toString)
          
        case Failure(e) =>
          failed(e.getMessage)
      }



      // New time
      // FIXME: should rather have calculation that takes long
      // intervals into account on restart
      time.setTime(time.getTime + sub.interval)
    }
  }



  def generateOmi(id: Int): xml.Node = {
    return OMISubscription.OMISubscriptionResponse(id)
  }
}
