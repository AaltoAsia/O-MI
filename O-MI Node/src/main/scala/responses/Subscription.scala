package responses

import Common._
import ErrorResponse.intervalNotSupported
import parsing.Types._
import database._
import scala.xml
import scala.xml._
import scala.collection.mutable.{ Buffer, PriorityQueue }
import scala.math.Ordering
import akka.actor.ActorSystem
import scala.concurrent.duration._

import java.sql.Timestamp
import java.util.Date

/**
 * Object for adding subscriptions to database. Handles also subscriptions without callback addresses
 */
object OMISubscription {

  /**
   * typedef for (Int,Long) tuple where values are (subID,ttlInMilliseconds + startTime).
   */
  type SubTuple = (Int, Long)

  /**
   * define ordering for priorityQueue this needs to be reversed when used, so that sub with earliest timeout is first.
   */
  val subOrder: Ordering[SubTuple] = Ordering.by(_._2)

  /**
   * PriorityQueue with subOrder ordering. value with earliest timeout is first.
   * This val is lazy and is computed when needed for the first time
   *
   * This queue contains only subs that have no callback address defined and have ttl > 0.
   */
  private val subQueue: PriorityQueue[SubTuple] = new PriorityQueue()(subOrder.reverse)

  // helper method, fills subs to subQueue, called from Boot
  def fillSubQueue()(implicit SQLite: DB) = {
    if (subQueue.isEmpty) {
      val subArray = SQLite.getAllSubs(Some(false)).filter(n => n.ttl > 0).map(n => (n.id, n.ttlToMillis + n.startTime.getTime))
      subQueue ++= subArray
    }
  }

  /**
   * method for getting current time without always having to call new Date() directly
   *
   * @return new Date object
   */
  private def date = new Date()

  //bring the ActorSystem in scope
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system = ActorSystem()

  //tuple with scheduled event and the time it executes
  //used to keep only 1 schedule running, no need for multiple schedulers
  var scheduledTimes: Option[(akka.actor.Cancellable, Long)] = None

  /**
   * This method is called by scheduler and when new sub is added to subQueue.
   *
   * This method removes all subscriptions that have expired from the priority queue and
   * schedules new checkSub method call.
   */
  def checkSubs()(implicit SQLite: DB): Unit = {
    
    val currentTime = date.getTime
    
    //exists returns false if Option is None
    while (subQueue.headOption.exists(_._2 <= currentTime)) {
      SQLite.removeSub(subQueue.dequeue()._1)
    }
    
    //foreach does nothing if Option is None
    subQueue.headOption.foreach { n =>
      val nextRun = ((n._2) - currentTime)
      val cancellable = system.scheduler.scheduleOnce(nextRun.milliseconds)(checkSubs)
      if (scheduledTimes.forall(_._1.isCancelled)) {
        scheduledTimes = Some((cancellable, currentTime + nextRun))
      } else if (scheduledTimes.exists(_._2 > (currentTime + nextRun))) {
        scheduledTimes.foreach(_._1.cancel())
        scheduledTimes=Some((cancellable,currentTime+nextRun))
      }
      else if (scheduledTimes.exists(n =>((n._2 > currentTime) && (n._2 < (currentTime + nextRun))))) {
        cancellable.cancel()
      }
    }
  }

  /**
   * Creates a subscription in the database and generates the immediate answer to a subscription.
   * If the subscription doesn't have callback and has finite ttl this method adds it in the subQueue
   * priority queue.
   *
   * @param subscription an object of Subscription class which contains information about the request
   * @return A tuple with the first element containing the requestId and the second element
   * containing the immediate xml that's used for responding to a subscription request
   */

  def setSubscription(subscription: Subscription)(implicit SQLite: DB): (Int, xml.NodeSeq) = {
    var requestIdInt: Int = -1
    val paths = getInfoItemPaths(subscription.sensors.toList)

    if (paths.isEmpty == false) {
      val xml =
        omiResult {
          returnCode200 ++
            requestId {

              val ttlInt = subscription.ttl.toInt
              val interval = subscription.interval.toInt
              val callback = subscription.callback
              val timeStamp = Some(new Timestamp(date.getTime()))
              requestIdInt = SQLite.saveSub(
                new DBSub(paths.toArray, ttlInt, interval, callback, timeStamp))

              if (callback.isEmpty && ttlInt > 0) {
                subQueue.enqueue((requestIdInt, (ttlInt * 1000).toLong + timeStamp.get.getTime))
                checkSubs
              }
              requestIdInt
            }
        }

      return (requestIdInt, xml)
    } else {
      val xml =
        omiResult {
          returnCode(400, "No InfoItems found in the paths")
        }

      return (requestIdInt, xml)
    }
  }

  /**
   * Creates the right hierarchy from the infoitems that have been subscribed to. If sub has no callback (hascallback == false),
   * get the values accumulated between the sub starttime and current time.
   *
   * @param paths The paths of the infoitems that have been subscribed to
   * @param index Index of the current 'level'. Used because it recursively drills deeper.
   * @param starttime Start time of the subscription
   * @param interval interval Interval of the subscription
   * @param hascallback Boolean value indicating if callback exists
   * @return The ODF hierarchy as XML
   */

  def createFromPaths(
        paths: Array[Path],
        index: Int,
        starttime: Timestamp,
        interval: Double,
        hascallback: Boolean
      )(implicit SQLite: DB): xml.NodeSeq = {
    var node: xml.NodeSeq = xml.NodeSeq.Empty

    if (paths.isEmpty == false) {
      var slices = Buffer[Path]()
      var previous = paths.head

      for (path <- paths) {
        var slicedpath = Path(path.toSeq.slice(0, index + 1))
        SQLite.get(slicedpath) match {
          case Some(sensor: DBSensor) => {

            node ++=
              <InfoItem name={ sensor.path.last }>
                {
                  if (hascallback) { <value dateTime={ sensor.time.toString.replace(' ', 'T') }>{ sensor.value }</value> }
                  else { getAllvalues(sensor, starttime, interval) }
                }
                {
                  val metaData = SQLite.getMetaData(sensor.path)
                  if (metaData.isEmpty == false) { XML.loadString(metaData.get) }
                  else { xml.NodeSeq.Empty }
                }
              </InfoItem>
          }

          case Some(obj: DBObject) => {
            if (path(index) == previous(index)) {
              slices += path
            } else {
              node ++= <Object>
                         <id>
                           { previous(index) }
                         </id>
                         { createFromPaths(slices.toArray, index + 1, starttime, interval, hascallback) }
                       </Object>
              slices = Buffer[Path](path)
            }

          }

          case None => { node ++= <Error> Item not found in the database </Error> }
        }

        previous = path

        //in case this is the last item in the array, we check if there are any non processed paths left
        if (path == paths.last) {
          if (slices.isEmpty == false) {
            node ++= <Object>
                       <id>
                         { slices.last.toSeq(index) }
                       </id>
                       { createFromPaths(slices.toArray, index + 1, starttime, interval, hascallback) }
                     </Object>
          }
        }
      }

    }

    return node
  }

  /**
   * Uses the Dataformater from database package to get a list of the values that have been accumulated during the start of the sub and the request
   *
   * @param sensor The InfoItem that's been subscribed to
   * @param starttime Start time of the subscription
   * @param interval Interval of the subscription
   * @return The values accumulated in ODF format
   */

  def getAllvalues(sensor: DBSensor, starttime: Timestamp, interval: Double
      )(implicit SQLite: DB): xml.NodeSeq = {
    var node: xml.NodeSeq = xml.NodeSeq.Empty

    val infoitemvaluelist = {
      if (interval != -1) DataFormater.FormatSubData(sensor.path, starttime, interval, None)
      else {
        /*filter out elements that have the same value as previous entry*/
        //NOTE 
        SQLite.getNBetween(sensor.path, Some(starttime), None, None, None)
          .foldLeft(Array[DBSensor]())((a, b) => if (a.lastOption.exists(n => n.value == b.value)) a else a :+ b)
      }
    }

    for (innersensor <- infoitemvaluelist) {
      node ++= <value dateTime={ innersensor.time.toString.replace(' ', 'T') }>{ innersensor.value }</value>
    }

    node
  }

  /**
   * Used for getting only the infoitems from the request. If an Object is subscribed to, get all the infoitems
   * that are children (or children's children etc.) of that object.
   *
   * @param objects A hierarchy of the ODF-structure that the parser creates
   * @return Paths of the infoitems
   */

  def getInfoItemPaths(objects: List[OdfObject])(implicit SQLite: DB): Buffer[Path] = {
    var paths = Buffer[Path]()
    for (obj <- objects) {
      /*      //just an object has been subscribed to
      if (obj.childs.nonEmpty == false && obj.sensors.nonEmpty == false) {

      }*/

      if (obj.childs.nonEmpty) {
        paths ++= getInfoItemPaths(obj.childs.toList)
      }

      if (obj.sensors.nonEmpty) {
        for (sensor <- obj.sensors) {
          SQLite.get(sensor.path) match {
            case Some(infoitem: DBSensor) => paths += infoitem.path
            case _ => //do nothing
          }
        }
      }
    }
    return paths
  }

  /**
   * Poll Subscription response
   */
  class PollResponseGen(implicit SQLite: DB) extends ResponseGen[PollRequest] {
    

    override def genResult(poll: PollRequest) = {
      val results = poll.requestIds.map{ id =>

        val sub = SQLite.getSub(id)
        sub match {
          case None => {
            resultWrapper {
              returnCode(404, "A subscription with this id has expired or doesn't exist") ++
                requestId(id)
            }
          }

          case Some(subscription) => {
            pollSub(subscription)
          }
        }
      }
      results.reduceLeft(_ ++ _)
    }

    /**
     * Help method for odfGeneration, also used for generating data in ODF-format
     *
     * @param subId the Id of the subscription
     * @return The data in ODF-format
     */
    private def pollSub(subId: DBSub): OmiResult = {
      val interval = subId.interval

      if (interval == -2) { // not supported
        intervalNotSupported
      } else if (interval == -1) { //Event based subscription
        val start = subId.startTime.getTime
        val currentTimeLong = date.getTime()
        
        val ttlDecreased = ((subId.ttl * 1000).toLong - (currentTimeLong - start)) / 1000.0
        
        //if subscription has expired just before polling:
        if(subId.ttl > 0 && ttlDecreased < 0){
          checkSubs
          return resultWrapper {
            returnCode(404, "A subscription with this id has expired or doesn't exist") ++
              requestId(subId.id)
          }
        }
        val newTTL: Double = {
          if (subId.ttl <= 0) subId.ttl
          else ttlDecreased
        }

        SQLite.setSubStartTime(subId.id, new Timestamp(currentTimeLong), newTTL)

        resultWrapper {
          returnCode200 ++
            requestId(subId.id) ++
            odfMsgWrapper(
              <Objects>
                { createFromPaths(subId.paths, 1, subId.startTime, subId.interval, false) }
              </Objects>)
        }

      } else if (interval == 0) {
        intervalNotSupported
      } else if (interval > 0) { //Interval based subscription

        val start = subId.startTime.getTime
        val currentTimeLong = date.getTime()
        //calculate new start time to be divisible by interval to keep the scheduling
        //also reduce ttl by the amount that startTime was changed
        val intervalMillisLong = (subId.interval * 1000).toLong
        val newStartTimeLong = start + (intervalMillisLong * ((currentTimeLong - start) / intervalMillisLong)) //sub.startTime.getTime + ((intervalMillisLong) * ((currentTimeLong - sub.startTime.getTime) / intervalMillisLong).toLong)
        val newTTL: Double = if (subId.ttl <= 0.0) subId.ttl else { //-1 and 0 have special meanings
          ((subId.ttl * 1000).toLong - (newStartTimeLong - start)) / 1000.0
        }

        SQLite.setSubStartTime(subId.id, new Timestamp(newStartTimeLong), newTTL)

        odfResultWrapper {
          returnCode200 ++
            requestId(subId.id) ++
            odfMsgWrapper(
              <Objects>
                { createFromPaths(subId.paths, 1, subId.startTime, subId.interval, false) }
              </Objects>)
        }

      } else {
        intervalNotSupported
      }
    }

  }

  /**
   * Used for generating data in ODF-format. When the subscription has callback set it acts like a OneTimeRead with a requestID,
   * when it doesn't have a callback it generates the values accumulated in the database.
   *
   * @param sub subscription that the response is generated for
   * @return The data in ODF-format
   */
  class SubscriptionResponseGen(implicit SQLite: DB) extends ResponseGen[SubDataRequest] {
    override def genResult(subreq: SubDataRequest) = {
      val sub = subreq.sub
      assert(sub.callback.isDefined)
      odfResultWrapper {
        returnCode200 ++
        requestId(sub.id) ++
        odfMsgWrapper(
          <Objects>
            { createFromPaths(sub.paths, 1, sub.startTime, sub.interval, sub.callback.isDefined) }
          </Objects>)
      }
    }


  }
}

