/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/

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

case class NewDataEvent(data: Seq[(Path, OdfValue)])// TODO
/**
 * Event for polling pollable subscriptions
 * @param id Id of the subscription to poll
 */
case class PollSubscription(id: Long)

/**
 * Class that handles event and interval based subscriptions.
 * Uses Akka scheduler to schedule ttl handling and intervalhandling
 * @param dbConnection
 */
class SubscriptionHandler(implicit val dbConnection: DB) extends Actor with ActorLogging {

  val minIntervalDuration = Duration(1, duration.SECONDS)
  val ttlScheduler = new SubscriptionScheduler
  val intervalScheduler = context.system.scheduler//ttlScheduler //temporarily

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
  private def pollSubscription(id: Long) : Option[OdfObjects]= {
    val pollTime = System.currentTimeMillis()
    val sub = SingleStores.pollPrevayler execute PollSub(id)
    sub match {
      case Some(pollSub) =>{

        val rawData = dbConnection.pollSubData(id)
          .groupBy(_.path)
          .mapValues(_.sortBy(_.timestamp.getTime))
        val data: Map[Path, IndexedSeq[OdfValue]] = rawData.mapValues(_.map(_.toOdf).toVector)

        val latestValues: Seq[SubValue] = rawData.mapValues(_.last).values.toSeq


        pollSub match {
          case pollEvent: PollEventSub => {
            val eventData = data //TODO take last value into consideration
            val pollData = eventData
              .map(n=> OdfInfoItem(n._1, n._2)) // Map to Infoitems
              .map(i => fromPath(i)).reduceOption(_.union(_)) //Create OdfObjects

            dbConnection.removeDataAndUpdateLastValues(id, latestValues)

            pollData
          }
          case pollInterval: PollIntervalSub => {
            val interval = pollInterval.interval
            val pollData = data.mapValues(values =>
              values
                .:+(values.last.copy(timestamp = new Timestamp(pollTime))) //take pollTime into calculations
                .tail
                .foldLeft((Seq[OdfValue](values.head),pollInterval.lastPolled.getTime)){ (col, nextSensorValue) => //if
                  //produces tuple with the values in the first variable, second variable is to keep track of passed intervals
                  if(nextSensorValue.timestamp.getTime() < col._2){
                    //If sensor value updates are faster than the interval of the Subscription
                    (col._1.updated(col._1.length - 1,nextSensorValue),col._2)
                  } else {
                    //If there are multiple intervals between sensor updates
                    val timeBetweenSensorUpdates = nextSensorValue.timestamp.getTime - col._1.last.timestamp.getTime() - 1
                    //-1 is to prevent cases where new sensor value is created exactly at interval from being overwritten
                    val intervalsPassed: Int = (timeBetweenSensorUpdates / interval.toMillis).toInt
                    (col._1 ++ Seq.fill(intervalsPassed)(col._1.last) :+ nextSensorValue, col._2+interval.toMillis) //Create values between timestamps if necessary
                  }
            //Take values from tuple and remove last value
            }._1.init)
            .map(n => OdfInfoItem(n._1, n._2))
            .map(i => fromPath(i)).reduceOption(_.union(_))

            dbConnection.removeDataAndUpdateLastValues(id, latestValues)

            pollData
          }

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
        val odfInfoOpt = hTree.get(iPath)
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
        }
    }

    nextRunTimeOption.foreach{ tStamp =>
      val nextRun = Duration(math.max(tStamp.getTime - currentTime, 0L), "milliseconds")
      intervalScheduler.scheduleOnce(nextRun, self, HandleIntervals)
    }
  }


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
      val currentTimestamp = new Timestamp(currentTime)

      val subId = subscription.callback match {
        case cb @ Some(callback) => subscription.interval match {
          case Duration(-1, duration.SECONDS) => {
            //event subscription


            SingleStores.eventPrevayler execute AddEventSub(
              EventSub(
                newId,
                OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq,
                newTime,
                callback
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
                currentTimestamp
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
          val paths = OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq
          val subData = paths.map(path => SubValue(newId,path,currentTimestamp,"",""))
          subscription.interval match{
            case Duration(-1, duration.SECONDS) => {
              //event poll sub
              SingleStores.pollPrevayler execute AddPollSub(
                PollEventSub(
                  newId,
                  newTime,
                  currentTimestamp,
                  paths
                )
              )
              dbConnection.addNewPollData(newId, subData)
              newId
            }
            case dur: FiniteDuration if dur.gteq(minIntervalDuration) => {
              //interval poll
              SingleStores.pollPrevayler execute AddPollSub(
                PollIntervalSub(
                  newId,
                  newTime,
                  dur,
                  currentTimestamp,
                  paths
                )
              )
              dbConnection.addNewPollData(newId, subData)
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
