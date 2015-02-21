package response

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import responses._

import scala.collection.mutable.PriorityQueue
import database._
import parsing.Types.Subscription

import java.sql.Timestamp
import xml._
import scala.math.Ordering

import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging
class SubscriptionHandlerActor extends Actor {
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

  private var subscriptions : PriorityQueue[ TimedSub ] = new PriorityQueue[TimedSub]()(SubscriptionOrdering)

  override def receive = { 
    case subscription: Subscription => {
      val (requestid, xmlanswer) = OMISubscription.setSubscription(subscription)
      subscriptions += ((new java.sql.Timestamp(System.currentTimeMillis()), requestid))
      //send xml to the address this came from?
    }
  }
  
  def looper = {
    var activeSubs = subscriptions.takeWhile(_._1.getTime <= System.currentTimeMillis())

    for( (time, id) <- activeSubs){
      //val dbSensors = SQLite.getSubData(id)
      //right now subscription omi generation uses the paths in the dbsub, not sure if getSubData-function is needed as this looper
      //loops through all subs in the interval already? Also with the paths its easier to construct the OMI hierarchy
      val sub = SQLite.getSub(id).get 
      val omiMsg = generateOmi(id)
      val callbackAddr = sub.callback.get

      // send
      CallbackHandlers.sendCallback(callbackAddr, omiMsg)

      time.setTime(time.getTime + sub.interval)
    }
  }

  def generateOmi(id: Int): xml.Node = {
    return OMISubscription.OMISubscriptionResponse(id)
  }
}
