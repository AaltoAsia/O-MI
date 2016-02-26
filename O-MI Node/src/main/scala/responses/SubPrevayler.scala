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
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
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

case class NewDataEvent(data: Seq[(Path, OdfValue)])// TODO move to DB
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
  val intervalScheduler = context.system.scheduler

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
      if(sub.endTime.getTime() != Long.MaxValue ) {
        val nextRun = Duration(sub.endTime.getTime() - currentTime, "milliseconds")

        if (nextRun.toMillis > 0L) {
          ttlScheduler.scheduleOnce(nextRun, self, RemoveSubscription(sub.id))
        } else {
          self ! RemoveSubscription(sub.id)
        }
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
    //case NewDataEvent(data) => handleNewData(data)
  }


  /**
   * Get pollsubscriptions data drom database
   *
   * This method is used to both 'event' and 'interval' based subscriptions.
   *
   * Event subscriptions remove all data from database related to the poll.
   *
   * Interval subscriptions leave one value in the database to serve as starting value for next poll.
   * Database only stores changed values for subscriptions so values need to be interpolated for interval based subscriptions.
   * If sensor updates happen faster than the interval of the subscription then only the newest sensor value is added and older values dropped,
   * on the other hand if interval is shorter than the sensor updates then interpolated values will be generated between sensor values.
   *
   * @param id id of subscription to poll
   * @return
   */
  private def pollSubscription(id: Long) : Option[OdfObjects]= {
    val pollTime: Long = System.currentTimeMillis()
    val sub: Option[PolledSub] = SingleStores.pollPrevayler execute PollSub(id)
    sub match {
      case Some(pollSub) =>{
        log.debug(s"Polling subcription with id: ${pollSub.id}")
        val odfTree = SingleStores.hierarchyStore execute GetTree()
        val emptyTree = pollSub
          .paths //get subscriptions paths
          .flatMap(path => odfTree.get(path)) //get odfNode for each path and flatten the Option values
          .map( oNode => fromPath(oNode)) //map OdfNodes to OdfObjects
          .reduce(_.union(_)) //combine results

        //pollSubscription method removes the data from database and returns the requested data
        pollSub match {
          case pollEvent: PollEventSub => {


            log.debug(s"Creating response message for Polled Event Subscription")

            val eventData = dbConnection.pollEventSubscription(id).toVector //get data from database
              .groupBy(_.path) //group data by the paths
              .mapValues(_.sortBy(_.timestamp.getTime).map(_.toOdf)) //sort values by timestmap and convert them to OdfValue type
              .map(n => OdfInfoItem(n._1, n._2)) // Map to Infoitems
              .map(i => fromPath(i)) //Map to OdfObjects
              .fold(emptyTree)(_.union(_))//.reduceOption(_.union(_)) //Combine OdfObjects

            Some(eventData)
          }

          case pollInterval: PollIntervalSub => {

            log.debug(s"Creating response message for Polled Interval Subscription")

            val interval: Duration = pollInterval.interval

            val intervalData: Map[Path, Vector[OdfValue]] = dbConnection.pollIntervalSubscription(id).toVector
              .groupBy(_.path)
              .mapValues(_.sortBy(_.timestamp.getTime).map(_.toOdf))

            val pollData: OdfObjects = intervalData.map( pathValuesTuple =>{
              val (path, values) = pathValuesTuple match {
                case (p, v) if (v.nonEmpty) => (p, v.:+(v.last.copy(timestamp = new Timestamp(pollTime))))
                case (p, v) => {
                  log.debug(s"No values found for path: $p in Interval subscription poll for sub id ${pollSub.id}")
                  val latestValue = SingleStores.latestStore execute LookupSensorData(p) match {
                    //lookup latest value from latestStore, if exists use that
                    case Some(value) => Vector(value,value.copy(timestamp = new Timestamp(pollTime)))
                    //no previous values v is empty
                    case _ => v
                  }
                  (p, latestValue)
                }
              }

              val calculatedData: Option[IndexedSeq[OdfValue]] = if (values.size < 2) None else {
              //values size is atleast 2 if there is any data found for sub because the last value is added with pollTime timestamp
                  val newValues: IndexedSeq[OdfValue] = values
                    .tail
                    .foldLeft[(IndexedSeq[OdfValue], Long)]((Vector(values.head), pollInterval.lastPolled.getTime))
                    { (col, nextSensorValue) => //if
                    //produces tuple with the values in the first variable, second variable is to keep track of passed intervals
                    if (nextSensorValue.timestamp.getTime() < col._2) {
                      //If sensor value updates are faster than the interval of the Subscription
                      (col._1.updated(col._1.length - 1, nextSensorValue), col._2)
                    } else {
                      //If there are multiple intervals between sensor updates
                      val timeBetweenSensorUpdates = nextSensorValue.timestamp.getTime - col._1.last.timestamp.getTime() - 1
                      //-1 is to prevent cases where new sensor value is created exactly at interval from being overwritten
                      val intervalsPassed: Int = (timeBetweenSensorUpdates / interval.toMillis).toInt
                      (col._1 ++ Seq.fill(intervalsPassed)(col._1.last) :+ nextSensorValue, col._2 + interval.toMillis) //Create values between timestamps if necessary
                    }
                  }._1.tail.init //Remove last and first values that were used to generate intermediate values
                  Option(newValues)
                  }

                calculatedData.map(cData => path -> cData)
              }).flatMap{ n => //flatMap removes None values
              //create OdfObjects from InfoItems
              n.map(m => fromPath(OdfInfoItem(m._1, m._2)))
            }
            //combine OdfObjects to single optional OdfObject
            .fold(emptyTree)(_.union(_))
            //.reduceOption(_.union(_))

            Some(pollData)
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
            //normal event subscription


            SingleStores.eventPrevayler execute AddEventSub(
              EventSub(
                newId,
                OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq,
                newTime,
                callback
              )
            )
            log.debug(s"Successfully added event subscription with id: $newId and callback: $callback")
            newId
          }
          case dur@Duration(-2, duration.SECONDS) => throw new NotImplementedError("Interval -2 not supported")//subscription for new node
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

            log.debug(s"Successfully added interval subscription with id: $newId and callback $callback")
            newId
          }
          case dur => {
            log.error(s"Duration $dur is unsupported")
            throw new Exception(s"Duration $dur is unsupported")
          }
        }
        case None => {
          val paths = OdfTypes.getLeafs(subscription.odf).iterator().map(_.path).toSeq
          //println(paths.mkString("\n"))
          //val subData = paths.map(path => SubValue(newId,path,currentTimestamp,"",""))
          subscription.interval match{
            case Duration(-1, duration.SECONDS) => {
              //event poll sub
              SingleStores.pollPrevayler execute AddPollSub(
                PollEventSub(
                  newId,
                  newTime,
                  currentTimestamp,
                  currentTimestamp,
                  paths
                )
              )
              //dbConnection.addNewPollData(subData)

              log.debug(s"Successfully added polled event subscription with id: $newId")
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
                  currentTimestamp,
                  paths
                )
              )
              //dbConnection.addNewPollData(subData)
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
