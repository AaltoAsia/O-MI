package database

import java.sql.Timestamp

import akka.http.scaladsl.model.Uri
import CustomJsonProtocol._
import org.specs2.mutable.Specification
import spray.json._
import types.OmiTypes.HTTPCallback
import types.Path
import types.odf.Value

import scala.collection.immutable.HashMap
import scala.concurrent.duration._

class BackupTest extends Specification {
  val startTime = new Timestamp(Int.MaxValue)
  val endTime = new Timestamp(Long.MaxValue)
  val polledMinus1 = PollNormalEventSub(11,
    endTime,
    startTime,
    startTime,
    Vector(
      Path("Objects/a/b/1"),
      Path("Objects/a/b/2"),
      Path("Objects/a/b/3")))
  val polledMinus2 = PollNewEventSub(22,
    endTime,
    startTime,
    startTime,
    Vector(
      Path("Objects/a/b/1"),
      Path("Objects/a/b/2"),
      Path("Objects/a/b/3")
    ))
  val pollInterval = PollIntervalSub(33,
    endTime,
    10 seconds,
    startTime,
    startTime,
    Vector(
      Path("Objects/a/b/1"),
      Path("Objects/a/b/2"),
      Path("Objects/a/b/3")
    )
  )
  val normInterval = IntervalSub(44,
    Vector(
      Path("Objects/a/b/1"),
      Path("Objects/a/b/2"),
      Path("Objects/a/b/3")
    ),
    endTime,
    HTTPCallback(Uri("http://localhost:8432")),
    10 seconds,
    startTime)
  val normEventSub = NormalEventSub(55,
    Vector(
      Path("Objects/a/b/1"),
      Path("Objects/a/b/2"),
      Path("Objects/a/b/3")
    ),
    endTime,
    HTTPCallback(Uri("http://localhost:8432")))
  val newEventSub = NewEventSub(66,
    Vector(
      Path("Objects/a/b/1"),
      Path("Objects/a/b/2"),
      Path("Objects/a/b/3")
    ),
    endTime,
    HTTPCallback(Uri("http://localhost:8432")))

  val subData = Some(
    SubData(HashMap((Path("Objects/a/b/2"),
                      List(Value(1, new Timestamp(System.currentTimeMillis())),
                        Value(2, new Timestamp(System.currentTimeMillis() + 1)))))))

  "Conversion between subscription-json-subscription transformation" should {
    "Work for polled -1 interval subscriptions" in {
      val in: (SavedSub, Option[SubData]) = (polledMinus1, subData)
      val jsver = in.toJson
      val jsonver = jsver.prettyPrint
      val parsed = jsonver.parseJson
      val convertedSub: (SavedSub, Option[SubData]) = parsed.convertTo[(SavedSub, Option[SubData])]
      convertedSub === (polledMinus1, subData)
    }
    "Work for polled -2 interval subscriptions" in {
      val in: (SavedSub, Option[SubData]) = (polledMinus2, subData)
      val jsver = in.toJson
      val jsonver = jsver.prettyPrint
      val parsed = jsonver.parseJson
      val convertedSub: (SavedSub, Option[SubData]) = parsed.convertTo[(SavedSub, Option[SubData])]
      convertedSub === (polledMinus2, subData)
    }
    "Work for polled interval subscriptions" in {
      val in: (SavedSub, Option[SubData]) = (pollInterval, subData)
      val jsver = in.toJson
      val jsonver = jsver.prettyPrint
      val parsed = jsonver.parseJson
      val convertedSub: (SavedSub, Option[SubData]) = parsed.convertTo[(SavedSub, Option[SubData])]
      convertedSub === (pollInterval, subData)
    }
    "Work for interval subscriptions" in {
      val in: (SavedSub, Option[SubData]) = (normInterval, None)
      val jsver = in.toJson
      val jsonver = jsver.prettyPrint
      val parsed = jsonver.parseJson
      val convertedSub: (SavedSub, Option[SubData]) = parsed.convertTo[(SavedSub, Option[SubData])]
      convertedSub === (normInterval, None)
    }
    "Work for -1 interval subscriptions" in {
      val in: (SavedSub, Option[SubData]) = (normEventSub, None)
      val jsver = in.toJson
      val jsonver = jsver.prettyPrint
      val parsed = jsonver.parseJson
      val convertedSub: (SavedSub, Option[SubData]) = parsed.convertTo[(SavedSub, Option[SubData])]
      convertedSub === (normEventSub, None)
    }
    "Work for -2 interval subscriptions" in {
      val in: (SavedSub, Option[SubData]) = (newEventSub, None)
      val jsver = in.toJson
      val jsonver = jsver.prettyPrint
      val parsed = jsonver.parseJson
      val convertedSub: (SavedSub, Option[SubData]) = parsed.convertTo[(SavedSub, Option[SubData])]
      convertedSub === (newEventSub, None)
    }
  }
}
