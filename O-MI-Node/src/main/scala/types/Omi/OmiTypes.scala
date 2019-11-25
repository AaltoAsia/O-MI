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

import java.lang.{Iterable => JIterable}
import java.net.URI
import java.sql.Timestamp
import javax.xml.stream.XMLInputFactory

import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.RemoteAddress

import scala.collection.JavaConverters._
import scala.collection.SeqView
import scala.concurrent.duration._
import scala.concurrent._
import scala.util.{Failure, Success, Try}
import akka.util.ByteString
import akka.stream.{Materializer, ClosedShape}
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import types.odf._
import utils._
import parsing.OMIStreamParser
import database.journal.PRequestInfo
import database.SingleStores

abstract class Version private (val number: Double, val standard: String) {
  val numberString = "%.2f".formatLocal(java.util.Locale.US, number)
  val namespace: String = f"http://www.opengroup.org/xsd/$standard/$numberString/"
}

object Version {
  abstract class OmiVersion private[omi] (n: Double) extends Version(n, "omi")
  case object OmiVersion1  extends OmiVersion(1.0)
  case object OmiVersion1b extends OmiVersion(1.0){ override val namespace = "omi.xsd" }
  case object OmiVersion2  extends OmiVersion(2.0)
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

  abstract class OdfVersion private[omi] (n: Double, val msgFormat: String = "odf") extends Version(n, "odf")
  case object OdfVersion1  extends OdfVersion(1.0)
  case object OdfVersion1b extends OdfVersion(1.0){ override val namespace = "odf.xsd" }
  case object OdfVersion2  extends OdfVersion(2.0)
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
}
import Version._

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
  def withRequestToken: Option[Long] => OmiRequest

  def hasCallback: Boolean = callback.nonEmpty

  //def user(): Option[UserInfo]


  def parsed: OmiParseResult = Right(Iterable(this))

  def unwrapped: Try[OmiRequest] = Success(this)

  def rawSource: Source[String,_] = asXMLSource

  def senderInformation: Option[SenderInformation]

  def withSenderInformation(ni: SenderInformation): OmiRequest
  def requestToken: Option[Long] 
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
  def requestTypeString: String 

  final implicit def asXMLByteSource: Source[ByteString, NotUsed] = parseEventsToByteSource(asXMLEvents)
  
  final implicit def asXMLSource: Source[String, NotUsed] = asXMLByteSource.map[String](_.utf8String)
}

object OmiRequestType extends Enumeration {
  type OmiRequestType = String
  val Read = "Read"
  val Write = "Write"
  val ProcedureCall = "ProcedureCall"

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
  def attributeStr: String = ""

  final def handleTTL: FiniteDuration = if (ttl.isFinite) {
    if (ttl.toSeconds != 0)
      FiniteDuration(ttl.toSeconds, SECONDS)
    else
      FiniteDuration(10, MINUTES)
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
  val wantedEventsFlow = Flow.apply[ParseEvent].takeWhile{
    pe: ParseEvent => 
      pe match {
        case startElement: StartElement if
          startElement.localName == "omiEnvelope" ||
          startElement.localName == "read" ||
          startElement.localName == "write" ||
          startElement.localName == "call" ||
          startElement.localName == "delete" ||
          startElement.localName == "cancel" ||
          startElement.localName == "response" ||
          startElement.localName == "result" ||
          startElement.localName == "requestID" || 
          startElement.localName == "return" ||
          startElement.localName == "msg" ||
          startElement.localName == "Objects" 
        => true
        case endElement: EndElement if
          endElement.localName == "requestID" ||  
          endElement.localName == "return" ||
          endElement.localName == "result" 
        => true
        case text: TextEvent => true
        case text: Comment => true
        case StartDocument => true
        case other: ParseEvent => 
          false
      }
  }.collect{
        case startElement: StartElement if
        startElement.localName == "omiEnvelope" ||
        startElement.localName == "Objects" ||
        startElement.localName == "read" ||
        startElement.localName == "write" ||
        startElement.localName == "call" ||
        startElement.localName == "delete" ||
        startElement.localName == "cancel" ||
        startElement.localName == "response" ||
        startElement.localName == "result" 
        => startElement
  }
  val infoSink = Sink.fold[WrapperInfo,StartElement](WrapperInfo(None,None,None, Vector.empty)){
    case (wrapperInfo: WrapperInfo, startElement: StartElement) =>
      startElement.localName match{
        case "omiEnvelope" =>
          wrapperInfo.copy(
            omiEnvelope = Some(startElement)
          )
        case "Objects" =>
          wrapperInfo.copy(
            odfObjects = Some(startElement)
          )
        case "read" =>
          wrapperInfo.copy(
            omiVerb = Some(startElement)
          )
        case "write" =>
          wrapperInfo.copy(
            omiVerb = Some(startElement)
          )
        case "call" =>
          wrapperInfo.copy(
            omiVerb = Some(startElement)
          )
        case "delete" =>
          wrapperInfo.copy(
            omiVerb = Some(startElement)
          )
        case "cancel" =>
          wrapperInfo.copy(
            omiVerb = Some(startElement)
          )
        case "response" => 
          wrapperInfo.copy(
            omiVerb = Some(startElement)
          )
        case  "result" => 
          wrapperInfo.copy(
            omiResults = Vector(startElement) ++ wrapperInfo.omiResults
          )
        case str: String => 
          wrapperInfo
      }

  }
  val eventSource = rawSource.via(OMIStreamParser.xmlParserFlow)
  val parsedSink : Sink[OmiRequest,Future[OmiRequest]] = Sink.head[OmiRequest] 

  //val broadcast = eventSource.map{ pe => println("broadcast: " + pe.toString); pe }.delay(0.seconds).runWith(
  //val broadcast = eventSource.delay(0.seconds).runWith(
  //  BroadcastHub.sink[ParseEvent](bufferSize=16))
  // FIXME: When akka/akka pull request is merged and released: https://github.com/akka/akka/pull/27206
  //  BroadcastHub.sink[ParseEvent](startAfterNrOfConsumers=2, bufferSize=16))


  //val infoResult = broadcast.map{ pe => println("info: " + pe.toString); pe }.via(wantedEventsFlow).runWith(infoSink)
  //val parsedResult = broadcast.map{ pe => println("parse: " + pe.toString); pe }.via(OMIStreamParser.omiParserFlow).runWith(parsedSink)
  //val blackhole = broadcast.runForeach(msg => println("blackhole: "+ msg))
  
  //val infoResult = broadcast.via(wantedEventsFlow).runWith(infoSink)
  //val parsedResult = broadcast.via(OMIStreamParser.omiParserFlow).runWith(parsedSink)

  val rGraph: RunnableGraph[(_,Future[OmiRequest],Future[WrapperInfo])] = RunnableGraph.fromGraph(
    GraphDSL.create(
      eventSource,
      parsedSink,
      infoSink
    )((_,_,_)){ implicit builder =>
      (parseEventSource,parsedRequestSink, wrapperInfoSink) =>
        import GraphDSL.Implicits._
        val bcast = builder.add(Broadcast[ParseEvent](2,false))
        parseEventSource ~> bcast.in
        bcast ~> OMIStreamParser.omiParserFlow ~> parsedRequestSink.in
        bcast ~> wantedEventsFlow ~> wrapperInfoSink.in
        ClosedShape
    }
  )
  val (_,parsedResult,infoResult): (_,Future[OmiRequest],Future[WrapperInfo]) = rGraph.run()

  //import materializer.executionContext
  //FutureTimer(parsedResult, println, "parse")
  //FutureTimer(infoResult, println, "info")



  
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
      .map { (ttlStr) => parsing.parseTTL(ttlStr.toDouble) }
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

  
  lazy val requestTypeString: String = {
    wrapperInfo.omiVerb.map( _.localName) match {
      case Some("read") =>
        if(wrapperInfo.odfObjects.isEmpty) "poll" 
        else if(wrapperInfo.omiVerb.flatMap{ _.attributes.get("interval") }.nonEmpty ) "subscription" else "read" 
      case Some(other: String) => other
      case None => "not found"
    }
  }
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

import types.omi.RawRequestWrapper.MessageType

/**
  * One-time-read request
  **/
case class ReadRequest(
                        odf: ODF,
                        begin: Option[Timestamp] = None,
                        end: Option[Timestamp] = None,
                        newest: Option[Int] = None,
                        oldest: Option[Int] = None,
                        maxLevels: Option[Int] = None,
                        callback: Option[Callback] = None,
                        ttl: Duration = 10.seconds,
                        private val user0: UserInfo = UserInfo(),
                        senderInformation: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        requestToken: Option[Long] = None
                      ) extends OmiRequest with OdfRequest {
  user = user0
  def requestTypeString: String = "read"
  override def attributeStr: String = s"""ttl="${ttlAsSeconds}"""" + 
     callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("") + 
     newest.map{ n => s""" newest="$n"""" }.getOrElse("") +
     oldest.map{ n => s""" oldest="$n"""" }.getOrElse("") +
     maxLevels.map{ n => s""" maxlevels="$n"""" }.getOrElse("") +
     begin.map{ ts => s""" begin="${timestampToDateTimeString(ts)}"""" }.getOrElse("") +
     end.map{ ts => s""" end="${timestampToDateTimeString(ts)}\"""" }.getOrElse("") 

  // def this(
  // odf: ODF ,
  // begin: Option[Timestamp ] = None,
  // end: Option[Timestamp ] = None,
  // newest: Option[Int ] = None,
  // oldest: Option[Int ] = None,
  // callback: Option[Callback] = None,
  // ttl: Duration = 10.seconds) = this(odf,begin,end,newest,oldest,callback,ttl,None)
  def withCallback: Option[Callback] => ReadRequest = cb => this.copy(callback = cb)
  def withRequestToken: Option[Long] => ReadRequest = id => this.copy(requestToken = id )

  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events: SeqView[ParseEvent,Seq[_]] = Vector(
      StartElement("read",
        List(
          Attribute("msgformat","odf"),
          //Attribute("targetType","node") TODO
        ) ++ newest.map{
          n => Attribute("newest",n.toString)
        }.toList ++ oldest.map{
          n => Attribute("oldest",n.toString)
        }.toList ++ maxLevels.map{
          n => Attribute("maxlevels",n.toString)
        }.toList ++ begin.map{
          timestamp => Attribute("begin",timestampToDateTimeString(timestamp))
        }.toList ++ end.map{
          timestamp => Attribute("end",timestampToDateTimeString(timestamp))
        }.toList ++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ odfAsOmiMsg ++ Vector(EndElement("read")).view
    omiEnvelopeEvents(events)
  }

  def replaceOdf(nOdf: ODF): ReadRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Read.type = MessageType.Read
}

/**
  * Poll request
  **/
case class PollRequest(
                        callback: Option[Callback] = None,
                        requestIDs: OdfCollection[RequestID] = OdfCollection.empty,
                        ttl: Duration = 10.seconds,
                        private val user0: UserInfo = UserInfo(),
                        senderInformation: Option[SenderInformation] = None,
                        ttlLimit: Option[Timestamp] = None,
                        requestToken: Option[Long] = None
                      ) extends OmiRequest with RequestIDRequest {
  def requestTypeString: String = "poll"
  override def attributeStr: String = s"""ttl="${ttlAsSeconds}"""" +callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("") 

  user = user0

  def withCallback: Option[Callback] => PollRequest = cb => this.copy(callback = cb)
  def withRequestToken: Option[Long] => PollRequest = id => this.copy(requestToken = id )

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Read.type = MessageType.Read
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("read",
        List(
          //Attribute("targetType","node") TODO
        ) ++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ requestIDs.map{
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
                                requestToken: Option[Long] = None
                              ) extends OmiRequest  with OdfRequest {
  require(interval == -1.seconds || interval == -2.seconds || interval >= 0.seconds, s"Invalid interval: $interval")
  require(ttl >= 0.seconds, s"Invalid ttl, should be positive (or +infinite): $ttl")
  user = user0

  override def attributeStr: String = s"""interval="${interval.toSeconds}" ttl="${ttlAsSeconds}"""" +callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("") 
  def requestTypeString: String = "subscription"
  def withCallback: Option[Callback] => SubscriptionRequest = cb => this.copy(callback = cb)
  def withRequestToken: Option[Long] => SubscriptionRequest = id => this.copy(requestToken = id )

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  def replaceOdf(nOdf: ODF): SubscriptionRequest = copy(odf = nOdf)

  val requestVerb: MessageType.Read.type = MessageType.Read
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("read",
        List(
          Attribute("msgformat","odf"),
          //Attribute("targetType","node"), TODO
          Attribute("interval",interval.toSeconds.toString)
        ) ++ newest.map{
          n => Attribute("newest",n.toString)
        }.toList ++ oldest.map{
          n => Attribute("oldest",n.toString)
        }.toList ++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ odfAsOmiMsg ++ Vector(EndElement("read"))
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
                         requestToken: Option[Long] = None
                       ) extends OmiRequest with OdfRequest with PermissiveRequest {

  user = user0

  override def attributeStr: String = s"""ttl="${ttlAsSeconds}"""" + callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("")  
  def requestTypeString: String = "write"
  def withCallback: Option[Callback] => WriteRequest = cb => this.copy(callback = cb)
  def withRequestToken: Option[Long] => WriteRequest = id => this.copy(requestToken = id )

  def replaceOdf(nOdf: ODF): WriteRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb = MessageType.Write
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("write",
        List(
          Attribute("msgformat","odf"),
          //Attribute("targetType","node") TODO
        )++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view  ++ odfAsOmiMsg ++ Vector(EndElement("write"))
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
                        requestToken: Option[Long] = None
                      ) extends OmiRequest with OdfRequest with PermissiveRequest {
  override def attributeStr: String = s"""ttl="${ttlAsSeconds}"""" + callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("")  
  def requestTypeString: String = "call"
  user = user0

  def withCallback: Option[Callback] => CallRequest = cb => this.copy(callback = cb)
  def withRequestToken: Option[Long] => CallRequest = id => this.copy(requestToken = id )

  def replaceOdf(nOdf: ODF): CallRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Call.type = MessageType.Call
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("call",
        List(
          Attribute("msgformat","odf"),
          //Attribute("targetType","node") TODO
        )++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ odfAsOmiMsg ++ Vector(EndElement("call"))
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
                          requestToken: Option[Long] = None
                        ) extends OmiRequest with OdfRequest with PermissiveRequest {
  user = user0
  override def attributeStr: String = s"""ttl="${ttlAsSeconds}"""" + callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("") 
  def requestTypeString: String = "delete"

  def withCallback: Option[Callback] => DeleteRequest = cb => this.copy(callback = cb)
  def withRequestToken: Option[Long] => DeleteRequest = id => this.copy(requestToken = id )

  def replaceOdf(nOdf: ODF): DeleteRequest = copy(odf = nOdf)

  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))

  val requestVerb: MessageType.Delete.type = MessageType.Delete
  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
    val events = Vector(
      StartElement("delete",
        List(
          Attribute("msgformat","odf"),
          //Attribute("targetType","node") TODO
        )++ callback.map{
          cb => Attribute("callback",cb.toString)
        }.toList
      )
    ).view ++ odfAsOmiMsg ++ Vector(EndElement("delete"))
    omiEnvelopeEvents(events)
  }
}

/**
  * Cancel request, for cancelling subscription.
  **/
case class CancelRequest(
                          requestIDs: OdfCollection[RequestID] = OdfCollection.empty,
                          ttl: Duration = 10.seconds,
                          private val user0: UserInfo = UserInfo(),
                          senderInformation: Option[SenderInformation] = None,
                          ttlLimit: Option[Timestamp] = None,
                          requestToken: Option[Long] = None
                        ) extends OmiRequest with RequestIDRequest {
  user = user0

  override def attributeStr: String = s"""ttl="${ttlAsSeconds}""""
  def requestTypeString: String = "cancel"

  def callback: Option[Callback] = None

  def withCallback: Option[Callback] => CancelRequest = cb => this
  def withRequestToken: Option[Long] => CancelRequest = id => this.copy(requestToken = id )

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
                       val requestToken: Option[Long] = None,
                       val requestInfo: Option[PRequestInfo] = None,
                     ) extends OmiRequest with PermissiveRequest with JavaResponseRequest {
  user = user0

  def resultsAsJava(): JIterable[OmiResult] = asJavaIterable(results)

  override def attributeStr: String = s"""ttl="${ttlAsSeconds}"""" + callback.map{ cb => s""" callback="${cb.address}"""" }.getOrElse("") 
  def requestTypeString: String = "response"
  def withCallback: Option[Callback] => ResponseRequest = cb => this.copy(callback = cb)
  def withSenderInformation(si: SenderInformation): OmiRequest = this.copy(senderInformation = Some(si))
  def withRequestToken: Option[Long] => ResponseRequest = id => this.copy(requestToken = id )
  def withRequestInfo: PRequestInfo => ResponseRequest = ri => this.copy(requestInfo = ri )
  def withRequestInfoFrom(s: SingleStores)(implicit ec: ExecutionContext): Future[ResponseRequest] =
    s.getRequestInfo(this) map this.withRequestInfo

  def odf: ODF = results.foldLeft(ImmutableODF()) {
    case (l: ODF, r: OmiResult) =>
      l.union(r.odf.getOrElse(ImmutableODF())).toImmutable
  }

  def union(another: ResponseRequest): ResponseRequest = {
    ResponseRequest(
                     Results.unionReduce((results ++ another.results)),
      if (ttl >= another.ttl) ttl else another.ttl
    )
  }

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
      ttl,
      requestToken = requestToken
    )
  }

  val requestVerb: MessageType.Response.type = MessageType.Response


  def omiVersion: OmiVersion = requestInfo map (OmiVersion fromNumber _.omiVersion) getOrElse OmiVersion1
  def odfVersion: OdfVersion = (for {
    ri <- requestInfo
    ov <- ri.odfVersion
  } yield OdfVersion.fromNumber(ov)) getOrElse OdfVersion1

  def asXMLEvents: SeqView[ParseEvent,Seq[_]] ={
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

