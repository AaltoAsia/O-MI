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

import java.lang.{Iterable => JIterable}
import java.net.URI
import java.sql.Timestamp

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.RemoteAddress
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.{omiDefaultScope, xmlTypes}
import types.odf._

import scala.collection.JavaConverters._
import scala.collection.SeqView
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.{NamespaceBinding, NodeSeq}
import akka.util.ByteString
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import utils._




abstract class Version private (val number: Double, val standard: String) {
  val namespace: String = f"http://www.opengroup.org/xsd/$standard/$number%.1f/"
}
object Version {
  abstract class OmiVersion private (n: Double) extends Version(n, "omi")
  object OmiVersion {
    case object OmiVersion1  extends OmiVersion(1.0)
    case object OmiVersion1b extends OmiVersion(1.0){ override val namespace = "omi.xsd" }
    case object OmiVersion2  extends OmiVersion(2.0)
  }

  abstract class OdfVersion private (n: Double) extends Version(n, "odf")
  object OdfVersion {
    case object OdfVersion1  extends OdfVersion(1.0)
    case object OdfVersion1b extends OdfVersion(1.0){ override val namespace = "odf.xsd" }
    case object OdfVersion2  extends OdfVersion(2.0)
  }
}
import Version._
import Version.OmiVersion._
import Version.OdfVersion._

object OmiVersion {
  def fromNumber: Double => OmiVersion = {
    case 2.0 => OmiVersion2
    case 1.0 => OmiVersion1
  }
  def fromStringNumber: String => OmiVersion = {
    case "2.0" | "2" => OmiVersion2
    case "1.0" | "1" => OmiVersion1
  }
  def fromNameSpace: String => OmiVersion = {
    case OmiVersion2.namespace => OmiVersion2
    case OmiVersion1.namespace => OmiVersion1
    case OmiVersion1b.namespace => OmiVersion1b
  }
}
object OdfVersion {
  def fromNumber: Double => OdfVersion = {
    case 2.0 => OdfVersion2
    case 1.0 => OdfVersion1
  }
  def fromStringNumber: String => OdfVersion = {
    case "2.0" | "2" => OdfVersion2
    case "1.0" | "1" => OdfVersion1
  }
  def fromNameSpace: String => OdfVersion = {
    case OdfVersion2.namespace => OdfVersion2
    case OdfVersion1.namespace => OdfVersion1
    case OdfVersion1b.namespace => OdfVersion1b
  }
}






trait JavaOmiRequest {
  def callbackAsJava(): JIterable[Callback]
}

/**
  * Trait that represents any Omi request. Provides some data that are common
  * for all omi requests.
  */
sealed trait OmiRequest extends RequestWrapper with JavaOmiRequest {
  def callbackAsJava(): JIterable[Callback] = asJavaIterable(callback)

  def callback: Option[Callback]

  def callbackAsUri: Option[URI] = callback map { cb => new URI(cb.address) }

  def withCallback: Option[Callback] => OmiRequest
  def withRequestID: Option[Long] => OmiRequest

  def hasCallback: Boolean = callback.nonEmpty

  //def user(): Option[UserInfo]
  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType

  implicit def asXML: NodeSeq = {
    //val timer = LapTimer(println)
    val envelope = asOmiEnvelope
    //timer.step("OmiRequest asXML: asEnvelope")
    val t = omiEnvelopeToXML(envelope)
    //timer.step("OmiRequest asXML: EnvelopeToXML")
    //timer.total()
    t
  }

  def parsed: OmiParseResult = Right(Iterable(this))

  def unwrapped: Try[OmiRequest] = Success(this)

  def rawRequest: String = asXML.toString

  def senderInformation: Option[SenderInformation]

  def withSenderInformation(ni: SenderInformation): OmiRequest
  def requestID: Option[Long] 
}

object OmiRequestType extends Enumeration {
  type OmiRequestType = String
  val Read = "Read"
  val Write = "Write"
  val ProcedureCall = "ProcedureCall"

}

object SenderInformation {

}

sealed trait SenderInformation

case class ActorSenderInformation(
                                   actorName: String,
                                   actorRef: ActorRef
                                 ) extends SenderInformation {
}

/**
  * This means request that is writing values
  */
sealed trait PermissiveRequest

/**
  * Request that contains O-DF, (read, write, response)
  */
sealed trait OdfRequest extends OmiRequest {
  def odf: ODF

  def replaceOdf(nOdf: ODF): OdfRequest

  def odfAsDataRecord: DataRecord[NodeSeq] = DataRecord(None, Some("Objects"), odf.asXML)
}

sealed trait JavaRequestIDRequest {
  def requestIDsAsJava(): JIterable[RequestID]
}

/**
  * Request that contains requestID(s) (read, cancel)
  */
sealed trait RequestIDRequest extends JavaRequestIDRequest {
  def requestIDs: OdfCollection[RequestID]

  def requestIDsAsJava(): JIterable[RequestID] = asJavaIterable(requestIDs)
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

  def requestVerb: RawRequestWrapper.MessageType

  def ttlAsSeconds: Long = ttl match {
    case finite: FiniteDuration => finite.toSeconds
    case infinite: Duration.Infinite => -1
  }

  final def handleTTL: FiniteDuration = if (ttl.isFinite) {
    if (ttl.toSeconds != 0)
      FiniteDuration(ttl.toSeconds, SECONDS)
    else
      FiniteDuration(2, MINUTES)
  } else {
    FiniteDuration(Int.MaxValue, MILLISECONDS)
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


  class Element(private val ev: EvElemStart) {
    val pre: String = ev.pre
    val label: String = ev.label
    val scope: NamespaceBinding = ev.scope

    def attr(key: String): Option[String] = for {
      nodeSeqAttr <- ev.attrs.get(key)
      head <- nodeSeqAttr.headOption
    } yield head.text

  }

  private val (parseSingle, closeParser): (() => EvElemStart, () => Unit) = {
    val src = io.Source.fromString(rawRequest)
    val er = new XMLEventReader(src)

    ({ () =>
      // skip to the interesting parts
      er.collectFirst {
        case e: EvElemStart => e
      } getOrElse parseError("no xml elements found")
    }, () => er.stop)
  }
  // NOTE: Order is important
  val omiEnvelope: Element = new Element(parseSingle())
  val omiVerb: Element = new Element(parseSingle())

  val odfObjects: Element = {
    def findObjects(n: Int): Element = {
      if (n >= 8) parseError("Objects element not found in 10 first tags")
      else
        new Element(parseSingle()) match {
          case e if e.label == "Objects" => e
          case _ => findObjects(n+1)
        }
    }
    findObjects(0)
  }
  closeParser() // <- Important! otherwise leaks memory



  require(omiEnvelope.label == "omiEnvelope", "Pre-parsing: omiEnvelope not found!")

  val ttl: Duration =
    omiEnvelope.attr("ttl")
      .map { (ttlStr) => parsing.OmiParser.parseTTL(ttlStr.toDouble) }
      .getOrElse(parseError("couldn't parse ttl"))

  /**
    * The verb of the O-MI message (read, write, cancel, response)
    */
  val requestVerb: MessageType = MessageType(omiVerb.label)

  /**
    * The msgformat attribute of O-MI as in the verb element
    */
  val msgFormat: Option[String] = omiVerb.attr("msgformat")

  /**
    * Gets the verb of the O-MI message
    */
  val callback: Option[Callback] =
    omiEnvelope.attr("callback").map(RawCallback.apply)

  /**
    * Get the parsed request. Message is parsed only once because of laziness.
    */
  lazy val parsed: OmiParseResult = parsing.OmiParser.parse(rawRequest)

  /**
    * Access the request easily and leave responsibility of error handling to someone else.
    * TODO: Only one request per xml message is supported currently
    */
  lazy val unwrapped: Try[OmiRequest] = parsed match {
    case Right(requestSeq) => Try {
      val req = requestSeq.head; req.user = user; req
    } // change user to correct, user parameter is MUTABLE and might be error prone. TODO change in future versions.
    case Left(errors) => Failure(ParseError.combineErrors(errors))
  }
}

object RawRequestWrapper {
  def apply(rawRequest: String, user: UserInfo): RawRequestWrapper = new RawRequestWrapper(rawRequest, user)

  private def parseError(m: String) = throw new IllegalArgumentException("Pre-parsing: " + m)

  sealed class MessageType(val name: String)

  object MessageType {

    case object Write extends MessageType("write")

    case object Read extends MessageType("read")

    case object Cancel extends MessageType("cancel")

    case object Response extends MessageType("response")

    case object Delete extends MessageType("delete")

    case object Call extends MessageType("call")

    def apply(xmlTagLabel: String): MessageType =
      xmlTagLabel match {
        case Write.name => Write
        case Read.name => Read
        case Cancel.name => Cancel
        case Response.name => Response
        case Delete.name => Delete
        case Call.name => Call
        case _ => parseError("read, write, cancel, response, call or delete element not found!")
      }
  }

}

import types.OmiTypes.RawRequestWrapper.MessageType

/**
  * One-time-read request
  **/
case class ReadRequest(
                        odf: ODF,
                        begin: Option[Timestamp] = None,
                        end: Option[Timestamp] = None,
                        newest: Option[Int] = None,
                        oldest: Option[Int] = None,
                        callback: Option[Callback] = None,
                        ttl: Duration = 10.seconds,
                        private val user0: UserInfo = UserInfo(),
                        senderInformation: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        val requestID: Option[Long] = None
                      ) extends OmiRequest with OdfRequest {
  user = user0

  // def this(
  // odf: ODF ,
  // begin: Option[Timestamp ] = None,
  // end: Option[Timestamp ] = None,
  // newest: Option[Int ] = None,
  // oldest: Option[Int ] = None,
  // callback: Option[Callback] = None,
  // ttl: Duration = 10.seconds) = this(odf,begin,end,newest,oldest,callback,ttl,None)
  def withCallback: Option[Callback] => ReadRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => ReadRequest = id => this.copy(requestID = id )

  implicit def asReadRequest: xmlTypes.ReadRequestType = {
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
        Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope))),
        oldest.map(t => "@oldest" -> DataRecord(BigInt(t))),
        begin.map(t => "@begin" -> DataRecord(timestampToXML(t))),
        end.map(t => "@end" -> DataRecord(timestampToXML(t))),
        newest.map(t => "@newest" -> DataRecord(BigInt(t)))
      ).flatten.toMap
    )
  }

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asReadRequest, ttlAsSeconds)

  def replaceOdf(nOdf: ODF): ReadRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Read.type = MessageType.Read
}

/**
  * Poll request
  **/
case class PollRequest(
                        callback: Option[Callback] = None,
                        requestIDs: OdfCollection[Long] = OdfCollection.empty,
                        ttl: Duration = 10.seconds,
                        private val user0: UserInfo = UserInfo(),
                        senderInformation: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        val requestID: Option[Long] = None
                      ) extends OmiRequest {

  user = user0

  def withCallback: Option[Callback] => PollRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => PollRequest = id => this.copy(requestID = id )

  implicit def asReadRequest: xmlTypes.ReadRequestType = xmlTypes.ReadRequestType(
    None,
    requestIDs.map {
      id =>
        id.toString //xmlTypes.IdType(id.toString) //FIXME: id has different types with cancel and read
    },
    None,
    List(
      callbackAsUri.map(c => "@callback" -> DataRecord(c)),
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope)))
    ).flatten.toMap
  )

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asReadRequest, ttlAsSeconds)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Read.type = MessageType.Read
}

/**
  * Subscription request for starting subscription
  **/
case class SubscriptionRequest(
                                interval: Duration,
                                odf: ODF,
                                newest: Option[Int] = None,
                                oldest: Option[Int] = None,
                                callback: Option[Callback] = None,
                                ttl: Duration = 10.seconds,
                                private val user0: UserInfo = UserInfo(),
                                senderInformation: Option[SenderInformation] = None,
                                ttlLimit: Option[Timestamp] = None,
                                val requestID: Option[Long] = None
                              ) extends OmiRequest  with OdfRequest {
  require(interval == -1.seconds || interval == -2.seconds || interval >= 0.seconds, s"Invalid interval: $interval")
  require(ttl >= 0.seconds, s"Invalid ttl, should be positive (or +infinite): $ttl")
  user = user0

  def withCallback: Option[Callback] => SubscriptionRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => SubscriptionRequest = id => this.copy(requestID = id )

  implicit def asReadRequest: xmlTypes.ReadRequestType = xmlTypes.ReadRequestType(
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
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope))),
      Some("@interval" -> DataRecord(interval.toSeconds.toString))
    ).flatten.toMap
  )

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asReadRequest, ttlAsSeconds)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  def replaceOdf(nOdf: ODF): SubscriptionRequest = copy(odf = nOdf)

  val requestVerb: MessageType.Read.type = MessageType.Read
}


/**
  * Write request
  **/
case class WriteRequest(
                         odf: ODF,
                         callback: Option[Callback] = None,
                         ttl: Duration = 10.seconds,
                         private val user0: UserInfo = UserInfo(),
                         senderInformation: Option[SenderInformation] = None,
                         ttlLimit: Option[Timestamp] = None,
                         val requestID: Option[Long] = None
                       ) extends OmiRequest with OdfRequest with PermissiveRequest {

  user = user0

  def withCallback: Option[Callback] => WriteRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => WriteRequest = id => this.copy(requestID = id )

  implicit def asWriteRequest: xmlTypes.WriteRequestType = xmlTypes.WriteRequestType(
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
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope)))
    ).flatten.toMap
  )

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asWriteRequest, ttlAsSeconds)

  def replaceOdf(nOdf: ODF): WriteRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb = MessageType.Write
}

case class CallRequest(
                        odf: ODF,
                        callback: Option[Callback] = None,
                        ttl: Duration = 10.seconds,
                        private val user0: UserInfo = UserInfo(),
                        senderInformation: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        val requestID: Option[Long] = None
                      ) extends OmiRequest with OdfRequest with PermissiveRequest {
  user = user0

  def withCallback: Option[Callback] => CallRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => CallRequest = id => this.copy(requestID = id )

  implicit def asCallRequest: xmlTypes.CallRequestType = xmlTypes.CallRequestType(
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
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope)))
    ).flatten.toMap
  )

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asCallRequest, ttlAsSeconds)

  def replaceOdf(nOdf: ODF): CallRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Call.type = MessageType.Call
}

case class DeleteRequest(
                          odf: ODF,
                          callback: Option[Callback] = None,
                          ttl: Duration = 10.seconds,
                          private val user0: UserInfo = UserInfo(),
                          senderInformation: Option[SenderInformation] = None,
                          ttlLimit: Option[Timestamp] = None,
                          val requestID: Option[Long] = None
                        ) extends OmiRequest with OdfRequest with PermissiveRequest {
  user = user0

  def withCallback: Option[Callback] => DeleteRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => DeleteRequest = id => this.copy(requestID = id )

  implicit def asDeleteRequest: xmlTypes.DeleteRequestType = xmlTypes.DeleteRequestType(
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
      Some("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope)))
    ).flatten.toMap
  )

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asDeleteRequest, ttlAsSeconds)

  def replaceOdf(nOdf: ODF): DeleteRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Delete.type = MessageType.Delete
}

/**
  * Cancel request, for cancelling subscription.
  **/
case class CancelRequest(
                          requestIDs: OdfCollection[Long] = OdfCollection.empty,
                          ttl: Duration = 10.seconds,
                          private val user0: UserInfo = UserInfo(),
                          senderInformation: Option[SenderInformation] = None,
                          ttlLimit: Option[Timestamp] = None,
                          val requestID: Option[Long] = None
                        ) extends OmiRequest {
  user = user0

  implicit def asCancelRequest: xmlTypes.CancelRequestType = xmlTypes.CancelRequestType(
    None,
    requestIDs.map {
      id =>
        xmlTypes.IdType(id.toString)
    }
  )

  def callback: Option[Callback] = None

  def withCallback: Option[Callback] => CancelRequest = cb => this
  def withRequestID: Option[Long] => CancelRequest = id => this.copy(requestID = id )

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asCancelRequest, ttlAsSeconds)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Cancel.type = MessageType.Cancel
}

trait JavaResponseRequest {
  def resultsAsJava(): JIterable[OmiResult]
}

/**
  * Response request, contains result for other requests
  **/
class ResponseRequest(
                       val results: OdfCollection[OmiResult],
                       val ttl: Duration,
                       val callback: Option[Callback] = None,
                       private val user0: UserInfo = UserInfo(),
                       val senderInformation: Option[SenderInformation] = None,
                       val ttlLimit: Option[Timestamp] = None,
                       val requestID: Option[Long] = None
                     ) extends OmiRequest with PermissiveRequest with JavaResponseRequest {
  user = user0

  def resultsAsJava(): JIterable[OmiResult] = asJavaIterable(results)

  def copy(
            results: OdfCollection[OmiResult] = this.results,
            ttl: Duration = this.ttl,
            callback: Option[Callback] = this.callback,
            senderInformation: Option[SenderInformation] = this.senderInformation,
            ttlLimit: Option[Timestamp] = this.ttlLimit,
            requestID: Option[Long] = this.requestID
          ): ResponseRequest = ResponseRequest(results, ttl)

  def withCallback: Option[Callback] => ResponseRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => OmiRequest = id => this.copy(requestID = id )

  def odf: ODF = results.foldLeft(ImmutableODF()) {
    case (l: ODF, r: OmiResult) =>
      l.union(r.odf.getOrElse(ImmutableODF())).immutable
  }

  implicit def asResponseListType: xmlTypes.ResponseListType =
    xmlTypes.ResponseListType(
                               results.map { result =>
                                 result.asRequestResultType
                               })

  def union(another: ResponseRequest): ResponseRequest = {
    ResponseRequest(
                     Results.unionReduce((results ++ another.results)),
      if (ttl >= another.ttl) ttl else another.ttl
    )
  }

  override def equals(other: Any): Boolean = {
    other match {
      case response: ResponseRequest =>
        response.ttl == ttl &&
          response.callback == callback &&
          response.user == user &&
          response.results.toSet == results.toSet
      case any: Any => any == this
    }
  }

  implicit def asOmiEnvelope: xmlTypes.OmiEnvelopeType = requestToEnvelope(asResponseListType, ttlAsSeconds)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  def odfResultsToWrites: Seq[WriteRequest] = results.collect {
    case omiResult: OmiResult if omiResult.odf.nonEmpty =>
      val odf = omiResult.odf.get
      WriteRequest(odf, None, ttl)
  }

  def odfResultsToSingleWrite: WriteRequest = {
    WriteRequest(
      odfResultsToWrites.foldLeft(ImmutableODF()) {
        case (objects, write) => objects.union(write.odf).immutable
      },
      None,
      ttl
    )
  }

  val requestVerb: MessageType.Response.type = MessageType.Response
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    Vector(
      StartDocument,
      StartElement("omiEnvelope",
        List(
          Attribute("ttl", ttlAsSeconds.toString),//.toString.replaceAll(".0$","")),
          Attribute("version", "1.0")
        ),
        namespaceCtx = List(Namespace("http://www.opengroup.org/xsd/omi/1.0/",None))
      ),
      StartElement("response")
    ).view ++ requestID.view.flatMap{
      rid =>
        Vector(
          StartElement("requestID"),
          Characters(rid.toString),
          EndElement("requestID")
        )
    } ++ results.view.flatMap{
      result => result.asXMLEvents
    } ++ Vector(
      EndElement("response"),
      EndElement("omiEnvelope"),
      EndDocument
    )
  }

  final implicit def asXMLByteSource: Source[ByteString, NotUsed] = parseEventsToByteSource(asXMLEvents)
  
  final implicit def asXMLSource: Source[String, NotUsed] = asXMLByteSource.map[String](_.utf8String)

}


object ResponseRequest {
  def apply(
             results: OdfCollection[OmiResult],
             ttl: Duration = 10.seconds
           ): ResponseRequest = new ResponseRequest(results, ttl)
}
