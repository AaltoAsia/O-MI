package response

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import responses._

import scala.collection.mutable.PriorityQueue
import database._

import java.sql.Timestamp
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._
import io._
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
  import Tcp._
  import context.system
  type TimedSub = (Timestamp,Int) 
  object SubscriptionOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) = a._1.getTime compare b._1.getTime
  }

  private var subscriptions : PriorityQueue[ TimedSub ] = new PriorityQueue[TimedSub]()(SubscriptionOrdering)

  override def receive = { 
    case subscription: Subscription => {
      val (requestid, xmlanswer) = OMISubscription.setSubscription(subscription)
      subscriptions ++= (new java.sql.Timestamp(System.currentTimeMillis()), requestid)
      //send xml to the address this came from?
    }
  }
  
  def looper = {
    var ids = subscriptions.takeWhile(sub => sub._1.getTime <= System.currentTimeMillis())
    for( id <- ids){
      //val dbSensors = SQLite.getSubData(id._2)
      //right now subscription omi generation uses the paths in the dbsub, not sure if getSubData-function is needed as this looper
      //loops through all subs in the interval already? Also with the paths its easier to construct the OMI hierarchy
      val sub = SQLite.getSub(id._2) 
      val omiMsg = generateOmi(id)
      val callback = sub.get.callback.get.split(":").dropRight(1).mkString(":")
      val port = sub.get.callback.get.split(":").takeRight(1).head.toInt
      //callback addres were to send data
      val address : InetSocketAddress = new InetSocketAddress(callback, port)
      /**TODO: 
        * Need away to get ActorRef of connection for sending data to address
        * Untested possible hack for it bellow
      */
      (IO(Tcp) ? Connect(address) ).onSuccess {
        case connected : Connected =>
        val connection = sender()
        connection ! Write( ByteString(
            new PrettyPrinter( 80, 2 ).format( omiMsg )
          )
        )
      }
      id._1.setTime(id._1.getTime + sub.get.interval)
    }
  }

  def generateOmi(id: Int): xml.Node = {
    return OMISubscription.OMISubscriptionResponse(id)
  }
}
