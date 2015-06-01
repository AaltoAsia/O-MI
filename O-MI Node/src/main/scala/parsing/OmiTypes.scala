package parsing
package Types

import OdfTypes._

import java.sql.Timestamp
import java.lang.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

object OmiTypes{


  /**
   * Trait that represents any Omi request. Provides some data that are common
   * for all omi requests.
   */
  sealed trait OmiRequest {
    def ttl: Double
    def callback: Option[String]
    def hasCallback = callback.isDefined
  }

  /**
   * Trait for subscription like classes. Offers a common interface for subscription types.
   */
  trait SubLike extends OmiRequest {
    // Note: defs can be implemented also as val and lazy val
    def interval: Double
    def ttl: Double
    def isIntervalBased  = interval >= 0.0
    def isEventBased = interval == -1
    def ttlToMillis: Long = (ttl * 1000).toLong
    def intervalToMillis: Long = (interval * 1000).toLong
    def isImmortal = ttl == -1.0
  }


  case class SubDataRequest(sub: database.DBSub) extends OmiRequest {
    val ttl = sub.ttl - (System.currentTimeMillis() - sub.startTime.getTime)*1000
    val callback = sub.callback
  }


case class ReadRequest(
  ttl: Double,
  odf: OdfObjects ,
  begin: Option[ Timestamp ] = None,
  end: Option[ Timestamp ] = None,
  newest: Option[ Int ] = None,
  oldest: Option[ Int ] = None,
  callback: Option[ String ] = None
) extends OmiRequest

case class PollRequest(
  ttl: Double,
  callback: Option[ String ] = None,
  requestIds: Iterable[ Int ] = asJavaIterable(Seq.empty[Int])
) extends OmiRequest

case class SubscriptionRequest(
  ttl: Double,
  interval: Double,
  odf: OdfObjects ,
  newest: Option[ Int ] = None,
  oldest: Option[ Int ] = None,
  callback: Option[ String ] = None
) extends OmiRequest with SubLike

case class WriteRequest(
  ttl: Double,
  odf: OdfObjects,
  callback: Option[ String ] = None
) extends OmiRequest

case class ResponseRequest(
  results: Iterable[OmiResult]  
) extends OmiRequest {
      def callback = None
      def ttl = 0
   }

case class CancelRequest(
  ttl: Double,
  requestId: Iterable[ Int ] = asJavaIterable(Seq.empty[Int])
) extends OmiRequest {
      def callback = None
    }

case class OmiResult(
  value: String,
  returnCode: String,
  description: Option[String] = None,
  requestId: Iterable[ Int ] = asJavaIterable(Seq.empty[Int]),
  odf: Option[OdfTypes.OdfObjects] = None
)

  type  OmiParseResult = Either[Iterable[ParseError], Iterable[OmiRequest]]
  def getRequests( omi: OmiParseResult ) : Iterable[OmiRequest] = 
    omi match{
      case Right(requests: Iterable[OmiRequest]) => requests
      case _ => asJavaIterable(Seq.empty[OmiRequest])
    }
  def getErrors( omi: OmiParseResult ) : Iterable[ParseError] = 
    omi match{
      case Left( pes: Iterable[ParseError]) => pes
      case _ => asJavaIterable(Seq.empty[ParseError])
    }
}
