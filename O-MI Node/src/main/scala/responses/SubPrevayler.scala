package responses

import java.sql.Timestamp
import java.util.Date

import org.prevayler.{Transaction, TransactionWithQuery}
import types.OdfTypes.OdfValue

import scala.collection.JavaConversions.asScalaIterator
import scala.collection.immutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try


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

case class PollSubscription(id: Long)

//private val subOrder: Ordering[TTLTimeout] = Ordering.by(_.endTimeMillis)


///**
// * PriorityQueue with subOrder ordering. value with earliest timeout is first.
// * This val is lazy and is computed when needed for the first time
// *
// * This queue contains only subs that have no callback address defined and have ttl > 0.
// */
//private val ttlQueue: ConcurrentSkipListSet[TTLTimeout] = new ConcurrentSkipListSet(subOrder)


//TODO remove initial value
class SubscriptionHandler(implicit val dbConnection: DB) extends Actor with ActorLogging {

  val minIntervalDuration = Duration(1, duration.SECONDS)
  val ttlScheduler = context.system.scheduler
  val intervalScheduler = ttlScheduler


  //  val pollPrevayler = PrevaylerFactory.createPrevayler()
  def receive = {
    case NewSubscription(subscription) => sender() ! setSubscription(subscription)
    case HandleIntervals => handleIntervals()
    case RemoveSubscription(id) => sender() ! removeSubscription(id)
    case PollSubscription(id) => sender() ! pollSubscription(id)
  }

  private def pollSubscription(id: Long) = { //explicit return type
    val sub = SingleStores.pollPrevayler execute PollSub(id)
    sub match {
      case Some(x) =>{

        val nodes = x.paths.flatMap(m => dbConnection.get(m))
        //val infoitems = dbConnection.getNBetween(nodes,Some(x.lastPolled), None, None, None)


        x match {
          case pollEvent: PollEventSub => {
            //dbConnection.getNBetween(nodes,Some(pollEvent.lastPolled),None,None,None)
          }
          case pollInterval: PollIntervalSub =>
          case unknown => log.error(s"unknown subscription type $unknown")
        }
      }
      case _ => None
    }
    val temp = sub.map(n => n.paths.flatMap(m => dbConnection.get(m)))
    //dbConnection.getNBetween(temp,sub.get)
  }

  /**
   * Method called when the interval of an interval subscription has passed
   */
  private def handleIntervals(): Unit = {
    val currentTime = System.currentTimeMillis()

    val (iSubs, nextRunTimeOption) = SingleStores.intervalPrevayler execute GetIntervals

    //val (iSubscription: Option[IntervalSub], nextRunTime: Option[Timestamp]) = SubWithNextRunTimeOption
     // .fold[(Option[IntervalSub], Option[Timestamp])]((None,None))(a => (Some(a._1), Some(a._2)))
    if(iSubs.isEmpty) {
      log.warning("HandleIntervals called when no interval subs existed")
      return
    }

    //send new data to callback addresses
    iSubs.foreach{iSub =>
      log.info(s"Trying to send subscription data to ${iSub.callback}")
      val datas = SingleStores.latestStore execute LookupSensorDatas(iSub.paths)
      //TODO combine path with the ODFVALUE !!


      CallbackHandlers.sendCallback(iSub.callback,???,iSub.interval)//Duration(iSub.endTime.getTime - currentTime, "milliseconds")) //TODO XXX ttl is the sub ttl not message ttl
    }


    //val odfValues = iSubscription.map(iSub => SingleStores.latestStore execute LookupSensorDatas(iSub.paths))



    nextRunTimeOption.foreach{ tStamp =>
      val nextRun = Duration(math.max(tStamp.getTime - currentTime, 0L), "milliseconds")
      intervalScheduler.scheduleOnce(nextRun, self, HandleIntervals)
    }
  }

  //  case class PollSubs(var pollSubs: ConcurrentSkipListSet[TTLTimeout])

  /**
   * Method used for removing subscriptions using their Id
   * @param id Id of the subscription to remove
   * @return Boolean indicating if the removing was successful
   */
  private def removeSubscription(id: Long): Boolean = {
    if(SingleStores.intervalPrevayler execute RemoveIntervalSub(id)) true
    else if(SingleStores.pollPrevayler execute RemovePollSub(id)) true
    else if(SingleStores.eventPrevayler execute RemoveEventSub(id)) true
    else false
  }

  /**
   * Method used to add subcriptions to Prevayler database
   * @param subscription SubscriptionRequest of the subscription to add
   * @return Subscription Id within Try monad if adding fails this is a Failure, otherwise Success(id)
   */
  private def setSubscription(subscription: SubscriptionRequest): Try[Long] = {
    Try {
      val newId = SingleStores.idPrevayler execute getAndUpdateId
      val newTime = subEndTimestamp(subscription.ttl)
      val currentTime = System.currentTimeMillis()

      subscription.callback match {
        case cb @ Some(callback) => subscription.interval match {
          case Duration(-1, duration.SECONDS) => {
            //event subscription


            SingleStores.eventPrevayler execute AddEventSub(
              EventSub(
                newId,
                OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq,
                newTime,
                callback,
                OdfValue("", "", new Timestamp(currentTime))
              )
            )
            newId
          }
          case dur@Duration(-2, duration.SECONDS) => ??? // subscription for new node
          case dur: FiniteDuration if dur.gteq(minIntervalDuration)=> {
            SingleStores.intervalPrevayler execute AddIntervalSub(
              IntervalSub(newId,
                OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq,
                newTime,
                callback,
                dur,
                new Timestamp(currentTime + dur.toMillis),
                new Timestamp(currentTime)

              )
            )

            newId
          }
          case dur => {
            log.error(s"Duration $dur is unsupported")
            throw new Exception(s"Duration $dur is unsupported")
          }
        }
        case None => {
          subscription.interval match{
            case Duration(-1, duration.SECONDS) => {
              //event poll sub
              SingleStores.pollPrevayler execute AddPollSub(
                PollEventSub(
                  newId,
                  newTime,
                  OdfValue("","",new Timestamp(currentTime)),
                  new Timestamp(currentTime),
                  OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq
                )
              )
              newId
            }
            case dur: FiniteDuration if dur.gteq(minIntervalDuration) => {
              //interval poll
              SingleStores.pollPrevayler execute AddPollSub(
                PollIntervalSub(
                  newId,
                  newTime,
                  OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq,
                  dur,
                  new Timestamp(currentTime)
                )
              )

              newId
            }
            case dur => {
              log.error(s"Duration $dur is unsupported")
              throw new Exception(s"Duration $dur is unsupported")
            }

          }
        }
      }
    }
  }

  /**
   * Helper method to get the Timestamp for removing the subscription
   * @param subttl time to live of the subscription
   * @return endTime of subscription as Timestamp
   */
  private def subEndTimestamp(subttl: Duration): Timestamp ={
              if (subttl.isFinite()) {
               new Timestamp(System.currentTimeMillis() + subttl.toMillis)
              } else {
               new Timestamp(Long.MaxValue)
              }
  }

//TODO EventSub
  case class AddEventSub(eventSub: EventSub) extends Transaction[EventSubs] {
    def executeOn(store: EventSubs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      //val currentTime: Long = System.currentTimeMillis()

      val scheduleTime: Long = eventSub.endTime.getTime - d.getTime // eventSub.ttl match

      if(scheduleTime > 0L){
        if(eventSub.endTime.getTime < Long.MaxValue){
          ttlScheduler.scheduleOnce(Duration(scheduleTime, "milliseconds"), self, RemoveSubscription(eventSub.id))
        }
        val newSubs: HashMap[Path, Seq[EventSub]] = HashMap(eventSub.paths.map(n => (n -> Seq(eventSub))): _*)
        store.eventSubs = store.eventSubs.merged[Seq[EventSub]](newSubs)((a, b) => (a._1, a._2 ++ b._2))
      }
    }
  }

  /*
  re schedule when starting in new subscription transactions
  */

  case class AddIntervalSub(intervalSub: IntervalSub) extends Transaction[IntervalSubs] {
    def executeOn(store: IntervalSubs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      val scheduleTime: Long = intervalSub.endTime.getTime - d.getTime
      if(scheduleTime > 0){
        if(intervalSub.endTime.getTime < Long.MaxValue){
          ttlScheduler.scheduleOnce(Duration(scheduleTime, "milliseconds"), self, RemoveSubscription(intervalSub.id))
        }
        store.intervalSubs = store.intervalSubs + intervalSub//TODO check this
      }

    }
  }

    case class AddPollSub(polledSub: PolledSub) extends Transaction[PolledSubs] {
      def executeOn(store: PolledSubs, d: Date) = {
        val scheduleTime: Long = polledSub.endTime.getTime - d.getTime
        if (scheduleTime > 0){
          if(polledSub.endTime.getTime < Long.MaxValue) {
            ttlScheduler.scheduleOnce(Duration(scheduleTime, "milliseconds"), self, RemoveSubscription(polledSub.id))
          }
          store.polledSubs = store.polledSubs + (polledSub.id -> polledSub)
        }
      }
    }

  /**
   * Transaction to remove a subscription from interval subscriptions, returns false if no sub with id found
   * @param id id of the subscription to remove
   */
  case class RemoveIntervalSub(id: Long) extends TransactionWithQuery[IntervalSubs, Boolean] {
    def executeAndQuery(store: IntervalSubs, d: Date): Boolean={
      val target = store.intervalSubs.find( _.id == id)
      target.fold(false){ sub =>
        store.intervalSubs = store.intervalSubs - sub
        true
      }

    }
  }

  /**
   * Transaction to remove subscription from event subscriptions
   * @param id id of the subscription to remove
   */
  case class RemoveEventSub(id: Long) extends  TransactionWithQuery[EventSubs, Boolean] {
    def executeAndQuery(store:EventSubs, d: Date): Boolean = {
      if(store.eventSubs.values.exists(_.exists(_.id == id))){
        val newStore: HashMap[Path, Seq[EventSub]] =
          store.eventSubs
            .mapValues(subs => subs.filterNot(_.id == id)) //remove values that contain id
            .filterNot( kv => kv._2.isEmpty ) //remove keys with empty values
            .map(identity)(collection.breakOut) //map to HashMap //TODO create helper method for matching
        store.eventSubs = newStore
        true
      } else{
        false
      }
    }
  }

  case class RemovePollSub(id: Long) extends TransactionWithQuery[PolledSubs, Boolean] {
    def executeAndQuery(store: PolledSubs, d: Date): Boolean = {
      if(store.polledSubs.contains(id)){
        store.polledSubs = store.polledSubs - id
        true
      } else false
    }
  }

  case class PollSub(id: Long) extends TransactionWithQuery[PolledSubs, Option[PolledSub]] {
    def executeAndQuery(store: PolledSubs, d: Date): Option[PolledSub] = {
      val sub = store.polledSubs.get(id)
      //sub.foreach(a => store.polledSubs = store.polledSubs.updated(id)) TODO update store and return value
      sub
    }
  }

  case object getAndUpdateId extends TransactionWithQuery[SubIds, Long] {
    override def executeAndQuery(p: SubIds, date: Date): Long = {
      p.id = p.id + 1
      p.id
    }
  }

  /**
   * Transaction to get the intervalSub with the earliest interval
   */

  case object GetIntervals extends TransactionWithQuery[IntervalSubs, (Set[IntervalSub], Option[Timestamp])] {
    def executeAndQuery(store: IntervalSubs, d: Date): (Set[IntervalSub], Option[Timestamp]) = {
      val (passedIntervals, rest) = store.intervalSubs.span(_.nextRunTime.before(d))// match { case (a,b) => (a, b.headOption)}
      val newIntervals = passedIntervals.map{a =>
          val numOfCalls = (d.getTime() - a.startTime.getTime) / a.interval.toMillis
          val newTime = new Timestamp(a.startTime.getTime + a.interval.toMillis * (numOfCalls + 1))
          a.copy(nextRunTime = newTime)}
      store.intervalSubs = rest ++ newIntervals
      (newIntervals, store.intervalSubs.headOption.map(_.nextRunTime))
    }

  }
}
