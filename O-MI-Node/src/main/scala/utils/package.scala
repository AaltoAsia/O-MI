import scala.language.implicitConversions
import scala.util.Try
import java.sql.Timestamp
import java.time.{LocalDateTime, OffsetDateTime, ZoneId}

import javax.xml.datatype.DatatypeFactory
import java.util.Date

import akka.stream.scaladsl._
import akka.util.ByteString
import akka.NotUsed
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl.XmlWriting
import com.typesafe.config.Config

package object utils {
  def dateTimeStrToTimestamp(dateTimeString: String): Timestamp = {
    Try{
      OffsetDateTime.parse( dateTimeString ).toInstant()
    }.orElse{
      Try{
        LocalDateTime.parse( dateTimeString ).atZone(ZoneId.systemDefault).toInstant()
      }
    }.map{
      instant =>
      Timestamp.from(instant)
    }.get
  }
  def merge[A, B](a: Map[A, B], b: Map[A, B])(mergef: (B, Option[B]) => B): Map[A, B] = {
    val (bigger, smaller) = if (a.size > b.size) (a, b) else (b, a)
    smaller.foldLeft(bigger) { case (z, (k, v)) => z + (k -> mergef(v, z.get(k))) }
  }

  implicit def asOption[A](a: A): Option[A] = Option(a)
  def currentTimestamp: Timestamp = new Timestamp( new Date().getTime())
  def parseEventsToByteSource( events: Iterable[ParseEvent] ): Source[ByteString,NotUsed] = {
    Source
      .fromIterator(() => events.iterator)
      .via( XmlWriting.writer )
      .filter(_.nonEmpty)
  }
  def parseEventsToStringSource( events: Iterable[ParseEvent] ): Source[String,NotUsed]  = parseEventsToByteSource(events).map[String](_.utf8String)

  implicit class RichConfig(val underlying: Config) extends AnyVal {
    def getOptionalString(path: String): Option[String] = if (underlying.hasPath(path)) Some(underlying.getString(path)) else None
    def getOptionalInt(path: String): Option[Int] = if (underlying.hasPath(path)) Some(underlying.getInt(path)) else None
    def getOptionalConfig(path: String): Option[Config] = if (underlying.hasPath(path)) Some(underlying.getConfig(path)) else None
    def getOptionalBoolean(path: String): Option[Boolean] = if (underlying.hasPath(path)) Some(underlying.getBoolean(path)) else None
  }
}
