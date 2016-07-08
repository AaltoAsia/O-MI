/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package responses

import java.sql.Timestamp
import java.util.concurrent.TimeUnit._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Future, duration}
import scala.util.Try

import akka.actor.{Actor, ActorLogging, Props}
import database._
import http.CLICmds.{ ListSubsCmd, SubInfoCmd}
import responses.CallbackHandlers.{CallbackFailure, CallbackSuccess}
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

/**
 * Event for polling pollable subscriptions
 * @param id Id of the subscription to poll
 */
case class PollSubscription(id: Long)

object SubscriptionManager{
  def props()(implicit dbConnection: DB): Props = Props(new SubscriptionManager())
}


/**
 * Class that handles event and interval based subscriptions.
 * Uses Akka scheduler to schedule ttl handling and intervalhandling
 */
class SubscriptionManager extends Actor with ActorLogging {
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
    val allSubs = (SingleStores.subStore execute  GetAllEventSubs()) ++
      (SingleStores.subStore execute GetAllIntervalSubs()) ++
      (SingleStores.subStore execute GetAllPollSubs())
    allSubs.foreach{ sub =>
      if(sub.endTime.getTime() != Long.MaxValue ) {
        val nextRun = Duration(sub.endTime.getTime() - currentTime,MILLISECONDS)

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
  handleIntervals() //when server restarts



  def receive: PartialFunction[Any, Unit] = {
    case NewSubscription(subscription) => sender() ! subscribe(subscription)
    case HandleIntervals => handleIntervals()
    case RemoveSubscription(id) => sender() ! removeSubscription(id)
    case PollSubscription(id) => sender() ! pollSubscription(id)
    case ListSubsCmd() => sender() ! getAllSubs()
    case SubInfoCmd(id) => sender() ! getSub(id)
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
  private def pollSubscription(id: Long) : Option[OdfObjects] = {
    val pollTime: Long = System.currentTimeMillis()
    val sub: Option[PolledSub] = SingleStores.subStore execute PollSub(id)
    sub match {
      case Some(pollSub) =>{
        log.info(s"Polling subcription with id: ${pollSub.id}")
        val odfTree = SingleStores.hierarchyStore execute GetTree()
        val emptyTree = pollSub
          .paths //get subscriptions paths
          .flatMap(path => odfTree.get(path)) //get odfNode for each path and flatten the Option values
          .map{
            case i: OdfInfoItem => createAncestors(OdfInfoItem(i.path))
            case o: OdfObject   => createAncestors(OdfObject(o.id, o.path, typeValue = o.typeValue))
            case o: OdfObjects  => createAncestors(OdfObjects())//map OdfNodes to OdfObjects
            //case o: OdfNode  => createAncestors(OdfObjects())//map OdfNodes to OdfObjects
          }.reduceOption[OdfObjects]{case (l,r) => l.union(r)}.getOrElse(OdfObjects())

        //pollSubscription method removes the data from database and returns the requested data
        pollSub match {
          case pollEvent: PollEventSub => {


            log.debug(s"Creating response message for Polled Event Subscription")
            val eventData = (SingleStores.pollDataPrevayler execute PollEventSubscription(pollEvent.id))
              .map{case (_path,_values) =>
                OdfInfoItem(_path,_values.sortBy(_.timestamp.getTime()))}

            //  .mapValues(_.sortBy(_.timestamp.getTime).map(subVal => OdfValue())) //sort values by timestamp and convert them to OdfValue type
            //  .map{case (_path, valueVec) => OdfInfoItem(_path, valueVec)} // Map to Infoitems
              .map(i => createAncestors(i)) //Map to OdfObjects
              .fold(emptyTree)(_.union(_))

            Some(eventData)//eventData.map(eData => Some(eData))
          }

          case pollInterval: PollIntervalSub => {

            log.info(s"Creating response message for Polled Interval Subscription")

            val interval: Duration = pollInterval.interval

            //val intervalData: Future[Map[Path, Vector[OdfValue]]] =

            val intervalData= (SingleStores.pollDataPrevayler execute PollIntervalSubscription(pollInterval.id))
              .mapValues(_.sortBy(_.timestamp.getTime()))
            //dbConnection.pollIntervalSubscription(id).map(_.toVector
            //  .groupBy(n => n.path)
            //  .mapValues(_.sortBy(_.timestamp.getTime).map(_.toOdf)))

            val combinedWithPaths =
              OdfTypes  //TODO easier way to get child paths... maybe something like prefix map
              .getOdfNodes(pollInterval.paths.flatMap(path => odfTree.get(path)):_*)
              .map( n => n.path)
              .map(p => p -> Vector[OdfValue]()).toMap ++ intervalData



            val pollData: OdfObjects = combinedWithPaths.map( pathValuesTuple =>{

              val (path, values) = pathValuesTuple match {
                case (p, v) if (v.nonEmpty) => {
                  v.lastOption match { 
                    case Some(last) =>
                    log.info(s"Found previous values for intervalsubscription: $last")
                    (p, v.:+(last.copy(timestamp = new Timestamp(pollTime))))
                    case None => 
                    val msg =s"Found previous values for intervalsubscription, but lastOption is None, should not be possible."
                    log.error(msg)
                    throw new Exception(msg)
                  }
                } //add polltime
                case (p, v) => {
                  log.info(s"No values found for path: $p in Interval subscription poll for sub id ${pollSub.id}")
                  val latestValue = SingleStores.latestStore execute LookupSensorData(p) match {
                    //lookup latest value from latestStore, if exists use that
                    case Some(value) => {
                    log.info(s"Found old value from latestStore for sub ${pollInterval.id}")
                    Vector(value,value.copy(timestamp = new Timestamp(pollTime)))
                    }
                    //no previous values v is empty
                    case _ => {
                    log.info("No previous value found return empty values.")
                    v
                    }
                  }
                  (p, latestValue)
                }

              }
              val calculatedData = {//Refactor
                val buffer: collection.mutable.Buffer[OdfValue] = collection.mutable.Buffer()
                val lastPolled = pollInterval.lastPolled.getTime()
                val pollTimeOffset = (lastPolled - pollInterval.startTime.getTime()) % pollInterval.interval.toMillis
                val interval  = pollInterval.interval.toMillis
                var nextTick = lastPolled + (interval - pollTimeOffset)

                if(values.length >= 2){
                  var i = 1 //Intentionally 1 and not 0
                  var previousValue = values.head

                  while(i < values.length){
                    if(values(i).timestamp.getTime >= (nextTick)){
                      buffer += previousValue
                      nextTick += interval
                    } else { //if timestmap.getTime < startime + interval
                        previousValue = values(i)
                        i += 1
                    }
                  }
                  //overcomplicated??
                  if( previousValue.timestamp.getTime != pollTime && 
                      previousValue.timestamp.getTime() > lastPolled && 
                      previousValue.timestamp.getTime() > (nextTick - interval))
                    buffer += previousValue
                  Some(buffer.toVector)
                } else None
              }

                calculatedData.map(cData => path -> cData)
              }).flatMap{ n => //flatMap removes None values
              //create OdfObjects from InfoItems
              n.map{case ( path, values) => createAncestors(OdfInfoItem(path, values))}
            }.fold(emptyTree)(_.union(_))
            Some(pollData)
          }

        }
      }
      case _ => None
    }
  }

  /**
   * Method called when the interval of an interval subscription has passed
   */
  private def handleIntervals(): Unit = {
    //TODO add error messages from requesthandler

    log.info("handling infoitems")
    val currentTime = System.currentTimeMillis()
    val hTree = SingleStores.hierarchyStore execute GetTree()
    val (iSubs, nextRunTimeOption) = SingleStores.subStore execute GetIntervals

    if(iSubs.isEmpty) {
      log.warning("HandleIntervals called when no intervals passed")
    } else {

      //send new data to callback addresses
      iSubs.foreach { iSub =>
        log.info(s"Trying to send subscription data to ${iSub.callback}")
        val subPaths = iSub.paths.map(path => (path,hTree.get(path)))
        val (failures, nodes) = subPaths.foldLeft[(Seq[Path], Seq[OdfNode])]((Seq(), Seq())){
            case ((paths, _nodes), (p,Some(node))) => (paths, _nodes.:+(node))
            case ((paths, nodes), (p, None))    => (paths.:+(p), nodes)
          }
        val subscribedInfoItems = OdfTypes
          .getInfoItems(nodes:_*)

        val datas = SingleStores.latestStore execute LookupSensorDatas(subscribedInfoItems.map(_.path))
        val objects: Vector[OdfObjects] = datas.map {
          case (iPath, oValue) =>

            createAncestors(OdfInfoItem(iPath, Iterable(oValue)))
        }

        val optionObjects: Option[OdfObjects] = objects.foldLeft[Option[OdfObjects]](None){
          case (s, n) => Some(s.fold(n)(prev=> prev.union(n)))
        }
        val succResult = optionObjects.map(odfObjects => responses.Results.odf("200", None, Some(iSub.id.toString), odfObjects)).toSeq
        val failedResults = failures.map(fail => Results.simple("404", Some(s"Could not find path: ${fail}.")))
        val resultXml = OmiGenerator.xmlFromResults(iSub.interval.toSeconds.toDouble, (succResult ++ failedResults): _*)



        val callbackF = CallbackHandlers.sendCallback(iSub.callback, resultXml, iSub.interval)
          callbackF.onSuccess {
            case CallbackSuccess() =>
              log.info(s"Callback sent; subscription id:${iSub.id} addr:${iSub.callback} interval:${iSub.interval}")
              case _ =>
                log.warning( s"Callback success, default case should not happen; subscription id:${iSub.id} addr:${iSub.callback} interval:${iSub.interval}")
            }
            callbackF.onFailure{
            case fail: CallbackFailure =>
              log.warning(
                s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${fail.toString}")
            case e : Throwable=>
              log.warning(
                s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${e.getMessage}")
          }
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
    lazy val removeIS = SingleStores.subStore execute RemoveIntervalSub(id)
    lazy val removePS = SingleStores.subStore execute RemovePollSub(id)
    lazy val removeES = SingleStores.subStore execute RemoveEventSub(id)
    if (removePS) {
      SingleStores.pollDataPrevayler execute RemovePollSubData(id)
      removePS
    } else {
      removeIS || removeES
    }
  }

  private def getAllSubs() = {
    log.info("getting list of all subscriptions")
    val intervalSubs = SingleStores.subStore execute GetAllIntervalSubs()
    val eventSubs = SingleStores.subStore execute GetAllEventSubs()
    val pollSubs = SingleStores.subStore execute GetAllPollSubs()
    (intervalSubs, eventSubs, pollSubs)
  }
  private def getSub( id: Long ) = {
    val intervalSubs = SingleStores.intervalPrevayler execute GetAllIntervalSubs()
    val eventSubs = SingleStores.eventPrevayler execute GetAllEventSubs()
    val pollSubs = SingleStores.pollPrevayler execute GetAllPollSubs()
    val allSubs = intervalSubs ++ eventSubs ++ pollSubs 
    allSubs.find{ sub => sub.id == id}
  }

  /**
   * Method used to add subcriptions to Prevayler database
   * @param subscription SubscriptionRequest of the subscription to add
   * @return Subscription Id within Try monad if adding fails this is a Failure, otherwise Success(id)
   */
  private def subscribe(subscription: SubscriptionRequest): Try[Long] = {
    Try {
      val newId = SingleStores.idPrevayler execute GetAndUpdateId
      val endTime = subEndTimestamp(subscription.ttl)
      val currentTime = System.currentTimeMillis()
      val currentTimestamp = new Timestamp(currentTime)

      val subId = subscription.callback match {
        case cb @ Some(callback) => subscription.interval match {
          case Duration(-1, duration.SECONDS) => {
            //normal event subscription


            SingleStores.subStore execute AddEventSub(
              EventSub(
                newId,
                OdfTypes.getLeafs(subscription.odf).iterator.map(_.path).toSeq,
                endTime,
                callback
              )
            )
            log.debug(s"Successfully added event subscription with id: $newId and callback: $callback")
            newId
          }
          case dur@Duration(-2, duration.SECONDS) => throw new NotImplementedError("Interval -2 not supported")//subscription for new node
          case dur: FiniteDuration if dur.gteq(minIntervalDuration)=> {

            SingleStores.subStore execute AddIntervalSub(
              IntervalSub(newId,
                OdfTypes.getLeafs(subscription.odf).iterator.map(_.path).toSeq,
                endTime,
                callback,
                dur,
                new Timestamp(currentTime + dur.toMillis),
                currentTimestamp
              )
            )

            log.info(s"Successfully added interval subscription with id: $newId and callback $callback")
            handleIntervals()
            newId
          }
          case dur : Duration => {
            val msg = s"Duration $dur is unsupported"
            log.error(msg)
            throw new Exception(msg)
          }
        }
        case None => {
          val paths = OdfTypes.getLeafs(subscription.odf).iterator.map(_.path).toSeq
          subscription.interval match{
            case Duration(-1, duration.SECONDS) => {
              //event poll sub
              SingleStores.subStore execute AddPollSub(
                PollEventSub(
                  newId,
                  endTime,
                  currentTimestamp,
                  currentTimestamp,
                  paths
                )
              )

              log.debug(s"Successfully added polled event subscription with id: $newId")
              newId
            }
            case dur: FiniteDuration if dur.gteq(minIntervalDuration) => {
              //interval poll
              SingleStores.subStore execute AddPollSub(
                PollIntervalSub(
                  newId,
                  endTime,
                  dur,
                  currentTimestamp,
                  currentTimestamp,
                  paths
                )
              )
              newId
            }
            case dur: Duration => {
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
