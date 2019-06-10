package types
package odf
import utils._
import scala.util.Try
import java.sql.Timestamp
import java.time.OffsetDateTime

package object `parser` {
  def dateTimeStrToTimestamp(dateTimeStr: String): Timestamp = {
    Timestamp.from(OffsetDateTime.parse(dateTimeStr).toInstant())
  }
  def solveTimestamp(dateTime: Option[String], unixTime: Option[String], receiveTime: Timestamp): Timestamp = {
    val dT = dateTime.map(dateTimeStrToTimestamp)
    val uT = unixTime.flatMap{
      str =>
        Try{
          new Timestamp((str.toDouble * 1000.0).toLong)
        }.toOption
    }
    (dT,uT) match{
      case (Some(dTs), Some(uTs)) => dTs
      case (Some(ts),None) => ts
      case (None,Some(ts)) => ts
      case (None,None) => receiveTime
    }

  }
}
