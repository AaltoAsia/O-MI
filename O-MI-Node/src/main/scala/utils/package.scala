import scala.language.implicitConversions
import java.sql.Timestamp
import java.util.Date
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl.XmlWriting

package object utils {
  def merge[A, B](a: Map[A, B], b: Map[A, B])(mergef: (B, Option[B]) => B): Map[A, B] = {
    val (bigger, smaller) = if (a.size > b.size) (a, b) else (b, a)
    smaller.foldLeft(bigger) { case (z, (k, v)) => z + (k -> mergef(v, z.get(k))) }
  }

  implicit def asOption[A](a: A): Option[A] = Option(a)
  def currentTimestamp: Timestamp = new Timestamp( new Date().getTime())
  def parseEventsToByteSource( events: Iterable[ParseEvent] ) = {
    Source
      .fromIterator(() => events.iterator)
      .via( XmlWriting.writer )
      .filter(_.length != 0)
  }
}
