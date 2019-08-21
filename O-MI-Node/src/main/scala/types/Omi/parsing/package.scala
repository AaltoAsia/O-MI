package types
package omi

import utils._
import scala.util.Try
import scala.concurrent.duration._
import java.sql.Timestamp
import java.time.OffsetDateTime
import akka.stream.alpakka.xml._
import types._

package object `parsing` {
  def unexpectedEventHandle(msg: String, event: ParseEvent, builder: EventBuilder[_]): EventBuilder[_] ={
    event match {
      case content: TextEvent if content.text.replaceAll("\\s","").nonEmpty => throw OMIParserError(s"Unexpected text content( ${content.text} ) $msg")
      case start: StartElement => throw OMIParserError(s"Unexpected start of ${start.localName} element $msg")
      case end: EndElement =>throw OMIParserError(s"Unexpected end of ${end.localName} element $msg")
      case EndDocument =>throw OMIParserError(s"Unexpected end of document $msg")
      case StartDocument =>throw OMIParserError(s"Unexpected start of document $msg")
      case other: ParseEvent => builder 
    }
  }
  def parseTTL(v: Double): Duration =
    v match {
      case -1.0 => Duration.Inf
      case 0.0 => Duration.Inf
      case w if w > 0 => w.seconds
      case _ => throw new IllegalArgumentException("Negative Interval, diffrent than -1 isn't allowed.")
    }
}
