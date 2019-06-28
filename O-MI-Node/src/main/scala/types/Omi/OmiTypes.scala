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
import javax.xml.stream.XMLInputFactory

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.RemoteAddress
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.{omiDefaultScope, xmlTypes}
import types.odf._

import scala.collection.JavaConverters._
import scala.collection.{SeqView, Iterator}
import scala.concurrent.duration._
import scala.concurrent._
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq
import akka.util.ByteString
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import utils._
import parser.OMIStreamParser




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

  abstract class OdfVersion private (n: Double, val msgFormat: String = "odf") extends Version(n, "odf")
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
    case _   => OmiVersion2
  }
  def fromStringNumber: String => OmiVersion = {
    case "2.0" | "2" => OmiVersion2
    case "1.0" | "1" => OmiVersion1
    case _ => OmiVersion2
  }
  def fromNameSpace: String => OmiVersion = {
    case OmiVersion2.namespace => OmiVersion2
    case OmiVersion1.namespace => OmiVersion1
    case OmiVersion1b.namespace => OmiVersion1b
    case _ => OmiVersion2
  }
}
object OdfVersion {
  def fromNumber: Double => OdfVersion = {
    case 2.0 => OdfVersion2
    case 1.0 => OdfVersion1
    case _   => OdfVersion2
  }
  def fromStringNumber: String => OdfVersion = {
    case "2.0" | "2" => OdfVersion2
    case "1.0" | "1" => OdfVersion1
    case _   => OdfVersion2
  }
  def fromNameSpace: String => OdfVersion = {
    case OdfVersion2.namespace => OdfVersion2
    case OdfVersion1.namespace => OdfVersion1
    case OdfVersion1b.namespace => OdfVersion1b
    case _   => OdfVersion2
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

  def rawSource: Source[String,_] = asXMLSource

  def senderInformation: Option[SenderInformation]

  def withSenderInformation(ni: SenderInformation): OmiRequest
  def requestID: Option[Long] 
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] 
  def omiEnvelopeEvents(events: SeqView[ParseEvent,Seq[_]]): SeqView[ParseEvent,Seq[_]] ={
    Vector(
      StartDocument,
      StartElement(
        "omiEnvelope",
        List( Attribute("ttl", ttlAsSeconds.toString), Attribute("version","1.0")),
        namespaceCtx = List(Namespace(s"http://www.opengroup.org/xsd/omi/1.0/",None))
      )
    ).view  ++ events ++ Vector(
      EndElement("omiEnvelope"),
      EndDocument
    )

  } 

  final implicit def asXMLByteSource: Source[ByteString, NotUsed] = parseEventsToByteSource(asXMLEvents)
  
  final implicit def asXMLSource: Source[String, NotUsed] = asXMLByteSource.map[String](_.utf8String)
}

object OmiRequestType extends Enumeration {
  type OmiRequestType = String
  val Read = "Read"
  val Write = "Write"
  val ProcedureCall = "ProcedureCall"

}

object SenderInformatio {

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
  
  def odfAsOmiMsg: SeqView[ParseEvent,Seq[_]] ={
    Vector(StartElement("msg")).view ++ odf.asXMLEvents() ++ Vector(EndElement("msg"))
  }
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] 
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

  def rawSource: Source[String,_]

  def ttl: Duration

  def parsed: OmiParseResult

  def unwrapped: Try[OmiRequest]

  def requestVerb: RawRequestWrapper.MessageType

  def ttlAsSeconds: Double = ttl match {
    case finite: FiniteDuration => finite.toMillis.toDouble/1000.0
    case infinite: Duration.Infinite => -1.0
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
class RawRequestWrapper(val rawSource: Source[String,_], private val user0: UserInfo)(implicit materializer: Materializer) extends RequestWrapper {

    case class WrapperInfo( 
      omiEnvelope: Option[StartElement],
      omiVerb: Option[StartElement],
      odfObjects: Option[StartElement],
      omiResults: Vector[StartElement]
    )
  val wantedEventsFlow: Flow[ParseEvent,StartElement, _] = Flow.apply[ParseEvent].collect{
    case startElement: StartElement if
      startElement.localName == "omiEnvelope" ||
      startElement.localName == "objects" ||
      startElement.localName == "read" ||
      startElement.localName == "write" ||
      startElement.localName == "call" ||
      startElement.localName == "delete" ||
      startElement.localName == "cancel" ||
      startElement.localName == "response" ||
      startElement.localName == "result" 
    => startElement
  }
  val wrapperInfoFlow = Flow.apply[StartElement].fold[WrapperInfo](WrapperInfo(None,None,None, Vector.empty)){
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "omiEnvelope" =>

        wrapperInfo.copy(
          omiEnvelope = Some(startElement)
        )
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "objects" =>
        wrapperInfo.copy(
          odfObjects = Some(startElement)
        )

    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "read" =>
        wrapperInfo.copy(
          omiVerb = Some(startElement)
        )
      case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "write" =>
        wrapperInfo.copy(
          omiVerb = Some(startElement)
        )
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "call" =>
        wrapperInfo.copy(
          omiVerb = Some(startElement)
        )
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "delete" =>
        wrapperInfo.copy(
          omiVerb = Some(startElement)
        )
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "cancel" =>
        wrapperInfo.copy(
          omiVerb = Some(startElement)
        )
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "response" => 
        wrapperInfo.copy(
          omiVerb = Some(startElement)
        )
    case (wrapperInfo: WrapperInfo, startElement: StartElement) if
      startElement.localName == "result" => 
        wrapperInfo.copy(
          omiResults = Vector(startElement) ++ wrapperInfo.omiResults
        )

  }
  val parsedSink : Sink[OmiRequest,Future[OmiRequest]] = Sink.head[OmiRequest] 
  val infoSink : Sink[WrapperInfo,Future[WrapperInfo]] = Sink.head[WrapperInfo]
  val eventSource = rawSource.log("raw source").via(OMIStreamParser.xmlParserFlow.log("xml parser"))
  val rGraph: RunnableGraph[(_,Future[OmiRequest],Future[WrapperInfo])] = RunnableGraph.fromGraph(
    GraphDSL.create(
      eventSource,
      parsedSink,
      infoSink
    )((_,_,_)){ implicit builder =>
      (parseEventSource,parsedRequestSink, wrapperInfoSink) =>
        import GraphDSL.Implicits._
        val bcast = builder.add(Broadcast[ParseEvent](2,false))
        val logF = builder.add(Flow[OmiRequest].log("wrapper parser"))
        parseEventSource ~> bcast.in
        bcast ~> OMIStreamParser.omiParserFlow ~> logF ~> parsedRequestSink.in
        bcast ~> wantedEventsFlow ~> wrapperInfoFlow ~> wrapperInfoSink.in
        ClosedShape
    }
  )
  //XXX: Should first complete infoResult before completing parseResult( parsing
  //whole request)
  val (_,parsedResult,infoResult): (_,Future[OmiRequest],Future[WrapperInfo]) = rGraph.run()
  if( !Await.ready(infoResult,10.seconds).isCompleted ){//XXX: How long to wait? should be pretty fast
    throw OMIParserError("Could not parse wrapper information in 10.seconds")
  }
  infoResult.value match{
    case Some(Failure(throwable)) => throw throwable 
    case Some(Success(_)) =>
    case None => throw OMIParserError("Could not parse wrapper information in 10.seconds")
  }

  val wrapperInfo: WrapperInfo = Await.result(infoResult,10.seconds)//XXX: Should be ready already. How long to wait? should be pretty fast

  


  import RawRequestWrapper._

  //import scala.xml.pull._ // deprecated

  user = user0


  // NOTE: Order is important
  val omiEnvelope: StartElement = wrapperInfo.omiEnvelope.getOrElse(throw OMIParserError("omiEnvelope not found."))
  val omiVerb: StartElement = wrapperInfo.omiVerb.getOrElse(throw OMIParserError("No O-MI read,write,call,delete,cancel or response found."))

  /**
    * The msgformat attribute of O-MI as in the verb element or the first result element
    */
  val msgFormat: Option[String] = omiVerb.attributes.get("msgformat") orElse wrapperInfo.omiResults.flatMap(_.attributes.get("msgformat")).headOption // verb or result

  val odfObjects: Option[StartElement] = wrapperInfo.odfObjects



  require(omiEnvelope.localName == "omiEnvelope", "Pre-parsing: omiEnvelope not found!")

  val ttl: Duration =
    omiEnvelope.attributes.get("ttl")
      .map { (ttlStr) => parser.parseTTL(ttlStr.toDouble) }
      .getOrElse(parseError("couldn't parse ttl"))

  /**
    * The verb of the O-MI message (read, write, cancel, response)
    */
  val requestVerb: MessageType = MessageType(omiVerb.localName)

  /**
    * Gets the verb of the O-MI message
    */
  val callback: Option[Callback] =
    omiVerb.attributes.get("callback").map(RawCallback.apply)

  /**
    * Get the parsed request. Message is parsed only once because of laziness.
    */
  //XXX: Blocking. Waiting ttl that is given by user.
  lazy val parsed: OmiParseResult = Await.ready(parsedResult,ttl).value.map{ 
    case Failure(pe: ParseError) => Left(Vector(pe))
    case Failure(other)=> throw other
    case Success(req: OmiRequest) => Right(Vector(req))
  }.getOrElse{
    throw OMIParserError(s"Parsing whole request takes longer than given $ttl")
  }

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
  def apply(rawSource: Source[String,_], user: UserInfo)(implicit materializer: Materializer): RawRequestWrapper = new RawRequestWrapper(rawSource, user)

  private def parseError(m: String) = throw new IllegalArgumentException("Pre-parsing: " + m)

  val xmlFactory = XMLInputFactory.newInstance()

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
import database.journal.PRequestInfo

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
                        requestID: Option[Long] = None
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

  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events: SeqView[ParseEvent,Seq[_]] = Vector(
      StartElement("read",
        List(
          Attribute("msgformat","odf"),
          Attribute("targetType","node")
        ) ++ newest.map{
          n => Attribute("newest",n.toString)
        }.toList ++ oldest.map{
          n => Attribute("oldest",n.toString)
        }.toList ++ begin.map{
          timestamp => Attribute("begin",timestampToDateTimeString(timestamp))
        }.toList ++ end.map{
          timestamp => Attribute("end",timestampToDateTimeString(timestamp))
        }.toList ++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestID.map{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.toSeq.flatten ++ odfAsOmiMsg ++ Vector(EndElement("read")).view
    omiEnvelopeEvents(events)
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
                        requestID: Option[Long] = None
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
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("read",
        List(
          Attribute("msgformat","odf"),
          Attribute("targetType","node")
        ) ++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestID.map{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.toSeq.flatten.view ++ Vector(EndElement("read"))
    omiEnvelopeEvents(events)
  }
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
                                requestID: Option[Long] = None
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
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("read",
        List(
          Attribute("msgformat","odf"),
          Attribute("targetType","node"),
          Attribute("interval",interval.toSeconds.toString)
        ) ++ newest.map{
          n => Attribute("newest",n.toString)
        }.toList ++ oldest.map{
          n => Attribute("oldest",n.toString)
        }.toList ++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestID.map{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.toSeq.flatten.view ++ odfAsOmiMsg ++ Vector(EndElement("read"))
    omiEnvelopeEvents(events)
  }
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
                         requestID: Option[Long] = None
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
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("write",
        List(
          Attribute("msgformat","odf"),
          Attribute("targetType","node")
        )++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestID.map{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.toSeq.flatten.view ++ odfAsOmiMsg ++ Vector(EndElement("write"))
    omiEnvelopeEvents(events)
  }
}

case class CallRequest(
                        odf: ODF,
                        callback: Option[Callback] = None,
                        ttl: Duration = 10.seconds,
                        private val user0: UserInfo = UserInfo(),
                        senderInformation: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        requestID: Option[Long] = None
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
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("call",
        List(
          Attribute("msgformat","odf"),
          Attribute("targetType","node")
        )++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestID.map{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.toSeq.flatten.view ++ odfAsOmiMsg ++ Vector(EndElement("call"))
    omiEnvelopeEvents(events)
  }
}

case class DeleteRequest(
                          odf: ODF,
                          callback: Option[Callback] = None,
                          ttl: Duration = 10.seconds,
                          private val user0: UserInfo = UserInfo(),
                          senderInformation: Option[SenderInformation] = None,
                          ttlLimit: Option[Timestamp] = None,
                          requestID: Option[Long] = None
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
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("delete",
        List(
          Attribute("msgformat","odf"),
          Attribute("targetType","node")
        )++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestID.map{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.toSeq.flatten.view ++ odfAsOmiMsg ++ Vector(EndElement("delete"))
    omiEnvelopeEvents(events)
  }
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
                          requestID: Option[Long] = None
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
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("cancel")
    ).view ++ requestIDs.flatMap{
      id => 
        Vector(
          StartElement("requestID"),
          Characters(id.toString),
          EndElement("requestID")
        )
    }.view ++ Vector(EndElement("cancel"))
    omiEnvelopeEvents(events)
  }
}

trait JavaResponseRequest {
  def resultsAsJava(): JIterable[OmiResult]
}

/**
  * Response request, contains result for other requests
  **/
case class ResponseRequest(
                       val results: OdfCollection[OmiResult],
                       val ttl: Duration = 10 seconds,
                       val callback: Option[Callback] = None,
                       private val user0: UserInfo = UserInfo(),
                       val senderInformation: Option[SenderInformation] = None,
                       val ttlLimit: Option[Timestamp] = None,
                       val requestID: Option[Long] = None,
                       val renderRequestID: Boolean = false
                     ) extends OmiRequest with PermissiveRequest with JavaResponseRequest {
  user = user0

  def resultsAsJava(): JIterable[OmiResult] = asJavaIterable(results)

  def copy(
            results: OdfCollection[OmiResult] = this.results,
            ttl: Duration = this.ttl,
            callback: Option[Callback] = this.callback,
            senderInformation: Option[SenderInformation] = this.senderInformation,
            ttlLimit: Option[Timestamp] = this.ttlLimit,
            requestID: Option[Long] = this.requestID,
            renderRequestID: Boolean = false,
          ): ResponseRequest = ResponseRequest(results, ttl)

  def withCallback: Option[Callback] => ResponseRequest = cb => this.copy(callback = cb)
  def withRequestID: Option[Long] => ResponseRequest = id => this.copy(requestID = id )

  def odf: ODF = results.foldLeft(ImmutableODF()) {
    case (l: ODF, r: OmiResult) =>
      l.union(r.odf.getOrElse(ImmutableODF())).toImmutable
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
        case (objects, write) => objects.union(write.odf).toImmutable
      },
      None,
      ttl
    )
  }

  val requestVerb: MessageType.Response.type = MessageType.Response

  def asXMLEvents = asXMLEventsWithVersion()

  def asXMLByteSourceWithVersion(info: PRequestInfo): Source[ByteString, NotUsed] =
    asXMLByteSourceWithVersion(OmiVersion.fromNumber(info.omiVersion), info.odfVersion.map(OdfVersion.fromNumber(_))) // msgformat?

  def asXMLByteSourceWithVersion(omiVersion: OmiVersion = OmiVersion1, odfVersion: Option[OdfVersion] = None): Source[ByteString, NotUsed] =
    parseEventsToByteSource(asXMLEventsWithVersion(omiVersion, odfVersion))

  def asXMLSourceWithVersion(info: PRequestInfo): Source[String, NotUsed] =
    asXMLSourceWithVersion(OmiVersion.fromNumber(info.omiVersion), info.odfVersion.map(OdfVersion.fromNumber(_))) // msgformat?

  def asXMLSourceWithVersion(omiVersion: OmiVersion = OmiVersion1, odfVersion: Option[OdfVersion] = None): Source[String, NotUsed] =
    asXMLByteSourceWithVersion(omiVersion, odfVersion).map[String](_.utf8String)

  def asXMLEventsWithVersion(info: PRequestInfo): SeqView[ParseEvent,Seq[_]] =
    asXMLEventsWithVersion(OmiVersion.fromNumber(info.omiVersion), info.odfVersion.map(OdfVersion.fromNumber(_))) // msgformat?

  def asXMLEventsWithVersion(omiVersion: OmiVersion = OmiVersion1, odfVersion: Option[OdfVersion] = None): SeqView[ParseEvent,Seq[_]] ={
    Vector(
      StartDocument,
      StartElement("omiEnvelope",
        List(
          Attribute("ttl", ttlAsSeconds.toString),//.toString.replaceAll(".0$","")),
          Attribute("version", omiVersion.number.toString)
        ),
        namespaceCtx = List(Namespace(omiVersion.namespace,None))
      ),
      StartElement("response")
    ).view ++
    {if (renderRequestID) requestID.view.flatMap{
      rid =>
        Vector(
          StartElement("requestID"),
          Characters(rid.toString),
          EndElement("requestID")
        )
    }
    else Seq.empty.view} ++
    results.view.flatMap{
      result => result.asXMLEvents(odfVersion)
    } ++ Vector(
      EndElement("response"),
      EndElement("omiEnvelope"),
      EndDocument
    )
  }
}


object ResponseRequest {
  def applySimple(results: OdfCollection[OmiResult], ttl: Duration) = ResponseRequest(results,ttl)
}

