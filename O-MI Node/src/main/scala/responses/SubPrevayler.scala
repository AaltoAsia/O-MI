package responses

import java.sql.Timestamp

import responses.CallbackHandlers.{CallbackFailure, CallbackSuccess}
import types.OdfTypes.{OdfInfoItem, OdfValue}

import scala.collection.JavaConversions.{asJavaIterable, asScalaIterator}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success, Try}


//import java.util.concurrent.ConcurrentSkipListSet
import akka.actor.{Actor, ActorLogging}
import database._
import types.OdfTypes._
import types.OmiTypes.SubscriptionRequest
import types._

/**
 * Message for triggering handling of intervalsubscriptions
 */
case object HandleIntervals

/**
 * New subscription event
 * @param subscription Subscription to be added
 */
case class NewSubscription(subscription: SubscriptionRequest)

/**
 * Remove subscription event
 * @param id Id of the subscription to remove
 */
case class RemoveSubscription(id: Long)

/**
 * Event for polling pollable subscriptions
 * @param id Id of the subscription to poll
 */
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
/**
 * Class that handles event and interval based subscriptions.
 * Uses Akka scheduler to schedule ttl handling and intervalhandling
 * @param dbConnection
 */
class SubscriptionHandler(implicit val dbConnection: DB) extends Actor with ActorLogging {

  val minIntervalDuration = Duration(1, duration.SECONDS)
  val ttlScheduler = context.system.scheduler
  val intervalScheduler = ttlScheduler

  /**
   * Schedule remove operation for subscriptions that are in prevayler stores,
   * only run at startup
   */
  private[this] def scheduleTtls() = {
    log.debug("Scheduling removesubscriptions for the first time...")
    val currentTime = System.currentTimeMillis()
    //event subs
    val allSubs = (SingleStores.eventPrevayler execute  GetAllEventSubs()) ++
                  (SingleStores.intervalPrevayler execute GetAllIntervalSubs()) ++
                  (SingleStores.pollPrevayler execute GetAllPollSubs())
    allSubs.foreach{ sub =>
      val nextRun = Duration(sub.endTime.getTime() - currentTime, "milliseconds")
      if(nextRun.toMillis > 0L){
        ttlScheduler.scheduleOnce(nextRun, self, RemoveSubscription(sub.id))
      } else {
        self ! RemoveSubscription(sub.id)
      }
    }
    log.debug("Scheduling done")
  }

  scheduleTtls()


  //  val pollPrevayler = PrevaylerFactory.createPrevayler()


  def receive = {
    case NewSubscription(subscription) => sender() ! setSubscription(subscription)
    case HandleIntervals => handleIntervals()
    case RemoveSubscription(id) => sender() ! removeSubscription(id)
    case PollSubscription(id) => sender() ! pollSubscription(id)
  }

  /**
   * Get pollsubscriptions data drom database
   * @param id id of subscription to poll
   * @return
   */
  private def pollSubscription(id: Long) : Option[OdfObjects]= { //explicit return type
    val sub = SingleStores.pollPrevayler execute PollSub(id)
    sub match {
      case Some(pollSub) =>{

        val nodes = pollSub.paths.flatMap(path => dbConnection.get(path))
        //val infoitems = dbConnection.getNBetween(nodes,Some(x.lastPolled), None, None, None)


        pollSub match {
          case pollEvent: PollEventSub => {
            //dbConnection.getNBetween(nodes,Some(pollEvent.lastPolled),None,None,None)
          ???
          }
          case pollInterval: PollIntervalSub =>
          ???
          case unknown => log.error(s"unknown subscription type $unknown")
          None
        }
      }
      case _ => None
    }
    //dbConnection.getNBetween(temp,sub.get)
  }

  /**
   * Method called when the interval of an interval subscription has passed
   */
  private def handleIntervals(): Unit = {
    //TODO add error messages from requesthandler
    val currentTime = System.currentTimeMillis()
    val hTree = SingleStores.hierarchyStore execute GetTree()
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
      val objectsAndFailures: Seq[Either[(Path, String),OdfObjects]] = datas.map{ 
        case (iPath, oValue) =>
        val odfInfoOpt = (SingleStores.hierarchyStore execute GetTree()).get(iPath)
        odfInfoOpt match {
          case Some(infoI: OdfInfoItem) =>
          Right(fromPath(infoI.copy(values = Iterable(oValue))))
          case thing => {
            log.warning(s"Could not find requested InfoItem($iPath) for subscription with id: ${iSub.id}")
            Left((iPath, s"Problem in hierarchyStore, Some(OdfInfoItem) expected actual: $thing"))
          }
        }
      }

      val (lefts, rights) = objectsAndFailures.foldLeft[(Seq[(Path,String)], Seq[OdfObjects])]((Seq(), Seq())){ 
        (s, n) =>
        n.fold(l => (s._1.:+(l), s._2), r => (s._1, s._2.:+(r))) //Split the either seq into two separate lists
      }
      val optionObjects: Option[OdfObjects] = rights.foldLeft[Option[OdfObjects]](None)((s, n) => Some(s.fold(n)(prev=> prev.union(n))))//rights.reduce(_.combine(_))
      val succResult = optionObjects.map(odfObjects => responses.Results.odf("200",None, Some(iSub.id.toString), odfObjects)).toSeq
      val failedResults = lefts.map(fail => Results.simple("404", Some(s"Could not find path: ${fail._1}. ${fail._2}")))
      val resultXml = OmiGenerator.xmlFromResults(iSub.interval.toSeconds.toDouble, (succResult ++ failedResults): _*)

      CallbackHandlers.sendCallback(iSub.callback,resultXml,iSub.interval)
        .onComplete {
          case Success(CallbackSuccess) =>
            log.info(s"Callback sent; subscription id:${iSub.id} addr:${iSub.callback} interval:${iSub.interval}")

          case Success(fail: CallbackFailure) =>
            log.warning(
              s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${fail.toString}")
          case Failure(e) =>
            log.warning(
              s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${e.getMessage}")
        }//Duration(iSub.endTime.getTime - currentTime, "milliseconds")) //TODO XXX ttl is the sub ttl not message ttl
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
    else if(SingleStores.pollPrevayler execute RemovePollSub(id)){
      dbConnection.removePollSub(id)
      true
    }
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

      val subId = subscription.callback match {
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
      subscription.ttl match {
        case dur: FiniteDuration => ttlScheduler.scheduleOnce(dur, self, RemoveSubscription(newId))
        case _ =>
      }
      subId
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


}
