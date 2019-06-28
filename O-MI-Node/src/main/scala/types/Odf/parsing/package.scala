package types
package odf

import utils._
import scala.util.Try
import java.sql.Timestamp
import java.time.OffsetDateTime
import akka.stream.alpakka.xml._
import types._

package object `parser` {
  def solveTimestamp(dateTime: Option[Timestamp], unixTime: Option[Timestamp], receiveTime: Timestamp): Timestamp = {
    (dateTime,unixTime) match{
      case (Some(dTs), Some(uTs)) => dTs
      case (Some(ts),None) => ts
      case (None,Some(ts)) => ts
      case (None,None) => receiveTime
    }

  }
  def unexpectedEventHandle(msg: String, event: ParseEvent, builder: EventBuilder[_]): EventBuilder[_] ={
    event match {
      case content: TextEvent if content.text.replaceAll("\\s","").nonEmpty => throw ODFParserError(s"Unexpect text content $msg")
      case start: StartElement => throw ODFParserError(s"Unexpect start of ${start.localName} element $msg")
      case end: EndElement =>throw ODFParserError(s"Unexpect end of ${end.localName} element $msg")
      case EndDocument =>throw ODFParserError(s"Unexpect end of document $msg")
      case StartDocument =>throw ODFParserError(s"Unexpect start of document $msg")
      case other: ParseEvent => builder 
    }
  }
}
