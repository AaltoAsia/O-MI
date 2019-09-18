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
import java.util.concurrent.ConcurrentHashMap

import akka.actor.{Actor, ActorRef, ActorLogging, Cancellable, Props, Scheduler}
import akka.pattern.pipe
import database._
import http.CLICmds.{GetSubsWithPollData, ListSubsCmd, SubInfoCmd}
import http.OmiConfigExtension
import http.MetricsReporter
import responses.CallbackHandler.{CallbackFailure, MissingConnection}
import types.omi._
import types._
import types.odf._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, duration}
import scala.util.{Random, Success}

/**
  * Message for triggering handling of intervalsubscriptions
  */
case class HandleIntervals(id: Long)

/**
  * New subscription event
  *
  * @param subscription Subscription to be added
  */
case class NewSubscription(subscription: SubscriptionRequest)

/**
  * Remove subscription event
  *
  * @param id Id of the subscription to remove
  */
case class RemoveSubscription(id: RequestID, ttl: FiniteDuration)

/**
  * Remove subscription event, used for removing subscrpitions when ttl is over
  *
  * @param id
  */
case class SubscriptionTimeout(id: Long)

case class AllSubscriptions(intervals: Set[IntervalSub], events: Set[EventSub], polls: Set[PolledSub])

/**
 * Used for loading subscriptions during runtime
 *
 * @param subs list of subscriptions to be added along with optional poll subscription data
 */
case class LoadSubs(subs: Seq[(SavedSub, Option[SubData])])


/**
 * Event for polling pollable subscriptions
 *
 * @param id Id of the subscription to poll
 */
case class PollSubscription(id: RequestID, ttl: FiniteDuration)

object SubscriptionManager {
  def props(
    settings: OmiConfigExtension,
    singleStores: SingleStores,
    callbackHandler: CallbackHandler,
    metricsReporter: ActorRef
    ): Props = Props(
      new SubscriptionManager(
        settings,
        singleStores,
        callbackHandler,
        metricsReporter
      )
    )
}


/**
 * Class that handles event and interval based subscriptions.
 * Uses Akka scheduler to schedule ttl handling and intervalhandling
 */
class SubscriptionManager(
  protected val settings: OmiConfigExtension,
  protected val singleStores: SingleStores,
  protected val callbackHandler: CallbackHandler,
  protected val metricsReporter: ActorRef
) extends Actor with ActorLogging {
  val minIntervalDuration = Duration(1, duration.SECONDS)
  val ttlScheduler = new SubscriptionScheduler
  val intervalScheduler: Scheduler = context.system.scheduler
  val intervalMap: ConcurrentHashMap[Long, Cancellable] = new ConcurrentHashMap

  /**
   * Schedule remove operation for subscriptions that are in journal stores,
   * only run at startup
   */
  private[this] def scheduleTtls(): Future[Unit] = {
    log.debug("Scheduling removesubscriptions for the first time...")
    //interval subs
    val intervalSubsF: Future[Set[IntervalSub]] = singleStores.getAllIntervalSubs()
    intervalSubsF.foreach{
      isubs =>
        metricsReporter ! MetricsReporter.SetSubscriptionCount( true, "interval",isubs.size )
    }

    val allESubsF: Future[Set[EventSub]] = singleStores.getAllEventSubs()
    allESubsF.foreach{
      esubs =>
        metricsReporter ! MetricsReporter.SetSubscriptionCount( true, "event",esubs.size )
    }
    val allPollSubsF: Future[Set[PolledSub]] = singleStores.getAllPollSubs()
    allPollSubsF.foreach{
      case subs: Set[PolledSub] =>
        val (intervals, events) = subs.partition{ 
          case sub: PolledEventSub => false
          case sub: NotNewEventSub => true
        }
        metricsReporter ! MetricsReporter.SetSubscriptionCount( false, "event",events.size )
        metricsReporter ! MetricsReporter.SetSubscriptionCount( false, "interval",intervals.size )
    }
    val currentTime: Long = System.currentTimeMillis()
    val setupFuture: Future[Unit] = for {
      intervalSubs <- intervalSubsF
      allESubs <- allESubsF
      allPollSubs <- allPollSubsF
      allSubs: Set[SavedSub] = allESubs ++ allPollSubs ++ intervalSubs
      temp = intervalSubs.foreach { iSub =>
        val subTime = currentTime - iSub.startTime.getTime
        val initialDelay = (iSub.interval.toMillis - (subTime % iSub.interval.toMillis)).millis
        intervalMap
          .putIfAbsent(iSub.id, intervalScheduler.schedule(initialDelay, iSub.interval, self, HandleIntervals(iSub.id)))
      }
      res: Unit = allSubs.foreach { sub =>
        if (sub.endTime.getTime != Long.MaxValue) {
          val nextRun = (sub.endTime.getTime - currentTime).millis

          if (nextRun.toMillis > 0L) {
            ttlScheduler.scheduleOnce(nextRun, self, SubscriptionTimeout(sub.id))
          } else {
            self ! SubscriptionTimeout(sub.id)
          }
        }
      }
    } yield res
    setupFuture.onComplete(res => log.debug("Scheduling done"))
    setupFuture
  }

  Await.ready(scheduleTtls(), Duration.Inf)

  //TODO FIX handleIntervals() //when server restarts

  def receive: PartialFunction[Any, Unit] = {
    case NewSubscription(subscription) => subscribe(subscription).pipeTo(sender())
    case HandleIntervals(id) => handleIntervals(id)
    case RemoveSubscription(id, ttl) => removeSubscription(id).pipeTo(sender())
    case SubscriptionTimeout(id) => removeSubscription(id)
    case PollSubscription(id, ttl) => pollSubscription(id).pipeTo(sender())
    case ListSubsCmd(ttl) => getAllSubs().pipeTo(sender())
    case GetSubsWithPollData(ttl) => getSubsWithPollData().pipeTo(sender())
    case SubInfoCmd(id, ttl) => getSub(id).pipeTo(sender())
    case LoadSubs(subs: Seq[(SavedSub, Option[SubData])]) => loadSub(subs).pipeTo(sender())
  }

  /**
   * Used to load subscriptions during runtime using cli
   *
   * @param subs list of subs and optional poll subscription data
   */
  private def loadSub(subs: Seq[(SavedSub, Option[SubData])]): Future[Unit] = {
    val allSubsF: Future[AllSubscriptions] = getAllSubs()
    for {
      allSubs <- allSubsF
      existingIds: Set[Long] = (allSubs.polls ++ allSubs.intervals ++ allSubs.events).map(_.id)
      res: Unit = subs.foreach {
        case (sub: PolledEventSub, data) if !existingIds.contains(sub.id) => {
          for {
            _ <- singleStores.addSub(sub)
            res1 = data.foreach(sData => {
              for {
                (path, data) <- sData.pathData
                value <- data
                res2 = singleStores.addPollData(sub.id, path, value)
              } yield res2
            })

          } yield res1

        }
        case (sub: PollIntervalSub, data) if !existingIds.contains(sub.id) => {
          for {
            _ <- singleStores.addSub(sub)
            res1 = data.foreach(sData => {
              for {
                (path, data) <- sData.pathData
                value <- data
                res2 = singleStores.addPollData(sub.id, path, value)
              } yield res2
            })

          } yield res1
        }
        case (sub: EventSub, _) if !existingIds.contains(sub.id) => {
          singleStores.addSub(sub)
        }
        case (sub: IntervalSub, _) if !existingIds.contains(sub.id) => {
          singleStores.addSub(sub).map(_ =>
              intervalMap
                .put(sub.id, intervalScheduler.schedule(sub.interval, sub.interval, self, HandleIntervals(sub.id))))


        }
        case (sub, _) if existingIds.contains(sub.id) => log.error(s"subscription with id ${sub.id} already exists")
        case sub => log.error("Unknown subscription:" + sub)
      }
    } yield res
  }


  private def handlePollEvent(pollEvent: PolledEventSub): Future[ImmutableODF] = {
    log.debug(s"Creating response message for Polled Event Subscription")
    val eventDataF: Future[Map[Path, Seq[Value[Any]]]] =
      singleStores.pollEventSubscrpition(pollEvent.id)
    for {
      eventData <- eventDataF
      res: ImmutableODF = ImmutableODF(
        eventData.map {
          case (path: Path, values: Seq[Value[Any]]) =>
            InfoItem(path, values.sortBy(_.timestamp.getTime()).toVector)
        }.toVector
        )
    } yield res
  }

  private def calculateIntervals(pollInterval: PollIntervalSub,
    values: Seq[Value[Any]],
    pollTime: Long): Option[Vector[Value[Any]]] = {
      //Refactor
      val buffer: collection.mutable.Buffer[Value[Any]] = collection.mutable.Buffer()
      val lastPolled = pollInterval.lastPolled.getTime
      val pollTimeOffset = (lastPolled - pollInterval.startTime.getTime) % pollInterval.interval.toMillis
      val interval = pollInterval.interval.toMillis
      var nextTick = lastPolled + (interval - pollTimeOffset)

      if (values.length >= 2) {
        var i = 1 //Intentionally 1 and not 0
        var previousValue = values.head

        while (i < values.length) {
          if (values(i).timestamp.getTime >= nextTick) {
            buffer += previousValue
            nextTick += interval
          } else {
            //if timestamp.getTime < starttime + interval
            previousValue = values(i)
            i += 1
          }
        }
        //overcomplicated??
        if (previousValue.timestamp.getTime != pollTime &&
          previousValue.timestamp.getTime > lastPolled &&
          previousValue.timestamp.getTime > (nextTick - interval))
        buffer += previousValue
        Some(buffer.toVector)
      } else None
  }

  private def handlePollInterval(pollInterval: PollIntervalSub, pollTime: Long, odf: ODF): Future[ImmutableODF] = {

    log.info(s"Creating response message for Polled Interval Subscription")

    val intervalDataF: Future[Map[Path, Seq[Value[Any]]]] = singleStores.pollIntervalSubscription(pollInterval.id)
    //.mapValues(_.sortBy(_.timestamp.getTime()))
    for {
      intervalDataP <- intervalDataF
      intervalData: Map[Path, Seq[Value[Any]]] = intervalDataP.mapValues(_.sortBy(_.timestamp.getTime()))
      combinedWithPaths: Map[Path, Seq[Value[Any]]] = odf.selectSubTree(pollInterval.paths.toSet).getInfoItems.map {
        ii: InfoItem =>
          ii.path -> Vector[Value[Any]]()
      }.toMap[Path, Seq[Value[Any]]] ++ intervalData
      pollDatas <- Future.sequence(combinedWithPaths.map {
        case (path: Path, values: Seq[Value[Any]]) if values.nonEmpty =>
          values.lastOption match {
            case Some(last) =>
              log.info(s"Found previous values for intervalsubscription: $last")
              val tuple: (Path, Seq[Value[Any]]) = (path, values :+ last.retime(new Timestamp(pollTime)))
              Future.successful[(Path, Seq[Value[Any]])](tuple)

            case None =>
              val msg = s"Found previous values for intervalsubscription, but lastOption is None, should not be possible."
              log.error(msg)
              Future.failed(new Exception(msg))
          }
            case (path: Path, values: Seq[Value[Any]]) if values.isEmpty =>
              log.info(s"No values found for path: $path in Interval subscription poll for sub id ${pollInterval.id}")
              val latestValue: Future[Seq[Value[Any]]] =
                singleStores.readValue(path).map {
                  //lookup latest value from latestStore, if exists use that
            case Some(value: Value[Any]) => {
              log.info(s"Found old value from latestStore for sub ${pollInterval.id}")
              Vector(value, value.retime(new Timestamp(pollTime)))
            }
            //no previous values v is empty
            case _ => {
              log.info("No previous value found return empty values.")
              values
            }
                }
              latestValue.map(lval => path -> lval)
              //path -> latestValue
      })
      pollData = pollDatas.flatMap {
        case (path: Path, values: Seq[Value[Any]]) =>
          calculateIntervals(pollInterval, values, pollTime).map {
            calculatedData: Vector[Value[Any]] => path -> calculatedData
          }
      }
      iisWithValues: Iterable[InfoItem] = pollData.map {
        case (path: Path, values: Seq[Value[Any]]) =>
          InfoItem(path, values)
      }
      result = ImmutableODF(iisWithValues)

    } yield result
  }

  /**
   * Get pollsubscriptions data from database
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
  private def pollSubscription(id: Long): Future[Option[ODF]] = {
    val pollTime: Long = System.currentTimeMillis()
    val subF: Future[Option[PolledSub]] = singleStores.pollSubscription(id)
    for {
      sub: Option[PolledSub] <- subF
      res: Option[ODF] <- sub match {
        case Some(ps: PolledSub) => {
          log.debug(s"Polling subscription with id: ${ps.id}")
          val odfTreeF: Future[ImmutableODF] = singleStores.getHierarchyTree()
          for {
            odfTree: ImmutableODF <- odfTreeF
            emptyTree: ODF = odfTree.selectSubTree(ps.paths.toSet).valuesRemoved.metaDatasRemoved.descriptionsRemoved
            subValues: ImmutableODF <- ps match {
              case pollEvent: PolledEventSub => handlePollEvent(pollEvent)
              case pollInterval: PollIntervalSub => handlePollInterval(pollInterval, pollTime, odfTree)
              case other =>
                log.warning(s"Found invalid polled sub type $other")
                Future.successful(ImmutableODF(Vector.empty))
            }
            res: ODF = subValues.union(emptyTree)
          } yield Some(res)
          //pollSubscription method removes the data from database and returns the requested data
        }
              case None => Future.successful(None)
      }
    } yield res
  }

  /**
   * Method called when the interval of an interval subscription has passed
   */
  private def handleIntervals(id: Long): Future[Unit] = {
    //TODO add error messages from requesthandler
    log.debug(s"handling interval sub with id: $id")
    val intervalSubscriptionOptionF: Future[Option[IntervalSub]] = singleStores.getIntervalSub(id)
    for {
      intervalSubscriptionOption <- intervalSubscriptionOptionF
      res: Unit <- intervalSubscriptionOption match {

        case Some(iSub: IntervalSub) => { //same as if exists
          val ret: Future[Unit] = for {
            hTree: ImmutableODF <- singleStores.getHierarchyTree()
            subedTree: ODF = hTree.selectSubTree(iSub.paths.toSet).metaDatasRemoved.descriptionsRemoved
            datas: Iterable[(Path, Value[Any])] <-
            singleStores.readValues(subedTree.getInfoItems.map(_.path).toSeq)

            odfWithValues = subedTree.union(
              ImmutableODF(datas.map {
                case (path: Path, value: Value[Any]) => InfoItem(path, Vector(value))
              })
            )
            foundPaths = odfWithValues.getPaths
            missedPaths = iSub.paths.filterNot {
              path: Path => foundPaths.contains(path)
            }
            succResult = Vector(Results.Success(Vector(iSub.id), Some(odfWithValues)))
            failedResults = if (missedPaths.nonEmpty) Vector(Results.SubscribedPathsNotFound(missedPaths)) else Vector
              .empty
              responseTTL = iSub.interval
              response = ResponseRequest((succResult ++ failedResults), responseTTL, requestToken=Some(id))

              callbackF <- callbackHandler
                .sendCallback(iSub.callback, response) // FIXME: change resultXml to ResponseRequest(..., responseTTL)

          } yield callbackF
          ret.foreach {
            case () =>
              log.debug(s"Callback sent; subscription id:${iSub.id} addr:${iSub.callback} interval:${iSub.interval}")
          }
          ret.failed.foreach {
            case fail@MissingConnection(callback) =>
              log.warning(
                s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${
                  fail
                    .toString
                }, subscription is removed.")
              removeSubscription(iSub.id)
            case fail: CallbackFailure =>
              log.warning(
                s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${fail.toString}")
              case e: Throwable =>
                log.warning(
                  s"Callback failed; subscription id:${iSub.id} interval:${iSub.interval}  reason: ${e.getMessage}")
          }
          ret
        }
              case None => Future.successful[Unit]( (): Unit)
      }
    } yield res
  }


  /**
   * Method used for removing subscriptions using their Id
   *
   * @param id Id of the subscription to remove
   * @return Boolean indicating if the removing was successful
   */
  private def removeSubscription(id: Long): Future[Boolean] = {
    singleStores.getRequestInfo(id) // remove entry from request info store

    Option(intervalMap.get(id)).foreach(_.cancel())

    val ret: Future[Boolean] = singleStores.getPolledSub(id).flatMap{ 
      case Some( eventSub: PolledEventSub ) => 
        metricsReporter ! MetricsReporter.RemoveSubscription(false,"event")
        singleStores.removePollSub(id).flatMap{ 
          case true => singleStores.removePollSubData(id).map(reply => true)
          case false => Future.successful(false)
        }
      case Some( eventSub: PollIntervalSub ) => 
        metricsReporter ! MetricsReporter.RemoveSubscription(false,"interval")
        singleStores.removePollSub(id).flatMap{ 
          case true => singleStores.removePollSubData(id).map(reply => true)
          case false => Future.successful(false)
        }
      case None => singleStores.removeIntervalSub(id).flatMap {
        case true => 
          metricsReporter ! MetricsReporter.RemoveSubscription(true,"interval")
          Future.successful(true)
        case false => 
          metricsReporter ! MetricsReporter.RemoveSubscription(true,"event")
          singleStores.removeEventSub(id)
      }

    }
    ret
  }

  private def getAllSubs(): Future[AllSubscriptions] = {
    log.info("getting list of all subscriptions")
    val intervalSubsF: Future[Set[IntervalSub]] = singleStores.getAllIntervalSubs()
    val eventSubsF: Future[Set[EventSub]] = singleStores.getAllEventSubs()
    val pollSubsF: Future[Set[PolledSub]] = singleStores.getAllPollSubs()
    for {
      intervalSubs <- intervalSubsF
      eventSubs <- eventSubsF
      pollSubs <- pollSubsF
    } yield AllSubscriptions(intervalSubs, eventSubs, pollSubs)
  }

  private def getSubsWithPollData(): Future[Seq[(SavedSub, Option[SubData])]] = {
    val allSubsF = getAllSubs()
    for {
      AllSubscriptions(intervals, events, polls) <- allSubsF
      res: Seq[(SavedSub, Option[SubData])] <- Future.sequence((intervals ++ events ++ polls).collect {
        case e: EventSub => Future.successful((e, None))
        case i: IntervalSub => Future.successful((i, None))
        case pe: PolledEventSub => {
          singleStores.checkSubData(pe.id).map(data =>
              (pe,
                Some(SubData(data))))
        }
        case pi: PollIntervalSub => {
          singleStores.checkSubData(pi.id).map(data =>
              (pi,
                Some(SubData(data))))
        }
      }.toSeq)
    } yield res
  }

  private def getSub(id: Long): Future[Option[SavedSub]] = {
    val intervalSubsF: Future[Set[IntervalSub]] = singleStores.getAllIntervalSubs()
    val eventSubsF: Future[Set[EventSub]] = singleStores.getAllEventSubs()
    val pollSubsF: Future[Set[PolledSub]] = singleStores.getAllPollSubs()
    for {
      intervalSubs <- intervalSubsF
      eventSubs <- eventSubsF
      pollSubs <- pollSubsF
      allSubs = intervalSubs ++ eventSubs ++ pollSubs
    } yield allSubs.find(sub => sub.id == id)
  }

  private val rand = new Random()

  /**
   * Method used to add subscriptions to journal database
   *
   * @param subscription SubscriptionRequest of the subscription to add
   * @return Subscription Id within Try monad if adding fails this is a Failure, otherwise Success(id)
   */
  private def subscribe(subscription: SubscriptionRequest): Future[Long] = {
    lazy val allSubsF = getAllSubs()
    lazy val allIdsF: Future[Set[Long]] = allSubsF
      .map(allSubs => (allSubs.events ++ allSubs.intervals ++ allSubs.polls).map(_.id))

      def getNewId: Future[Long] = {
        val idF = subscription.requestToken match {
          case Some(id) => Future.successful(id)
          case None => 
            log.warning("Using subscription id fallback generation because request token not found.")
            val nId: Long = rand.nextInt(Int.MaxValue)
            allIdsF.flatMap{
              allIds =>
                if (allIds.contains(nId))
                  getNewId
                else
                  Future.successful(nId)
            }
        }
        idF 
      }

      val endTime = subEndTimestamp(subscription.ttl)
      val currentTime = System.currentTimeMillis()
      val currentTimestamp = new Timestamp(currentTime)
      val subscribedOdf = subscription.odf

      val subId: Future[Long] = subscription.callback match {
        case cb@Some(callback: RawCallback) =>
          Future.failed(RawCallbackFound(s"Tried to subscribe with RawCallback: ${callback.address}"))
        case cb@Some(callback: DefinedCallback) => subscription.interval match {
          case Duration(-1, duration.SECONDS) => {
            //normal event subscription
            val newSubId: Future[Long] = for {
              newId <- getNewId
              addedSub <- singleStores.addSub(
                NormalEventSub(
                  newId,
                  subscribedOdf.getLeafs.map(_.path),
                  endTime,
                  callback
                )
              )
            } yield newId
            newSubId.onComplete {
              case Success(id) =>
                log.info(s"Successfully added event subscription with id: $id and callback: $callback")
                metricsReporter ! MetricsReporter.NewSubscription(subscription.callback.nonEmpty,"event")
              case other =>
            }
            newSubId
          }
              case dur@Duration(-2, duration.SECONDS) => {
                val newSubId: Future[Long] = for {
                  newId <- getNewId
                  addedSub <- singleStores.addSub(
                    NewEventSub(
                      newId,
                      subscribedOdf.getLeafs.map(_.path),
                      endTime,
                      callback
                    )
                  )
                } yield newId

                newSubId.onComplete {
                  case Success(id) =>
                    log.info(s"Successfully added event subscription for new events with id: $id and callback: $callback")
                    metricsReporter ! MetricsReporter.NewSubscription(subscription.callback.nonEmpty,"event")
                  case other =>
                }
                newSubId
              } //subscription for new node
                  case dur: FiniteDuration if dur.gteq(minIntervalDuration) => {
                    val newSubId: Future[Long] = for {
                      newId <- getNewId
                      iSub = IntervalSub(
                        newId,
                        subscribedOdf.getLeafs.map(_.path),
                        endTime,
                        callback,
                        dur,
                        currentTimestamp)
                      addedSub <- singleStores.addSub(iSub)
                      temp = intervalMap.put(newId, intervalScheduler.schedule(dur, dur, self, HandleIntervals(newId)))
                    } yield newId
                    newSubId.onComplete {
                      case Success(id) =>
                        log.info(s"Successfully added interval subscription with id: $id and callback $callback")
                        metricsReporter ! MetricsReporter.NewSubscription(subscription.callback.nonEmpty,"interval")
                      case other =>
                    }
                    newSubId
                  }
                      case dur: Duration => {
                        val msg = s"Duration $dur is unsupported"
                        log.error(msg)
                        Future.failed(new Exception(msg))
                      }
        }
                      case None => {
                        val paths = subscribedOdf.getLeafs.map(_.path)
                        subscription.interval match {
                          case Duration(-1, duration.SECONDS) => {
                            //event poll sub
                            val newSubId: Future[Long] = for {
                              newId <- getNewId
                              addedSub <- singleStores.addSub(
                                PollNormalEventSub(
                                  newId,
                                  endTime,
                                  currentTimestamp,
                                  currentTimestamp,
                                  paths
                                )
                              )
                            } yield newId

                            newSubId.onComplete {
                              case Success(id) =>
                                log.info(s"Successfully added polled event subscription with id: $id")
                                metricsReporter ! MetricsReporter.NewSubscription(subscription.callback.nonEmpty,"event")
                              case other =>
                            }
                            newSubId
                          }
                              case Duration(-2, duration.SECONDS) => {
                                val newSubId: Future[Long] = for {
                                  newId <- getNewId
                                  addedSub <- singleStores.addSub(
                                    PollNewEventSub(
                                      newId,
                                      endTime,
                                      currentTimestamp,
                                      currentTimestamp,
                                      paths
                                    )
                                  )
                                } yield newId
                                newSubId.onComplete {
                                  case Success(id) =>
                                    log.info(s"Successfully added polled new data event subscription with id: $id")
                                    metricsReporter ! MetricsReporter.NewSubscription(subscription.callback.nonEmpty,"event")
                                  case other =>
                                }
                                newSubId
                              }


                                  case dur: FiniteDuration if dur.gteq(minIntervalDuration) => {
                                    //interval poll
                                    val newSubId: Future[Long] = for {
                                      newId <- getNewId
                                      addedSub <- singleStores.addSub(
                                        PollIntervalSub(
                                          newId,
                                          endTime,
                                          dur,
                                          currentTimestamp,
                                          currentTimestamp,
                                          paths
                                        )
                                      )
                                    } yield newId
                                    newSubId.foreach{
                                      id => 
                                        metricsReporter ! MetricsReporter.NewSubscription(subscription.callback.nonEmpty,"interval")
                                    }
                                    newSubId
                                  }
                                  case dur: Duration => {
                                    log.error(s"Duration $dur is unsupported")
                                    Future.failed(new Exception(s"Duration $dur is unsupported"))
                                  }

                        }
                      }
      }
      subId.foreach(id => subscription.ttl match {
        case dur: FiniteDuration => ttlScheduler.scheduleOnce(dur, self, SubscriptionTimeout(id))
      case _ =>
    })
    subId
  }

  /**
    * Helper method to get the Timestamp for removing the subscription
    *
    * @param subttl time to live of the subscription
    * @return endTime of subscription as Timestamp
    */
  private def subEndTimestamp(subttl: Duration): Timestamp = {
    if (subttl.isFinite()) {
      new Timestamp(System.currentTimeMillis() + subttl.toMillis)
    } else {
      new Timestamp(Long.MaxValue)
    }
  }


}
