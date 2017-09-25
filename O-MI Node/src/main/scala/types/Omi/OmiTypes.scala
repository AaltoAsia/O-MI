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
package omi

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

import akka.actor.ActorRef

import akka.http.scaladsl.model.RemoteAddress
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.{omiDefaultScope, scalaxb, xmlTypes}
import parsing.xmlGen.xmlTypes._
import responses.CallbackHandler
import types.odf._

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
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType

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
  def senderInformation: Option[SenderInformation]
  def withSenderInformation(ni:SenderInformation): OmiRequest
}

object OmiRequestType extends Enumeration{
  type OmiRequestType = String
  val Read = "Read"
  val Write = "Write"
  val ProcedureCall = "ProcedureCall"

}

object SenderInformation{

}
sealed trait SenderInformation 

case class ActorSenderInformation(
  val actorName: String,
  val actorRef: ActorRef
  ) extends SenderInformation{
}
import OmiRequestType._

/**
 * This means request that is writing values
 */
sealed trait PermissiveRequest

/**
 * Request that contains O-DF, (read, write, response)
 */
sealed trait OdfRequest extends OmiRequest{
  def odf : ImmutableODF
  def replaceOdf( nOdf: ImmutableODF ) : OdfRequest
  def odfAsDataRecord = DataRecord(None, Some("Objects"), odf.asXML)
}

sealed trait JavaRequestIDRequest{
  def requestIDsAsJava(): JIterable[RequestID]
}
/**
 * Request that contains requestID(s) (read, cancel) 
 */
sealed trait RequestIDRequest extends JavaRequestIDRequest{
  def requestIDs : Vector[RequestID]
  def requestIDsAsJava : JIterable[RequestID] = asJavaIterable(requestIDs)
}

case class UserInfo(
                   remoteAddress: Option[RemoteAddress] = None,
                   name: Option[String] = None
                   )

sealed trait RequestWrapper {
  //def user(): Option[UserInfo]
  var user: UserInfo = _
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
class RawRequestWrapper(val rawRequest: String, private val user0: UserInfo) extends RequestWrapper {
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
      ttl = OMIParser.parseTTL(head.text.toDouble)
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
  lazy val parsed: OmiParseResult = OMIParser.parse(rawRequest)

  /**
   * Access the request easily and leave responsibility of error handling to someone else.
   * TODO: Only one request per xml message is supported currently
   */
  lazy val unwrapped = parsed match {
    case Right(requestSeq) => Try{ val req = requestSeq.head; req.user = user;req} // change user to correct, user parameter is MUTABLE and might be error prone. TODO change in future versions.
    case Left(errors) => Failure(ParseError.combineErrors(errors))
  }
}

object RawRequestWrapper {
  def apply(rawRequest: String, user: UserInfo): RawRequestWrapper = new RawRequestWrapper(rawRequest, user)

  private def parseError(m: String) = throw new IllegalArgumentException("Pre-parsing: " + m)

  sealed trait MessageType

  object MessageType {
    case object Write extends MessageType
    case object Read extends MessageType
    case object Cancel extends MessageType
    case object Response extends MessageType
    case object Delete extends MessageType
    case object Call extends MessageType
    def apply(xmlTagLabel: String): MessageType =
      xmlTagLabel match {
        case "write"  => Write
        case "read"   => Read
        case "cancel" => Cancel
        case "response" => Response
        case "delete" => Delete
        case "call" => Call
        case _ => parseError("read, write, cancel, call or delete  element not found!")
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
  odf: ImmutableODF ,
  begin: Option[Timestamp ] = None,
  end: Option[Timestamp ] = None,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None

) extends OmiRequest  with OdfRequest{
  user = user0
 // def this(
 // odf: ImmutableODF ,
 // begin: Option[Timestamp ] = None,
 // end: Option[Timestamp ] = None,
 // newest: Option[Int ] = None,
 // oldest: Option[Int ] = None,
 // callback: Option[Callback] = None,
 // ttl: Duration = 10.seconds) = this(odf,begin,end,newest,oldest,callback,ttl,None)
  def withCallback = cb => this.copy(callback = cb)

  implicit def asReadRequest : xmlTypes.ReadRequestType = {
    xmlTypes.ReadRequestType(
      None,
      Nil,
      Some(
          MsgType(
            Seq(
              odfAsDataRecord
              )
            )
      ),
      List(
        callbackAsUri.map(c => "@callback" -> DataRecord(c)),
        Some("@msgformat" -> DataRecord("odf")),
        Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope ))),
        oldest.map(t => "@oldest" -> DataRecord(t)),
        begin.map(t => "@begin" -> DataRecord(timestampToXML(t))),
        end.map(t => "@end" -> DataRecord(timestampToXML(t))),
        newest.map(t => "@newest" -> DataRecord(t))
      ).flatten.toMap
    )
  }
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType = requestToEnvelope(asReadRequest, ttlAsSeconds)
  def replaceOdf( nOdf: ImmutableODF ) = copy(odf = nOdf)

  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
}

/**
 * Poll request
 **/
case class PollRequest(
  callback: Option[Callback] = None,
  requestIDs: Vector[Long ] = Vector.empty,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None
) extends OmiRequest {

  user = user0
  def withCallback = cb => this.copy(callback = cb)
  
  implicit def asReadRequest : xmlTypes.ReadRequestType = xmlTypes.ReadRequestType(
    None,
    requestIDs.map{
      id =>
      id.toString//xmlTypes.IdType(id.toString) //FIXME: id has different types with cacel and read
    }.toSeq,
    None,
    List(
        callbackAsUri.map(c => "@callback" -> DataRecord(c)),
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope )))
    ).flatten.toMap
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType= requestToEnvelope(asReadRequest, ttlAsSeconds)
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
}

/**
 * Subscription request for starting subscription
 **/
case class SubscriptionRequest(
  interval: Duration,
  odf: ImmutableODF,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None
) extends OmiRequest with SubLike with OdfRequest{
  user = user0
  def withCallback = cb => this.copy(callback = cb)

  implicit def asReadRequest : xmlTypes.ReadRequestType = xmlTypes.ReadRequestType(
    None,
    Nil,
    Some(
      MsgType(
        Seq(
          odfAsDataRecord
          )
        )
      ),
    List(
        callbackAsUri.map(c => "@callback" -> DataRecord(c)),
        Some("@msgformat" -> DataRecord("odf")),
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope ))),
        Some("@interval" -> DataRecord(interval.toSeconds.toString()))
      ).flatten.toMap
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType= requestToEnvelope(asReadRequest, ttlAsSeconds)
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
  def replaceOdf( nOdf: ImmutableODF ) = copy(odf = nOdf)
}


/**
 * Write request
 **/
case class WriteRequest(
  odf: ImmutableODF,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None
) extends OmiRequest with OdfRequest with PermissiveRequest{

  user = user0
  def withCallback = cb => this.copy(callback = cb)

  implicit def asWriteRequest : xmlTypes.WriteRequestType = xmlTypes.WriteRequestType(
    None,
    Nil,
      Some(
      MsgType(
        Seq(
          odfAsDataRecord
          )
        )
      ),
    List(
        callbackAsUri.map(c => "@callback" -> DataRecord(c)),
        Some("@msgformat" -> DataRecord("odf")),
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope )))
      ).flatten.toMap
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType = requestToEnvelope(asWriteRequest, ttlAsSeconds)
  def replaceOdf( nOdf: ImmutableODF ) = copy(odf = nOdf)
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
}

case class CallRequest(
  odf: ImmutableODF,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None
) extends OmiRequest with OdfRequest with PermissiveRequest {
  user = user0

  def withCallback = cb => this.copy(callback = cb)

  implicit def asCallRequest : xmlTypes.CallRequestType = xmlTypes.CallRequestType(
    None,
    Nil,
      Some(
      MsgType(
        Seq(
          odfAsDataRecord
          )
        )
      ),
    List(
        callbackAsUri.map(c => "@callback" -> DataRecord(c)),
        Some("@msgformat" -> DataRecord("odf")),
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope )))
      ).flatten.toMap
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType = requestToEnvelope(asCallRequest, ttlAsSeconds)
  def replaceOdf( nOdf: ImmutableODF ) = copy(odf = nOdf)
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
}

case class DeleteRequest(
  odf: ImmutableODF,
  callback: Option[Callback] = None,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None
) extends OmiRequest with OdfRequest with PermissiveRequest{
  user = user0

  def withCallback = cb => this.copy(callback = cb)

  implicit def asDeleteRequest : xmlTypes.DeleteRequestType = xmlTypes.DeleteRequestType(
    None,
    Nil,
    Some(
      MsgType(
        Seq(
          odfAsDataRecord
        )
      )
    ),
  List(
    callbackAsUri.map(c => "@callback" -> DataRecord(c)),
        Some("@msgformat" -> DataRecord("odf")),
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope )))
      ).flatten.toMap
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType = requestToEnvelope(asDeleteRequest, ttlAsSeconds)
  def replaceOdf( nOdf: ImmutableODF ) = copy(odf = nOdf)
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
}
/**
 * Cancel request, for cancelling subscription.
 **/
case class CancelRequest(
  requestIDs: Vector[Long ] = Vector.empty,
  ttl: Duration = 10.seconds,
  private val user0: UserInfo = UserInfo(),
  senderInformation: Option[SenderInformation] = None
) extends OmiRequest {
  user = user0
  implicit def asCancelRequest : xmlTypes.CancelRequestType = xmlTypes.CancelRequestType(
    None,
    requestIDs.map{
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq
  )
  def callback : Option[Callback] = None
  def withCallback = cb => this

  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType = requestToEnvelope(asCancelRequest, ttlAsSeconds)
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))
}

trait JavaResponseRequest{
  def resultsAsJava(): JIterable[OmiResult]
}

/**
 * Response request, contains result for other requests
 **/
class ResponseRequest(
  val results: Vector[OmiResult],
  val ttl: Duration,
  val callback : Option[Callback] = None,
  private val user0: UserInfo = UserInfo(),
  val senderInformation: Option[SenderInformation] = None
) extends OmiRequest  with PermissiveRequest with JavaResponseRequest{
  user = user0

  def resultsAsJava(): JIterable[OmiResult] = asJavaIterable(results)
  def copy(
    results: Vector[OmiResult] = this.results,
    ttl: Duration = this.ttl,
    callback: Option[Callback] = this.callback,
    senderInformation: Option[SenderInformation] = this.senderInformation
  ) : ResponseRequest = ResponseRequest( results, ttl)

  def withCallback = cb => this.copy(callback = cb)

  def odf : Option[ImmutableODF] = results.flatMap{
    case result: OmiResult => result.odf
  }.reduceOption{
    ( l: ImmutableODF,r: ImmutableODF) => l.union(r).immutable 
  }

  implicit def asResponseListType : xmlTypes.ResponseListType =
    xmlTypes.ResponseListType(
      results.map{ result =>
        result.asRequestResultType
      }.toVector.toSeq)
   
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
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelopeType = requestToEnvelope(asResponseListType, ttlAsSeconds)
  
  def withSenderInformation(si:SenderInformation):OmiRequest = this.copy( senderInformation = Some(si))

  def odfResultsToWrites: Seq[WriteRequest] = results.collect{
        case omiResult : OmiResult if omiResult.odf.nonEmpty =>
        val odf = omiResult.odf.get
        WriteRequest( odf, None,ttl)
  }.toVector
  def odfResultsToSingleWrite: WriteRequest ={
    WriteRequest(
      odfResultsToWrites.foldLeft(ImmutableODF()){
        case (objects, write) => objects.union(write.odf).immutable
      },
      None,
      ttl
    )
  }
} 


object ResponseRequest{
  def apply(
    results: Vector[OmiResult],
    ttl: Duration = 10.seconds
  ) : ResponseRequest = new ResponseRequest( results, ttl)
}
