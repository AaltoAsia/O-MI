import scala.language.implicitConversions
import java.sql.Timestamp
import javax.xml.datatype.DatatypeFactory
import java.util.Date
import akka.stream.scaladsl._
import akka.util.ByteString
import akka.NotUsed
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl.XmlWriting

package object utils {
  def dateTimeStrToTimestamp(dateTimeString: String): Timestamp = {
    val gc = DatatypeFactory.newInstance().newXMLGregorianCalendar(dateTimeString).toGregorianCalendar()
    Timestamp.from(gc.toInstant())
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
}
