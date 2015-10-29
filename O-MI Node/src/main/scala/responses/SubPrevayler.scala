package responses

import java.sql.Timestamp
import java.util.Date

import types.Path

import scala.concurrent.duration.Duration
import scala.concurrent.stm.Ref

//import java.util.concurrent.ConcurrentSkipListSet

import akka.actor.{ActorLogging, Actor}
import database._
import org.prevayler.{Transaction, PrevaylerFactory}
import types.OdfTypes.OdfValue
import CallbackHandlers._
import types.OmiTypes.SubscriptionRequest

import scala.collection.SortedSet
import scala.collection.mutable.HashMap

case object HandleIntervals

case object CheckTTL

case class RegisterRequestHandler(reqHandler: RequestHandler)

case class NewSubscription(subscription: SubscriptionRequest)

case class RemoveSubscription(id: Long)

//private val subOrder: Ordering[TTLTimeout] = Ordering.by(_.endTimeMillis)


///**
// * PriorityQueue with subOrder ordering. value with earliest timeout is first.
// * This val is lazy and is computed when needed for the first time
// *
// * This queue contains only subs that have no callback address defined and have ttl > 0.
// */
//private val ttlQueue: ConcurrentSkipListSet[TTLTimeout] = new ConcurrentSkipListSet(subOrder)

case class PrevaylerSub(
                         val ttl: Duration,
                         val interval: Duration,
                         val callback: Option[String],
                       val paths: Seq[Path]

                         )

//TODO remove initial value
class SubscriptionHandler(subIDCounter:Ref[Long] = Ref(0L))(implicit val dbConnection: DB) extends Actor with ActorLogging {

  val subID:Ref[Long] = Ref(0L)

  val scheduler = system.scheduler

  case class EventSubs(var eventSubs: HashMap[String, Seq[EventSub]])

  case class IntervalSubs(var intervalSubs: SortedSet[TimedSub])


  case class AddEventSub(newName: String) extends Transaction[EventSubs] {
    def executeOn(store: EventSubs, d: Date) =
???
    //      store.data = store.data.copy(name = newName)
  }

  //  case class PollSubs(var pollSubs: ConcurrentSkipListSet[TTLTimeout])

  object TimedSubOrdering extends Ordering[TimedSub] {
    def compare(a: TimedSub, b: TimedSub) =
      a.nextRunTime.getTime compare b.nextRunTime.getTime
  }

  case class TimedSub(id: Long, ttl: Duration, paths: Seq[Path], interval: Duration, nextRunTime: Timestamp)
    extends SavedSub

  sealed trait SavedSub {
    val id: Long
    val ttl: Duration
    val paths: Seq[Path]

  }

  case class IntervalSub(id: Long, ttl: Duration, paths: Seq[Path], interval: Duration) extends SavedSub
  case class EventSub(id: Long, ttl: Duration, paths: Seq[Path], lastValue: OdfValue)
    extends SavedSub

  /*
  re schedule when starting in new subscription transactions
  */
  val eventPrevayler = PrevaylerFactory.createPrevayler(EventSubs(HashMap()))
  val intervalPrevayler = PrevaylerFactory.createPrevayler(IntervalSubs(SortedSet()(TimedSubOrdering.reverse)))

  //  val pollPrevayler = PrevaylerFactory.createPrevayler()
  def receive = {
    case NewSubscription(id) =>
      temp: Any => Unit
  }

}
