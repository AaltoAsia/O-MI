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

import scala.language.postfixOps
import java.net.InetAddress
import java.sql.Timestamp
import java.util.Date

import scala.collection.immutable.{HashMap}
import akka.http.scaladsl.model.Uri
import spray.json.{DefaultJsonProtocol, JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, RootJsonFormat}
import types._
import types.odf._
import types.OmiTypes._
import journal.Models.PersistentSub
import journal.{PPollNormalEventSub, PPollNewEventSub, PNewEventSub, PIntervalSub, PNormalEventSub, PPollIntervalSub}

import collection.breakOut
import scala.concurrent.duration._

sealed trait SavedSub {
  val id: Long
  val endTime: Date
  val paths: Vector[Path]

  def persist(): PersistentSub
}

trait NotNewEventSub extends PolledSub

sealed trait PolledSub extends SavedSub {
  val lastPolled: Timestamp
  val startTime: Timestamp //Used for preventing from saving duplicate values in database and debugging
}

sealed trait PolledEventSub extends PolledSub {
  val id: Long
  val endTime: Timestamp
  val lastPolled: Timestamp
  val startTime: Timestamp
  val paths: Vector[Path]
}

case class PollNormalEventSub(
                               id: Long,
                               endTime: Timestamp,
                               lastPolled: Timestamp,
                               startTime: Timestamp,
                               paths: Vector[Path]
                             ) extends PolledEventSub with NotNewEventSub {
  def persist() = PPollNormalEventSub(id, endTime.getTime, lastPolled.getTime, startTime.getTime, paths.map(_.toString))
}

case class PollNewEventSub(
                            id: Long,
                            endTime: Timestamp,
                            lastPolled: Timestamp,
                            startTime: Timestamp,
                            paths: Vector[Path]
                          ) extends PolledEventSub {
  def persist() = PPollNewEventSub(id, endTime.getTime, lastPolled.getTime, startTime.getTime, paths.map(_.toString))
}

case class PollIntervalSub(
                            id: Long,
                            endTime: Timestamp,
                            interval: Duration,
                            lastPolled: Timestamp,
                            startTime: Timestamp,
                            paths: Vector[Path]
                          ) extends NotNewEventSub {
  def persist() = PPollIntervalSub(id,
    endTime.getTime,
    interval.toSeconds,
    lastPolled.getTime,
    startTime.getTime,
    paths.map(_.toString))
}

case class IntervalSub(
                        id: Long,
                        paths: Vector[Path],
                        endTime: Timestamp,
                        callback: DefinedCallback,
                        interval: FiniteDuration,
                        startTime: Timestamp
                      ) extends SavedSub {
  def persist() = PIntervalSub(id,
    paths.map(_.toString),
    endTime.getTime,
    callback.persist(),
    interval.toSeconds,
    startTime.getTime)
}

case class NormalEventSub(
                           id: Long,
                           paths: Vector[Path],
                           endTime: Timestamp,
                           callback: DefinedCallback
                         ) extends EventSub {
  def persist() = PNormalEventSub(id, paths.map(_.toString), endTime.getTime, callback.persist())
}

//startTime: Duration) extends SavedSub

case class NewEventSub(
                        id: Long,
                        paths: Vector[Path],
                        endTime: Timestamp,
                        callback: DefinedCallback
                      ) extends EventSub {
  def persist() = PNewEventSub(id, paths.map(_.toString), endTime.getTime, callback.persist())
}

sealed trait EventSub extends SavedSub {
  val paths: Vector[Path]
  val endTime: Timestamp
  val callback: DefinedCallback

}


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

//case class PollSubData(
//                        idToData: collection.mutable.HashMap[Long, collection.mutable.HashMap[Path, List[Value[Any]]]])

case class SubData(
                    pathData: Map[Path, Seq[Value[Any]]]
                  )

//object PollSubData {
//  def empty: PollSubData = PollSubData(collection.mutable.HashMap.empty)
//}

object CustomJsonProtocol extends DefaultJsonProtocol {

  implicit object SubscriptionJsonFormat extends RootJsonFormat[(SavedSub, Option[SubData])] {
    private def createCB(address: String): DefinedCallback = {
      val uri = Uri(address)
      val hostAddress = uri.authority.host.address()
      InetAddress.getByName(hostAddress)
      val scheme = uri.scheme
      scheme match {
        case "http" => HTTPCallback(uri)
        case "https" => HTTPCallback(uri)
        case _ => throw new Exception(
          "Callback 0 subscription cannot be preserved, subscriptions with 0 callback will be skipped")
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
      case Seq(JsNumber(id),
      JsNumber(endTime),
      JsNumber(interval),
      JsNumber(startTime),
      JsNumber(lastPolled),
      JsNull,
      JsArray(paths: Vector[JsString]),
      JsArray(data: Vector[JsObject])) => {
        val subData: Seq[(Path, List[Value[Any]])] = data.map(_.getFields("path", "values") match {
          case Seq(JsString(path), JsArray(values: Vector[JsObject])) => (Path(path), values
            .map(_.getFields("value", "typeValue", "timeStamp") match {
              case Seq(JsString(value),
              JsString(typeValue),
              JsNumber(timeStamp)
              ) => Value(value,
                typeValue,
                new Timestamp(timeStamp.toLong))
            }).toList)
        })
        if (interval.toLong == -1) { //normal poll sub
          (PollNormalEventSub(
            id.toLong,
            new Timestamp(endTime.toLong),
            new Timestamp(lastPolled.toLong),
            new Timestamp(startTime.toLong),
            paths.map(p => Path(p.value))
          ), Some(SubData(subData.toMap.map(identity)(breakOut))))
        } else if (interval.toLong == -2) { //poll new event sub
          (PollNewEventSub(
            id.toLong,
            new Timestamp(endTime.toLong),
            new Timestamp(lastPolled.toLong),
            new Timestamp(startTime.toLong),
            paths.map(p => Path(p.value))
          ), Some(SubData(subData.toMap.map(identity)(breakOut))))
        } else {
          (PollIntervalSub( //interval poll sub
            id.toLong,
            new Timestamp(endTime.toLong),
            interval.toLong seconds,
            new Timestamp(lastPolled.toLong),
            new Timestamp(startTime.toLong),
            paths.map(p => Path(p.value))
          ), Some(SubData(subData.toMap.map(identity)(breakOut))))
        }
      }
      case Seq(JsNumber(id),
      JsNumber(endTime),
      JsNumber(interval),
      JsNull,
      JsNull,
      JsString(callback),
      JsArray(paths: Vector[JsString]),
      JsNull) if interval.toLong == -1 || interval.toLong == -2 => { //EventSub
        if (interval.toLong == -1) {
          (NormalEventSub(id.toLong,
            paths.map(p => Path(p.value)),
            new Timestamp(endTime.toLong),
            createCB(callback)), None)
        } else if (interval.toLong == -2) {
          (NewEventSub(id.toLong,
            paths.map(p => Path(p.value)),
            new Timestamp(endTime.toLong),
            createCB(callback)), None)
        } else {
          http.Boot.log
            .error(s"Invalid subscription with no start time and interval ${interval.toLong} different than -1 and -2")
          throw new Exception(s"Invalid subscription with no start time and interval ${
            interval
              .toLong
          } different than -1 and -2")
        }

      }
      case Seq(JsNumber(id),
      JsNumber(endTime),
      JsNumber(interval),
      JsNumber(startTime),
      JsNull,
      JsString(callback),
      JsArray(paths: Vector[JsString]),
      JsNull) => { //IntervalSub
        (IntervalSub(id.toLong,
          paths.map(p => Path(p.value)),
          new Timestamp(endTime.toLong),
          createCB(callback),
          interval.toLong seconds,
          new Timestamp(startTime.toLong)), None)
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
                  "typeValue" -> JsString(v.typeAttribute),
                  "timeStamp" -> JsNumber(v.timestamp.getTime)
                )
              ).toVector
            )
          )
      }.toVector)).getOrElse(JsArray.empty)
      sub match {
        case PollNormalEventSub(id, endTime, lastPolled, startTime, paths) => JsObject(
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
        case PollNewEventSub(id, endTime, lastPolled, startTime, paths) => JsObject(
          "id" -> JsNumber(id),
          "endTime" -> JsNumber(endTime.getTime),
          "interval" -> JsNumber(-2),
          "startTime" -> JsNumber(startTime.getTime),
          "lastPolled" -> JsNumber(lastPolled.getTime),
          "callback" -> JsNull,
          "paths" -> JsArray(paths.map(p => JsString(p.toString))),
          "data" -> data
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
        case IntervalSub(id, paths, endTime, callback, interval, startTime) =>
          if (callback.toString == "0") throw new Exception("Callback 0 subscriptions will be skipped")
          JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(interval.toSeconds),
            "startTime" -> JsNumber(startTime.getTime),
            "lastPolled" -> JsNull,
            "callback" -> JsString(callback.toString),
            "paths" -> JsArray(paths.map(p => JsString(p.toString))),
            "data" -> JsNull
          )
        case NormalEventSub(id, paths, endTime, callback) =>
          if (callback.toString == "0") throw new Exception("Callback 0 subscriptions will be skipped")
          JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(-1),
            "startTime" -> JsNull,
            "lastPolled" -> JsNull,
            "callback" -> JsString(callback.toString),
            "paths" -> JsArray(paths.map(p => JsString(p.toString))),
            "data" -> JsNull
          )
        case NewEventSub(id, paths, endTime, callback) =>
          if (callback.toString == "0") throw new Exception("Callback 0 subscriptions will be skipped")
          JsObject(
            "id" -> JsNumber(id),
            "endTime" -> JsNumber(endTime.getTime),
            "interval" -> JsNumber(-2),
            "startTime" -> JsNull,
            "lastPolled" -> JsNull,
            "callback" -> JsString(callback.toString),
            "paths" -> JsArray(paths.map(p => JsString(p.toString))),
            "data" -> JsNull
          )
      }
    }
  }

}

