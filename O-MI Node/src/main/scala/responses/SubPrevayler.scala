package responses

import java.sql.Timestamp
import java.util.Date

import org.prevayler.{Transaction, TransactionWithQuery}
import types.OdfTypes.OdfValue

import scala.collection.immutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.stm.Ref
import scala.util.Try
import scala.collection.JavaConversions.asScalaIterator


//import java.util.concurrent.ConcurrentSkipListSet
import akka.actor.{Actor, ActorLogging}
import database._
import types.OmiTypes.SubscriptionRequest
import types._


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
                         val id: Long,
                         val ttl: Duration,
                         val interval: Duration,
                         val callback: Option[String],
                         val paths: Seq[Path]

                         )

//TODO remove initial value
class SubscriptionHandler(subIDCounter:Ref[Long] = Ref(0L))(implicit val dbConnection: DB) extends Actor with ActorLogging {

  val scheduler = context.system.scheduler


//TODO EventSub
  case class AddEventSub(eventSub: EventSub) extends Transaction[EventSubs] {
    def executeOn(store: EventSubs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      val currentTime = System.currentTimeMillis()

      val scheduleTime: Long = eventSub.endTime.getTime - d.getTime // eventSub.ttl match

      if(scheduleTime > 0L){
        if(eventSub.endTime.getTime < Long.MaxValue){
          scheduler.scheduleOnce(Duration(scheduleTime, "milliseconds"), self, RemoveSubscription(eventSub.id))
        }
        val newSubs: HashMap[String, Seq[EventSub]] = eventSub.paths.groupBy(identity).map(n => (n.toString() -> Seq(eventSub)))(collection.breakOut)
        store.eventSubs = store.eventSubs.merged(newSubs)((a, b) => (a._1, a._2 ++ b._2))
      }
    }
  }

  case class AddIntervalSub(intervalSub: IntervalSub) extends Transaction[IntervalSubs] {
    def executeOn(store: IntervalSubs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      val scheduleTime: Long = intervalSub.endTime.getTime - d.getTime
      if(scheduleTime >= 0){
        if(intervalSub.endTime.getTime < Long.MaxValue){
          scheduler.scheduleOnce(Duration(scheduleTime, "milliseconds"), self, RemoveSubscription(intervalSub.id))
        }
        val
        ???
      }
      val currentTime = System.currentTimeMillis()

    }
  }
  //  case class PollSubs(var pollSubs: ConcurrentSkipListSet[TTLTimeout])





  /*
  re schedule when starting in new subscription transactions
  */

  case object getAndUpdateId extends TransactionWithQuery[SubIds, Long]{
    override def executeAndQuery(p: SubIds, date: Date): Long = {
      p.id = p.id + 1
      p.id
    }
  }


  //  val pollPrevayler = PrevaylerFactory.createPrevayler()
  def receive = {
    case NewSubscription(subscription) => sender() ! setSubscription(subscription)

    }
      //temp: Any => Unit

  private def subEndTimestamp(subttl: Duration): Timestamp ={
              if (subttl.isFinite()) {
               new Timestamp(System.currentTimeMillis() + subttl.toMillis)
              } else {
               new Timestamp(Long.MaxValue)
              }
  }
  private def setSubscription(subscription: SubscriptionRequest): Try[Long] = {
    Try {
      val newId = SingleStores.idPrevayler execute getAndUpdateId

      subscription.callback match {
        case cb @ Some(callback) => subscription.interval match {
          case Duration(-1, duration.SECONDS) => {
            //event subscription

            val newTime = subEndTimestamp(subscription.ttl)

            SingleStores.eventPrevayler execute AddEventSub(
              EventSub(
                newId,
                OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq,
                newTime,
                cb,
                OdfValue("", "", None)
              )
            )
            newId
          }
          case dur@Duration(-2, duration.SECONDS) => ??? // subscription for new node
          case dur: FiniteDuration => {
            // interval subscription
            ???
          }
          case dur => ??? //log.error(Exception("unsupported Duration for subscription"), s"Duration $dur is unsupported")
        }
        case None => ??? //PollSub
      }
    }
  }

}
