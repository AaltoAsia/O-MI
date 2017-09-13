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

package database

import java.net.InetAddress
import java.sql.Timestamp
import java.util.Date

import scala.annotation.meta.field
import scala.collection.immutable.{HashMap, SortedSet}
import scala.collection.mutable
import scala.concurrent.duration.{Duration, FiniteDuration}
import akka.actor.Cancellable
import akka.http.scaladsl.model.Uri
import org.prevayler._
import spray.json.{DefaultJsonProtocol, JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}
import types.OdfTypes._
import types._
import types.OmiTypes._
import collection.breakOut
import scala.util.parsing.json.JSONObject
import scala.concurrent.duration._

sealed trait SavedSub {
  val id: Long
  val endTime: Date
  val paths: Vector[Path]
}
sealed trait PolledSub extends SavedSub {
  val lastPolled: Timestamp
  val startTime: Timestamp  //Used for preventing from saving duplicate values in database and debugging
}


case class SubIds(var id: Long)
case class PollEventSub(
  id: Long,
  endTime: Timestamp,
  lastPolled: Timestamp,
  startTime: Timestamp,
  paths: Vector[Path]
  ) extends PolledSub

case class PollIntervalSub(
  id: Long,
  endTime: Timestamp,
  interval: Duration,
  lastPolled: Timestamp,
  startTime: Timestamp,
  paths: Vector[Path]
) extends PolledSub

case class IntervalSub(
  id: Long,
  paths: Vector[Path],
  endTime: Timestamp,
  callback: DefinedCallback,
  interval: FiniteDuration,
  startTime: Timestamp
  ) extends SavedSub

case class EventSub(
  id: Long,
  paths: Vector[Path],
  endTime: Timestamp,
  callback: DefinedCallback
  ) extends SavedSub//startTime: Duration) extends SavedSub

/** from Path string to event subs for that path */

case class Subs(
               var eventSubs: HashMap[Path, Vector[EventSub]],
               var idToSub: HashMap[Long, PolledSub], 
               var pathToSubs: HashMap[Path, Set[Long]],
               var intervalSubs: HashMap[Long, IntervalSub])
object Subs {
  def empty: Subs = Subs(
    HashMap.empty,
    HashMap.empty,
    HashMap.empty,
    HashMap.empty)
}

case class PollSubData(
                        idToData: collection.mutable.HashMap[Long, collection.mutable.HashMap[Path, List[OdfValue[Any]]]])

case class SubData(
                  pathData: HashMap[Path,List[OdfValue[Any]]]
                  )

object PollSubData {
  def empty: PollSubData = PollSubData(collection.mutable.HashMap.empty)
}
  class CustomJsonProtocol(implicit singleStores: SingleStores) extends DefaultJsonProtocol{
  implicit object SubscriptionJsonFormat extends RootJsonFormat[(SavedSub, Option[SubData])] {
    private def createCB(address: String): DefinedCallback = {
      val uri = Uri(address)
      val hostAddress = uri.authority.host.address()
      val ipAddress = InetAddress.getByName(hostAddress)
      val scheme = uri.scheme
      scheme match{
        case "http" => HTTPCallback(uri)
        case "https" => HTTPCallback(uri)
        case _ => ??? //TODO is it possible to create ws callback here?
      }

  }
    def read(in: JsValue): (SavedSub, Option[SubData]) = in.asJsObject.getFields(
      "id",
      "endTime",
      "interval",
      "startTime",
      "lastPolled",
      "callback",
      "paths",
      "data") match {
      case Seq(JsNumber(id), JsNumber(endTime), JsNumber(interval), JsNumber(startTime), JsNumber(lastPolled), JsNull, JsArray(paths: Vector[JsString]), JsArray(data: Vector[JsObject])) if interval.toLong == -1=> { // PollEventsub
        val subData: Seq[(Path, List[OdfValue[Any]])] = data.map(_.getFields("path","values") match {
          case Seq(JsString(path), JsArray(values: Vector[JsObject])) => (Path(path), values.map(_.getFields("value", "typeValue", "timeStamp", "attributes") match {
            case Seq(JsString(value), JsString(typeValue), JsNumber(timeStamp), JsObject(attributes: Map[String, JsString])) =>  OdfValue(value, typeValue, new Timestamp(timeStamp.toLong), attributes.map{case (key, jsvalue) => (key, jsvalue.value)}(breakOut))
          }).toList)
        })

        (PollEventSub(id.toLong,new Timestamp(endTime.toLong), new Timestamp(lastPolled.toLong),new Timestamp(startTime.toLong), paths.map(p => Path(p.value))),
        Some(SubData(subData.toMap.map(identity)(breakOut))))
      }
      case Seq(JsNumber(id), JsNumber(endTime), JsNumber(interval), JsNumber(startTime), JsNumber(lastPolled), JsNull, JsArray(paths: Vector[JsString]), JsArray(data: Vector[JsObject])) => { //PollInterval
        val subData: Seq[(Path, List[OdfValue[Any]])] = data.map(_.getFields("path","values") match {
          case Seq(JsString(path), JsArray(values: Vector[JsObject])) => (Path(path), values.map(_.getFields("value", "typeValue", "timeStamp", "attributes") match {
            case Seq(JsString(value), JsString(typeValue), JsNumber(timeStamp), JsObject(attributes: Map[String, JsString])) =>  OdfValue(value, typeValue, new Timestamp(timeStamp.toLong), attributes.map{case (key, jsvalue) => (key, jsvalue.value)}(breakOut))
          }).toList)
        })

        (PollIntervalSub(id.toLong, new Timestamp(endTime.toLong),interval.toLong seconds, new Timestamp(lastPolled.toLong),new Timestamp(startTime.toLong), paths.map(p => Path(p.value))),
          Some(SubData(subData.toMap.map(identity)(breakOut))))
      }
      case Seq(JsNumber(id), JsNumber(endTime), JsNumber(interval), JsNull, JsNull, JsString(callback), JsArray(paths: Vector[JsString]), JsNull) if interval.toLong == -1 => { //EventSub
        (EventSub(id.toLong,paths.map(p=> Path(p.value)),new Timestamp(endTime.toLong),createCB(callback)), None)
      }
      case Seq(JsNumber(id), JsNumber(endTime), JsNumber(interval), JsNumber(startTime), JsNull, JsString(callback), JsArray(paths: Vector[JsString]), JsNull) => { //IntervalSub
        (IntervalSub(id.toLong, paths.map(p => Path(p.value)), new Timestamp(endTime.toLong), createCB(callback), interval.toLong seconds, new Timestamp(startTime.toLong)), None)
      }
    }

    def write(obj: (SavedSub, Option[SubData])): JsValue = {
      val sub = obj._1
      val data: JsArray = obj._2.map(n => JsArray(n.pathData.map {
                  case (path, values) =>
                    JsObject(
                      "path" -> JsString(path.toString),
                      "values" -> JsArray(
                        values.map(v =>
                          JsObject(
                            "value" -> JsString(v.value.toString),
                            "typeValue" -> JsString(v.typeValue),
                            "timeStamp" -> JsNumber(v.timestamp.getTime),
                            "attributes" -> JsObject(v.attributes.mapValues(a => JsString(a)))
                          )
                        ).toVector
                      )
                    )
                }.toVector)).getOrElse(JsArray.empty)
        sub match {
          case PollEventSub(id, endTime, lastPolled, startTime, paths) => JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(-1),
            "startTime" -> JsNumber(startTime.getTime),
            "lastPolled" -> JsNumber(lastPolled.getTime),
            "callback" -> JsNull,
            "paths" -> JsArray(paths.map(p => JsString(p.toString))),
            "data" -> data
              /*JsArray(
              (singleStores.pollDataPrevayler execute CheckSubscriptionData(id))
                .map {
                  case (path, values) =>
                    JsObject(
                      "path" -> JsString(path.toString),
                      "values" -> JsArray(
                        values.map(v =>
                          JsObject(
                            "value" -> JsString(v.value.toString),
                            "typeValue" -> JsString(v.typeValue),
                            "timeStamp" -> JsNumber(v.timestamp.getTime),
                            "attributes" -> JsObject(v.attributes.mapValues(a => JsString(a)))
                          )
                        ).toVector
                      )
                    )
                }.toVector)*/
          )
          case PollIntervalSub(id, endTime, interval, lastPolled, startTime, paths) => JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(interval.toSeconds),
            "startTime" -> JsNumber(startTime.getTime),
            "lastPolled" -> JsNumber(lastPolled.getTime),
            "callback" -> JsNull,
            "paths" -> JsArray(paths.map(p => JsString(p.toString))),
            "data" -> data
          )
          case IntervalSub(id, paths, endTime, callback, interval, startTime) => JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(interval.toSeconds),
            "startTime" -> JsNumber(startTime.getTime),
            "lastPolled" -> JsNull,
            "callback" -> JsString(callback.toString),
            "paths" -> JsArray(paths.map(p => JsString(p.toString))),
            "data" -> JsNull
          )
          case EventSub(id, paths, endTime, callback) => JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(-1),
            "startTime" -> JsNull,
            "lastPolled" -> JsNull,
            "callback" -> JsString(callback.toString),
            "paths" -> JsArray(paths.map(p => JsString(p.toString)))
          )
        }
    }
  }
}

/**
 * Transaction for adding data for polled subscriptions. Does not check if the sub actually exists
 * @param subId
 * @param path
 * @param value
 */
case class AddPollData(subId: Long, path: Path, value: OdfValue[Any]) extends Transaction[PollSubData] {
  def executeOn(p: PollSubData, date: Date): Unit = {
    p.idToData.get(subId) match {
      case Some(pathToValues) => pathToValues.get(path) match {
        case Some(sd) =>
          p.idToData(subId)(path) = value :: sd
        case None =>
          p.idToData(subId).update(path, List(value))
      }
      case None =>
        p.idToData
          .update(
            subId,
            collection.mutable.HashMap(path -> List(value)))
    }
  }
}

case class CheckSubscriptionData(subId: Long) extends Query[PollSubData, collection.mutable.HashMap[Path,List[OdfValue[Any]]]] {
  def query(p: PollSubData, date: Date): mutable.HashMap[Path, List[OdfValue[Any]]] = {
    p.idToData.get(subId).getOrElse(collection.mutable.HashMap.empty[Path, List[OdfValue[Any]]])
  }
}

/**
 * Used to Poll event subscription data from the prevayler. Can also used to remove data from subscription
 * @param subId
 */
case class PollEventSubscription(subId: Long) extends TransactionWithQuery[PollSubData, collection.mutable.HashMap[Path,List[OdfValue[Any]]]] {
  def executeAndQuery(p: PollSubData, date: Date): collection.mutable.HashMap[Path, List[OdfValue[Any]]] = {
    p.idToData.remove(subId).getOrElse(collection.mutable.HashMap.empty[Path,List[OdfValue[Any]]])
  }
}

/**
 * Used to poll an interval subscription with the given ID.
 * Polling interval subscriptions leaves the newest value in the database
 * so this can't be used to remove subscriptions.
 * @param subId
 */
case class PollIntervalSubscription(subId:Long) extends TransactionWithQuery[PollSubData, collection.mutable.HashMap[Path, List[OdfValue[Any]]]]{
  def executeAndQuery(p: PollSubData, date: Date): mutable.HashMap[Path, List[OdfValue[Any]]] = {
    val removed = p.idToData.remove(subId)

    removed match {
      case Some(old) => {
        //add the empty sub as placeholder
        p.idToData += (subId -> mutable.HashMap.empty)
        old.foreach {
          case (path, oldValues) if oldValues.nonEmpty => {
            val newest = oldValues.maxBy(_.timestamp.getTime)

            //add latest value back to the database
            p.idToData(subId).get(path) match {
              case Some(oldV) => p.idToData(subId)(path) = newest :: oldV
              case None => p.idToData(subId).update(path, newest :: Nil)
            }
          }
          case _ =>
        }
        old
      }

      case None => mutable.HashMap.empty
    }

  }
}

/**
 * Used to remove data from interval and event based poll subscriptions.
 * @param subId ID of the sub to remove
 */
case class RemovePollSubData(subId: Long) extends Transaction[PollSubData] {
  def executeOn(p: PollSubData, date: Date): Unit = {
    p.idToData.remove(subId)
  }
}


case class LookupEventSubs(path: Path) extends Query[Subs, Vector[EventSub]] {
  def query(es: Subs, d: Date): Vector[EventSub] =
    (path.getParentsAndSelf flatMap (p => es.eventSubs.get(p))).flatten.toVector // get for Map returns Option (safe)
}

case class RemoveWebsocketSubs() extends TransactionWithQuery[Subs, Unit] {
  def executeAndQuery(store: Subs, d: Date): Unit= {
    store.intervalSubs = store.intervalSubs.filter{
      case (_, IntervalSub(_,_,_,cb: CurrentConnectionCallback, _, _)) =>{
      false
      }
      case _  => true
    }
    store.eventSubs = HashMap[Path,Vector[EventSub]](store.eventSubs.mapValues{
      subs =>
        subs.filter{
          case EventSub(_,_,_,callback: CurrentConnectionCallback) => false
          case sub: EventSub => true
          }
          }.toSeq:_*)
  }
}
// Other transactions are in responses/SubscriptionHandler.scala
