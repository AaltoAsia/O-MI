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
package types
package OmiTypes

import java.lang.Iterable
import java.sql.Timestamp
import java.net.URI
import java.util.GregorianCalendar
import java.lang.{Iterable => JIterable}
import javax.xml.datatype.DatatypeFactory

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import scala.language.existentials
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

import akka.http.scaladsl.model.RemoteAddress
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import parsing.xmlGen.xmlTypes.{ObjectsType, OmiEnvelope}
import responses.CallbackHandler
import types.OdfTypes._

trait JavaOmiRequest{
  def callbackAsJava(): JIterable[Callback]
}
/**
  * Trait that represents any Omi request. Provides some data that are common
  * for all omi requests.
  */
sealed trait OmiRequest extends RequestWrapper with JavaOmiRequest{
  def callbackAsJava(): JIterable[Callback] = asJavaIterable(callback)
  def callback: Option[Callback]
  def callbackAsUri: Option[URI] = callback map {cb => new URI(cb.address)}
  def withCallback: Option[Callback] => OmiRequest
  def hasCallback: Boolean = callback.nonEmpty
  //def user(): Option[UserInfo]
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope 

  implicit def asXML : NodeSeq= omiEnvelopeToXML(asOmiEnvelope)
  def parsed: OmiParseResult = Right(asJavaIterable(collection.Iterable(this)))
  def unwrapped: Try[OmiRequest] = Success(this)
  def rawRequest: String = asXML.toString
  final def handleTTL : FiniteDuration = if( ttl.isFinite ) {
        if(ttl.toSeconds != 0)
          FiniteDuration(ttl.toSeconds, SECONDS)
        else
          FiniteDuration(2,MINUTES)
      } else {
        FiniteDuration(Int.MaxValue,MILLISECONDS)
      }
}


/**
 * This means request that is writing values
 */
sealed trait PermissiveRequest

/**
 * Request that contains O-DF, (read, write, response)
 */
sealed trait OdfRequest {
  def odf : OdfObjects
}

sealed trait JavaRequestIDRequest{
  def requestIDsAsJava(): JIterable[RequestID]
}
/**
 * Request that contains requestID(s) (read, cancel) 
 */
sealed trait RequestIDRequest extends JavaRequestIDRequest{
  def requestIDs : OdfTreeCollection[RequestID]
  def requestIDsAsJava : JIterable[RequestID] = asJavaIterable(requestIDs)
}

case class UserInfo(
                   remoteAddress: Option[RemoteAddress]
                   )

sealed trait RequestWrapper {
  //def user(): Option[UserInfo]
  var user: Option[UserInfo] = _
  def rawRequest: String
  def ttl: Duration
  def parsed: OmiParseResult
  def unwrapped: Try[OmiRequest]
  def ttlAsSeconds : Long = ttl match {
    case finite : FiniteDuration => finite.toSeconds
    case infinite : Duration.Infinite => -1
  }
}

/**
 * Defines values from the beginning of O-MI message like ttl and message type
 * without parsing the whole request.
 */
class RawRequestWrapper(val rawRequest: String, private val user0: Option[UserInfo]) extends RequestWrapper {
  import RawRequestWrapper._
  import scala.xml.pull._
  user = user0


  private val parseSingle: () => EvElemStart = {
    val src = io.Source.fromString(rawRequest)
    val er = new XMLEventReader(src)

    {() =>
      // skip to the intresting parts
      er.collectFirst{
        case e: EvElemStart => e
      } getOrElse parseError("no xml elements found")
    }
  }
  // NOTE: Order is important
  val omiEnvelope: EvElemStart = parseSingle()
  val omiVerb: EvElemStart = parseSingle()

  require(omiEnvelope.label == "omiEnvelope", "Pre-parsing: omiEnvelope not found!")

  val ttl: Duration = (for {
      ttlNodeSeq <- Option(omiEnvelope.attrs("ttl"))
      head <- ttlNodeSeq.headOption
      ttl = parsing.OmiParser.parseTTL(head.text.toDouble)
    } yield ttl
  ) getOrElse parseError("couldn't parse ttl")

  /**
   * The verb of the O-MI message (read, write, cancel, response)
   */
  val messageType: MessageType = MessageType(omiVerb.label)

  /**
   * Gets the verb of the O-MI message
   */
  val callback: Option[Callback] = for {
      callbackNodeSeq <- Option(omiEnvelope.attrs("callback"))
      head <- callbackNodeSeq.headOption
      callback = RawCallback(head.text)
    } yield callback
  
  /**
   * Get the parsed request. Message is parsed only once because of laziness.
   */
  lazy val parsed: OmiParseResult = parsing.OmiParser.parse(rawRequest, user)

  /**
   * Access the request easily and leave responsibility of error handling to someone else.
   * TODO: Only one request per xml message is supported currently
   */
  lazy val unwrapped = parsed match {
    case Right(requestSeq) => Try(requestSeq.head)
    case Left(errors) => Failure(ParseError.combineErrors(errors))
  }
}

object RawRequestWrapper {
  def apply(rawRequest: String, user: Option[UserInfo]): RawRequestWrapper = new RawRequestWrapper(rawRequest, user)

  private def parseError(m: String) = throw new IllegalArgumentException("Pre-parsing: " + m)

  sealed trait MessageType

  object MessageType {
    case object Write extends MessageType
    case object Read extends MessageType
    case object Cancel extends MessageType
    case object Response extends MessageType
    def apply(xmlTagLabel: String): MessageType =
      xmlTagLabel match {
        case "write"  => Write
        case "read"   => Read
        case "cancel" => Cancel
        case "response" => Response
        case _ => parseError("read, write or cancel element not found!")
      }
  }
}

/**
  * Trait for subscription like classes. Offers a common interface for subscription types.
  */
trait SubLike {
  // Note: defs can be implemented also as val and lazy val
  def interval: Duration
  def ttl: Duration
  def isIntervalBased : Boolean  = interval >= 0.milliseconds
  def isEventBased: Boolean = interval == -1.seconds
  def ttlToMillis: Long = ttl.toMillis
  def intervalToMillis: Long = interval.toMillis
  def isImmortal: Boolean  = ! ttl.isFinite
  require(interval == -1.seconds || interval >= 0.seconds, s"Invalid interval: $interval")
  require(ttl >= 0.seconds, s"Invalid ttl, should be positive (or +infinite): $interval")
}

/** 
 * One-time-read request
 **/
case class ReadRequest(
  odf: OdfObjects ,
  begin: Option[Timestamp ] = None,
  end: Option[Timestamp ] = None,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: Option[UserInfo] = None
) extends OmiRequest  with OdfRequest{
  user = user0
 // def this(
 // odf: OdfObjects ,
 // begin: Option[Timestamp ] = None,
 // end: Option[Timestamp ] = None,
 // newest: Option[Int ] = None,
 // oldest: Option[Int ] = None,
 // callback: Option[Callback] = None,
 // ttl: Duration = 10.seconds) = this(odf,begin,end,newest,oldest,callback,ttl,None)
  def withCallback = cb => this.copy(callback = cb)

  implicit def asReadRequest : xmlTypes.ReadRequest = {
    xmlTypes.ReadRequest(
      None,
      Nil,
      Some(
        scalaxb.DataRecord(
          Some("omi.xsd"),
          Some("msg"),
          odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope))
        )
      ),
      callbackAsUri,
      Some("odf"),
      xmlTypes.Node,
      None,
      oldest,
      begin.map{
        timestamp: Timestamp => 
        timestampToXML(timestamp)
      },
      end.map{
        timestamp : Timestamp => 
        timestampToXML(timestamp)
      },
      newest
    )
  }
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttlAsSeconds)
}

/**
 * Poll request
 **/
case class PollRequest(
  callback: Option[Callback] = None,
  requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty,
  ttl: Duration = 10.seconds,
  private val user0: Option[UserInfo] = None
) extends OmiRequest {

  user = user0
  def withCallback = cb => this.copy(callback = cb)
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    requestIDs.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq,
    None,
    callbackAsUri,
    None,
    xmlTypes.Node,
    None
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttlAsSeconds)
}

/**
 * Subscription request for starting subscription
 **/
case class SubscriptionRequest(
  interval: Duration,
  odf: OdfObjects,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: Option[UserInfo] = None
) extends OmiRequest with SubLike with OdfRequest{
  user = user0
  def withCallback = cb => this.copy(callback = cb)

  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callbackAsUri,
    Some("odf"),
    xmlTypes.Node,
    Some(interval.toSeconds)
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttlAsSeconds)
}


/**
 * Write request
 **/
case class WriteRequest(
  odf: OdfObjects,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: Option[UserInfo] = None
) extends OmiRequest with OdfRequest with PermissiveRequest{
  user = user0

  def withCallback = cb => this.copy(callback = cb)

  implicit def asWriteRequest : xmlTypes.WriteRequest = xmlTypes.WriteRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callbackAsUri,
    Some("odf")
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asWriteRequest, ttlAsSeconds)
}
/**
 * Cancel request, for cancelling subscription.
 **/
case class CancelRequest(
  requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty,
  ttl: Duration = 10.seconds,
  private val user0: Option[UserInfo] = None
) extends OmiRequest {
  user = user0
  implicit def asCancelRequest : xmlTypes.CancelRequest = xmlTypes.CancelRequest(
    None,
    requestIDs.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq
  )
  def callback : Option[Callback] = None
  def withCallback = cb => this

  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asCancelRequest, ttlAsSeconds)
}

trait JavaResponseRequest{
  def resultsAsJava(): JIterable[OmiResult]
}

/**
 * Response request, contains result for other requests
 **/
class ResponseRequest(
  val results: OdfTreeCollection[OmiResult],
  val ttl: Duration,
  val callback : Option[Callback] = None,
  private val user0: Option[UserInfo] = None
) extends OmiRequest with OdfRequest with PermissiveRequest with JavaResponseRequest{
  user = user0

  def resultsAsJava(): JIterable[OmiResult] = asJavaIterable(results)
  def copy(
    results: OdfTreeCollection[OmiResult] = this.results,
    ttl: Duration = this.ttl,
    callback: Option[Callback] = this.callback
  ) : ResponseRequest = ResponseRequest( results, ttl)

  def withCallback = cb => this.copy(callback = cb)

  def odf : OdfObjects = results.foldLeft(OdfObjects()){
    _ union _.odf.getOrElse(OdfObjects())
  }

  implicit def asResponseListType : xmlTypes.ResponseListType =
    xmlTypes.ResponseListType(
      results.map{ result =>
        result.asRequestResultType
      }.toVector.toSeq: _*)
   
  def union(another: ResponseRequest): ResponseRequest ={
    ResponseRequest( 
      Results.unionReduce( (results ++ another.results).toVector ).toVector,
      if( ttl >= another.ttl) ttl else another.ttl
    )
  }
  override def equals( other: Any) : Boolean = {
    other match {
      case response: ResponseRequest => 
        response.ttl == ttl && 
        response.callback == callback &&
        response.user == user &&
        response.results.toSet == results.toSet
      case any: Any => any == this
    }
  }
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope = requestToEnvelope(asResponseListType, ttlAsSeconds)
} 

object ResponseRequest{
  def apply(
    results: OdfTreeCollection[OmiResult],
    ttl: Duration = 10.seconds
  ) : ResponseRequest = new ResponseRequest( results, ttl)
}
