/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2019 Aalto University.                                        +
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
package parsing

import java.sql.Timestamp
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.concurrent.duration._
import scala.util.Try

import akka.NotUsed
import akka.util._
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import types._
import types.odf._
import types.odf.parsing.ODFEventBuilder
import utils._

private object RequestPosition extends Enumeration {
  type RequestPosition = Value
  val OpenRequest, NodeList, RequestId, OpenMsg, OpenObjects, CloseMsg, CloseRequest = Value
}
import RequestPosition.{RequestPosition,OpenRequest, NodeList, RequestId, OpenMsg, OpenObjects, 
  CloseMsg, CloseRequest} 
class EnvelopeEventBuilder(
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp
) extends EventBuilder[OmiRequest]{
  private var version: String = "2.0"
  private var ttl: Duration = 0.seconds
  private var request: Option[OmiRequest] = None
  private var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete && request.nonEmpty
  def setRequest( req: OmiRequest ): EnvelopeEventBuilder ={
    request = Some(req)
    this
  }

  def build: OmiRequest = request.getOrElse{ throw OMIParserError("incomplete request build")}

  def parse( event: ParseEvent): EventBuilder[_] ={
    event match {
      case StartDocument => this
      case EndDocument if isComplete => this
      case EndDocument if !isComplete => 
        if (request.isEmpty)
          unexpectedEventHandle("Expecting request tag", EndDocument, this)
        else
          unexpectedEventHandle("Expecting omiEnvelope close tag", EndDocument, this)

      case startElement: StartElement if startElement.localName == "omiEnvelope" =>
        val correctNS = startElement.namespace.forall{
          str: String =>
            str.startsWith("http://www.opengroup.org/xsd/omi/")
        }
        if( !correctNS )
          throw OMIParserError("Wrong namespace url.")
        val ver = startElement.attributes.get("version")
        val ttlO = startElement.attributes.get("ttl").flatMap{
          str: String => 
            Try{
              println("ttl check: " + str + " " + str.toDouble )
              parseTTL(str.toDouble)
            }.toOption
        }
        if( ttlO.isEmpty ){
          throw OMIParserError("No correct ttl as attribute in omiEnvelope")
        } else if( ver.isEmpty ){
          throw OMIParserError("No correct version as attribute in omiEnvelope")
        } else {
          version = ver.getOrElse("2.0")
          ttl = ttlO.getOrElse(Duration.Inf)
          this
        }
      case startElement: StartElement if startElement.localName == "read" =>
        new ReadEventBuilder(ttl,Some(this),receiveTime).parse(event)
      case startElement: StartElement if startElement.localName == "write" =>
        new WriteEventBuilder(ttl,Some(this),receiveTime).parse(event)
      case startElement: StartElement if startElement.localName == "call" =>
        new CallEventBuilder(ttl,Some(this),receiveTime).parse(event)
      case startElement: StartElement if startElement.localName == "cancel" =>
        new CancelEventBuilder(ttl,Some(this),receiveTime).parse(event)
      case startElement: StartElement if startElement.localName == "delete" =>
        new DeleteEventBuilder(ttl,Some(this),receiveTime).parse(event)
      case startElement: StartElement if startElement.localName == "response" =>
        new ResponseEventBuilder(ttl, Some(this),receiveTime).parse(event)
      case endElement: EndElement if endElement.localName == "omiEnvelope" =>
        if( request.nonEmpty ){
          complete = true
          previous match {
            case None => this
            case Some( builder: EventBuilder[_] ) => 
              throw OMIParserError("Wrong previous builder for omiEnvelope.")
          }
        } else
            throw OMIParserError("OmiEnvelope completed without request.")
      case event: ParseEvent if complete =>
        unexpectedEventHandle("after complete omiEnvelope element.", event, this)
      case event: ParseEvent =>
        unexpectedEventHandle("before omiEnvelope element.", event, this)
    }
  }
}
trait OdfRequestEventBuilder[T] extends EventBuilder[T] {
  protected var odf: ODF = ImmutableODF()
  protected var complete: Boolean = false 
  final def isComplete: Boolean = previous.isEmpty && complete
  def addODF(_odf: ODF ): OdfRequestEventBuilder[T] ={
    odf = _odf
    this
  }
}
class WriteEventBuilder(
  val ttl: Duration, 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends OdfRequestEventBuilder[WriteRequest]{
  private var msgformat: String = ""
  private var callback: Option[Callback] = None
  private var position: RequestPosition = OpenRequest
  def build: WriteRequest = WriteRequest( odf, callback, ttl) 
  def parse( event: ParseEvent ): EventBuilder[_] ={
    position match {
      case OpenRequest =>
        event match {
          case startElement: StartElement if startElement.localName == "write" =>
            callback = startElement.attributes.get("callback").map{ str: String => RawCallback(str)}
            val mf = startElement.attributes.get("msgformat")
            if( mf.isEmpty ){
              throw OMIParserError("No msgformat for write")
            } else {
              msgformat = mf.getOrElse("")
              position = OpenMsg
              this
            }
          case event: ParseEvent =>
            unexpectedEventHandle("before write element.", event, this)
        }
      case OpenMsg =>
        event match {
          case startElement: StartElement if startElement.localName == "nodeList" =>
            throw OMIParserError("nodeList is not supported by O-MI Node.")
          case startElement: StartElement if startElement.localName == "requestID" =>
            throw OMIParserError("Write request doesn't use requestID.")
          case startElement: StartElement if startElement.localName == "msg" =>
            msgformat match {
              case "odf" =>
                position = OpenObjects
                this
              case unknown: String => throw OMIParserError(s"Unknown msgformat: $unknown. Do not know how to parse content inside msg element.")
            }
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("before msg element.", event, this)
        }
      case OpenObjects =>
        event match {
          case startElement: StartElement if startElement.localName == "Objects" =>
            position = CloseMsg
            new ODFEventBuilder(Some(this),receiveTime).parse(event)
          case event: ParseEvent =>
            unexpectedEventHandle("in msg element.", event, this)
        }
      case CloseMsg =>
        event match {
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("in msg element.", event, this)
        }
      case CloseRequest =>
        event match {
          case endElement: EndElement if endElement.localName == "write" =>
            complete = true
            previous match {
              case None => this
              case Some( builder: EnvelopeEventBuilder ) => builder.setRequest( build)
            }
          case event: ParseEvent if( complete )=>
            unexpectedEventHandle("after complete write request.", event, this)
          case event: ParseEvent =>
            unexpectedEventHandle("before write element closing.", event, this)
        }

    }
  }
}
class CallEventBuilder( 
  val ttl: Duration, 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends OdfRequestEventBuilder[CallRequest]{
  private var msgformat: String = ""
  private var callback: Option[Callback] = None
  private var position: RequestPosition = OpenRequest
  def build: CallRequest = CallRequest( odf, callback, ttl) 
  def parse( event: ParseEvent ): EventBuilder[_] ={
    position match {
      case OpenRequest =>
        event match {
          case startElement: StartElement if startElement.localName == "call" =>
            callback = startElement.attributes.get("callback").map{ str: String => RawCallback(str)}
            val mf = startElement.attributes.get("msgformat")
            if( mf.isEmpty ){
              throw OMIParserError("No msgformat for call")
            } else {
              msgformat = mf.getOrElse("")
              position = OpenMsg
              this
            }
          case event: ParseEvent =>
            unexpectedEventHandle("before call element.", event, this)
        }
      case OpenMsg =>
        event match {
          case startElement: StartElement if startElement.localName == "nodeList" =>
            throw OMIParserError("nodeList is not supported by O-MI Node.")
          case startElement: StartElement if startElement.localName == "requestID" =>
            throw OMIParserError("Call request doesn't use requestID.")
          case startElement: StartElement if startElement.localName == "msg" =>
            msgformat match {
              case "odf" =>
                position = OpenObjects
                this
              case unknown: String => throw OMIParserError(s"Unknown msgformat: $unknown. Do not know how to parse content inside msg element.")
            }
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("before msg element.", event, this)
        }
      case OpenObjects =>
        event match {
          case startElement: StartElement if startElement.localName == "Objects" =>
            position = CloseMsg
            new ODFEventBuilder(Some(this),receiveTime).parse(event)
          case event: ParseEvent =>
            unexpectedEventHandle("in msg element.", event, this)
        }
      case CloseMsg =>
        event match {
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("in msg element.", event, this)
        }
      case CloseRequest =>
        event match {
          case endElement: EndElement if endElement.localName == "call" =>
            complete = true
            previous match {
              case None => this
              case Some( builder: EnvelopeEventBuilder ) => builder.setRequest( build)
            }
          case event: ParseEvent if( complete )=>
            unexpectedEventHandle("after complete call request.", event, this)
          case event: ParseEvent =>
            unexpectedEventHandle("before call element closing.", event, this)
        }

    }
  }
}
class DeleteEventBuilder( 
  val ttl: Duration, 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends OdfRequestEventBuilder[DeleteRequest]{
  private var msgformat: String = ""
  private var callback: Option[Callback] = None
  private var position: RequestPosition = OpenRequest
  def build: DeleteRequest = DeleteRequest( odf, callback, ttl) 
  def parse( event: ParseEvent ): EventBuilder[_] ={
    position match {
      case OpenRequest =>
        event match {
          case startElement: StartElement if startElement.localName == "delete" =>
            callback = startElement.attributes.get("callback").map{ str: String => RawCallback(str)}
            val mf = startElement.attributes.get("msgformat")
            if( mf.isEmpty ){
              throw OMIParserError("No msgformat for delete")
            } else {
              msgformat = mf.getOrElse("")
              position = OpenMsg
              this
            }
          case event: ParseEvent =>
            unexpectedEventHandle("before delete element.", event, this)
        }
      case OpenMsg =>
        event match {
          case startElement: StartElement if startElement.localName == "nodeList" =>
            throw OMIParserError("nodeList is not supported by O-MI Node.")
          case startElement: StartElement if startElement.localName == "requestID" =>
            throw OMIParserError("Delete request doesn't use requestID.")
          case startElement: StartElement if startElement.localName == "msg" =>
            msgformat match {
              case "odf" =>
                position = OpenObjects
                this
              case unknown: String => throw OMIParserError(s"Unknown msgformat: $unknown. Do not know how to parse content inside msg element.")
            }
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("before msg element.", event, this)
        }
      case OpenObjects =>
        event match {
          case startElement: StartElement if startElement.localName == "Objects" =>
            position = CloseMsg
            new ODFEventBuilder(Some(this),receiveTime).parse(event)
          case event: ParseEvent =>
            unexpectedEventHandle("in msg element.", event, this)
        }
      case CloseMsg =>
        event match {
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("in msg element.", event, this)
        }
      case CloseRequest =>
        event match {
          case endElement: EndElement if endElement.localName == "delete" =>
            complete = true
            previous match {
              case None => this
              case Some( builder: EnvelopeEventBuilder ) => builder.setRequest( build)
            }
          case event: ParseEvent if( complete )=>
            unexpectedEventHandle("after complete delete request.", event, this)
          case event: ParseEvent =>
            unexpectedEventHandle("before delete element closing.", event, this)
        }

    }
  }
}
class ReadEventBuilder( 
  val ttl: Duration, 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends OdfRequestEventBuilder[OmiRequest]{
  private var msgformat: Option[String] = None
  private var callback: Option[Callback] = None
  private var position: RequestPosition = OpenRequest
  private var newest: Option[Int] = None
  private var oldest: Option[Int] = None
  private var intervalO: Option[Duration] = None
  private var begin: Option[Timestamp] = None
  private var end: Option[Timestamp] = None
  private var maxLevels: Option[Int] = None
  private var all: Option[Boolean] = None

  private var requestIds: List[Long] = List.empty
  def addRequestId( id: Long ): ReadEventBuilder ={
    requestIds = id :: requestIds
    this
  }
  def build: OmiRequest ={
    intervalO match{
      case Some(interval:Duration) =>
        SubscriptionRequest(
          interval,
          odf,
          newest,
          oldest,
          callback,
          ttl
        )
      case None =>
        if( requestIds.nonEmpty && msgformat.isEmpty){
          PollRequest(
            callback,
            requestIds.toVector,
            ttl
          )
        } else {
          ReadRequest(
            odf,
            begin,
            end,
            newest,
            oldest,
            maxLevels,
            callback,
            ttl
          )
        }
    }
  }
  def parse( event: ParseEvent ): EventBuilder[_] ={
    position match {
      case OpenRequest =>
        event match {
          case startElement: StartElement if startElement.localName == "read" =>
              msgformat = startElement.attributes.get("msgformat")
              all = startElement.attributes.get("all").map{ 
                str: String => 
                  Try{ str.toBoolean }.getOrElse{ throw OMIParserError("Attribute all found for read request but it is not type of boolean.")}
              }
              maxLevels = startElement.attributes.get("maxlevels").map{ 
                str: String => 
                  Try{ str.toInt }.getOrElse{ throw OMIParserError("Attribute maxlevels found for read request but it is not type of Int.")}
              }

              newest = startElement.attributes.get("newest").map{ 
                str: String => 
                  Try{ str.toInt }.getOrElse{ throw OMIParserError("Attribute newest found for read request but it is not type of Int.")}
              }
              oldest = startElement.attributes.get("oldest").map{ 
                str: String => 
                  Try{ str.toInt }.getOrElse{ throw OMIParserError("Attribute oldest found for read request but it is not type of Int.")}
              }
              if( newest.nonEmpty && oldest.nonEmpty)
                throw OMIParserError("Invalid Read request, Can not query oldest and newest values at same time.")
              
              begin = startElement.attributes.get("begin").map{ 
                str: String => 
                  Try{ dateTimeStrToTimestamp(str) }.getOrElse{ throw OMIParserError("Attribute begin found for read request but it is not dateTime.")}
              }
              end = startElement.attributes.get("end").map{ 
                str: String => 
                  Try{ dateTimeStrToTimestamp(str) }.getOrElse{ throw OMIParserError("Attribute end found for read request but it is not dateTime.")}
              }
              intervalO = startElement.attributes.get("interval").map{ 
                str: String => 
                  Try{ str.toDouble.seconds }.getOrElse{ throw OMIParserError("Attribute interval found for read request but it is not Double.")}
              }
              if( (begin.nonEmpty || end.nonEmpty ) && intervalO.nonEmpty )
                throw OMIParserError("Attributes begin and end can not be used at same time wth interval attribute.")
              if( all.nonEmpty )//TODO: Remove after all is implemented correctly.
                throw OMIParserError("Attribute all not supported yet.")
              if( all.nonEmpty && (intervalO.nonEmpty || begin.nonEmpty || end.nonEmpty || newest.nonEmpty || oldest.nonEmpty ) )
                throw OMIParserError("Attribute all can not use with begin, end, newest, oldest or interval attribute.")
              callback = startElement.attributes.get("callback").map{ str: String => RawCallback(str)}
              position = RequestId
              this
          case event: ParseEvent =>
            unexpectedEventHandle("before read element.", event, this)
        }
      case RequestId =>
        event match {
          case startElement: StartElement if startElement.localName == "nodeList" =>
            throw OMIParserError("nodeList is not supported by O-MI Node.")
          case startElement: StartElement if startElement.localName == "msg" =>
            position = OpenMsg
            this.parse(event)
          case startElement: StartElement if startElement.localName == "requestID" =>
            if( msgformat.nonEmpty || intervalO.nonEmpty || newest.nonEmpty || oldest.nonEmpty || begin.nonEmpty || end.nonEmpty || all.nonEmpty)
              throw OMIParserError("Element requestID can not be used with interval, newest, oldest, begin, end or all attribute.")
            else
              new RequestIdEventBuilder(Some(this), receiveTime).parse(event)
          case endElement: EndElement if endElement.localName == "read" =>
            position = CloseRequest
            this.parse(event)
          case event: ParseEvent =>
            unexpectedEventHandle("after requestID element", event, this)
        }
      case OpenMsg =>
        event match {
          case startElement: StartElement if startElement.localName == "nodeList" =>
            throw OMIParserError("nodeList is not supported by O-MI Node.")
          case startElement: StartElement if startElement.localName == "msg" =>
            position = CloseMsg
            msgformat match {
              case Some("odf") =>
                new ODFEventBuilder(Some(this),receiveTime)
              case Some(unknown: String) => 
                throw OMIParserError(s"Unknown msgformat: $unknown. Do not know how to parse content inside msg element.")
              case None =>
                throw OMIParserError(s"No msgformat. Do not know how to parse content inside msg element.")
            }
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("before msg element.", event, this)
        }
      case CloseMsg =>
        event match {
          case endElement: EndElement if endElement.localName == "msg" =>
            position = CloseRequest
            this
          case event: ParseEvent =>
            unexpectedEventHandle("after msg element.", event, this)
        }
      case CloseRequest =>
        event match {
          case endElement: EndElement if endElement.localName == "read" =>
            complete = true
            previous match {
              case None => this
              case Some( builder: EnvelopeEventBuilder ) => builder.setRequest( build)
            }
          case event: ParseEvent if( complete )=>
            unexpectedEventHandle("after complete read request.", event, this)
          case event: ParseEvent =>
            unexpectedEventHandle("before read element closing.", event, this)
        }

    }
  }
}
class CancelEventBuilder( 
  val ttl: Duration, 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends OdfRequestEventBuilder[CancelRequest]{
  private var callback: Option[Callback] = None
  private var position: RequestPosition = OpenRequest
  private var requestIds: List[Long] = List.empty

  def addRequestId( id: Long ): CancelEventBuilder ={
    requestIds = id :: requestIds
    this
  }
  def build: CancelRequest = CancelRequest(requestIds.toVector,ttl)
  def parse( event: ParseEvent ): EventBuilder[_] ={
    position match {
      case OpenRequest =>
        event match {
          case startElement: StartElement if startElement.localName == "cancel" =>
            position = RequestId
            this
          case event: ParseEvent =>
            unexpectedEventHandle("before cancel element.", event, this)
        }
      case RequestId =>
        event match {
          case startElement: StartElement if startElement.localName == "nodeList" =>
            throw OMIParserError("nodeList is not supported by O-MI Node.")
          case startElement: StartElement if startElement.localName == "requestID" =>
              new RequestIdEventBuilder(Some(this)).parse(event)
          case endElement: EndElement if endElement.localName == "cancel" =>
            position = CloseRequest
            this.parse(event)
          case event: ParseEvent =>
            unexpectedEventHandle("before requestID element.", event, this)
        }
      case CloseRequest =>
        event match {
          case endElement: EndElement if endElement.localName == "cancel" =>
            complete = true
            previous match {
              case None => this
              case Some( builder: EnvelopeEventBuilder ) => builder.setRequest( build)
            }
          case event: ParseEvent if( complete )=>
            unexpectedEventHandle("after complete cancel request.", event, this)
          case event: ParseEvent =>
            unexpectedEventHandle("before cancel element closing.", event, this)
        }

    }
  }
}
class RequestIdEventBuilder( 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends EventBuilder[Long]{
    object Position extends Enumeration {
      type Position = Value
      val OpenTag, Content, CloseTag = Value 
    }
    import Position._
    private var position: Position = OpenTag
    private var text: String = ""
    def build: Long = {
      Try{ 
        text.toLong
      }.getOrElse{
        throw OMIParserError(s"RequestId $text was not type Long.") 
      }
      
    } 
    private var complete: Boolean = false
    final def isComplete: Boolean = previous.isEmpty && complete

    def parse(event: ParseEvent): EventBuilder[_] ={
        position match{
          case OpenTag =>
            event match {
              case startElement: StartElement if startElement.localName == "requestID" =>
                position = Content
                this
              case event: ParseEvent =>
                unexpectedEventHandle( "before expected requestID element.", event, this)
            }
          case Content =>
            event match {
              case content: TextEvent => 
                text = text + content.text
                position = CloseTag
                this
              case event: ParseEvent =>
                unexpectedEventHandle( "when expected text content.", event, this)
            }
          case CloseTag =>
            event match {
              case event: ParseEvent if complete =>
                unexpectedEventHandle( s"after complete requestID element.", event, this)
              case content: TextEvent => 
                text = text + content.text
                this
              case endElement: EndElement if endElement.localName == "requestID" =>
                complete = true
                previous match {
                  case Some(state: ReadEventBuilder) => 
                    state.addRequestId(build)
                  case Some(state: CancelEventBuilder) => 
                    state.addRequestId(build)
                  case Some(state: ResultEventBuilder) => 
                    state.addRequestId(build)
                  case Some(state: EventBuilder[_]) => throw OMIParserError("RequestId state after wrong state. Previous should be read.")
                  case None =>
                    this
                }
              case event: ParseEvent =>
                unexpectedEventHandle( s"before expected closing of requestID.", event, this)
            }
        }
    }
}

class ResponseEventBuilder( 
  val ttl: Duration, 
  val previous: Option[EventBuilder[_]], implicit val receiveTime: Timestamp = currentTimestamp 
) extends EventBuilder[ResponseRequest]{
    object Position extends Enumeration {
      type Position = Value
      val OpenResponse, Results, CloseResponse = Value 
    }
    import Position._
    private var callback: Option[Callback] = None
    private var position: Position = OpenResponse 
    private var complete: Boolean = false
    private var results: List[OmiResult] = List.empty
    final def isComplete: Boolean = previous.isEmpty && complete
    def addResult(result: OmiResult): ResponseEventBuilder = {
      results = result :: results
      this
    }
    def build: ResponseRequest ={
      ResponseRequest(
        results.toVector,
        ttl,
        callback
      )
    }

    def parse(event: ParseEvent): EventBuilder[_] ={
      position match{
        case OpenResponse =>
          event match {
            case startElement: StartElement if startElement.localName == "response" =>
              callback = startElement.attributes.get("callback").map{ str: String => RawCallback(str)}
              position = Results
              this
              
            case event: ParseEvent =>
              unexpectedEventHandle("before response element.", event, this)
          }
        case Results =>
          event match {
            case startElement: StartElement if startElement.localName == "result" =>
              position = CloseResponse
              new ResultEventBuilder(Some(this),receiveTime).parse(event)
            case event: ParseEvent =>
              unexpectedEventHandle("before result element.", event, this)
          }
        case CloseResponse =>
          event match {
            case event: ParseEvent if( complete )=>
              unexpectedEventHandle("after complete response.", event, this)
            case startElement: StartElement if startElement.localName == "result" =>
              position = CloseResponse
              new ResultEventBuilder(Some(this),receiveTime).parse(event)
            case endElement: EndElement if endElement.localName == "response" =>
              complete = true
              previous match {
                case Some(builder: EnvelopeEventBuilder) => 
                  builder.setRequest(build)
                case Some(state: EventBuilder[_]) => 
                  throw OMIParserError("ResponseEventBuilder after wrong EventBuilder. Previous should be omiEnvelope.")
                case None =>
                  this
              }
            case event: ParseEvent =>
              unexpectedEventHandle("before response element closing.", event, this)
          }
      }

    }
}
class ResultEventBuilder( 
  val previous: Option[EventBuilder[_]], 
  implicit val receiveTime: Timestamp = currentTimestamp 
) extends EventBuilder[OmiResult]{
    object Position extends Enumeration {
      type Position = Value
      val OpenResult, Return, RequestIds, OpenMsg,CloseMsg, CloseResult = Value 
    }
    import Position._
    private var callback: Option[Callback] = None
    private var position: Position = OpenResult 
    private var complete: Boolean = false
    private var odf: Option[ODF] = None
    private var msgformat: Option[String] = None
    private var returnCode: Option[String] = None
    private var returnDescription: Option[String] = None 
    private var requestIds: List[Long] = List.empty
  final def isComplete: Boolean = previous.isEmpty && complete
    def addRequestId( id: Long ): ResultEventBuilder ={
      requestIds = id :: requestIds
      this
    }
    def addODF( _odf: ODF ): ResultEventBuilder ={
      odf = Some(_odf)
      this
    }
    def build: OmiResult ={
      val ret = OmiReturn(
        returnCode.get,
        returnDescription
      )
      OmiResult(
        ret,
        requestIds.toVector,
        odf
      )
    }
    def parse(event: ParseEvent): EventBuilder[_] ={
      position match{
        case OpenResult =>
          event match {
            case startElement: StartElement if startElement.localName == "result" => 
              msgformat = startElement.attributes.get("msgformat")
              position = Return
              this
            case event: ParseEvent =>
              unexpectedEventHandle("before result element.", event, this)
          }
        case Return =>
          event match {
            case startElement: StartElement if startElement.localName == "return" => 
              if( returnCode.nonEmpty )
                throw OMIParserError("Another return element found inside return element.")
              returnDescription = startElement.attributes.get("description")
              returnCode = startElement.attributes.get("returnCode")
              if( returnCode.isEmpty )
                throw OMIParserError("return element requires returnCode attribute.")
              this
            case endElement: EndElement if endElement.localName == "return" => 
              position = RequestIds
              this
            case event: ParseEvent =>
              unexpectedEventHandle("when expecting return element.", event, this)
          }
        case RequestIds =>
          event match {
            case startElement: StartElement if startElement.localName == "requestID" => 
              new RequestIdEventBuilder(Some(this)).parse(event)
            case endElement: EndElement if endElement.localName == "result" => 
              position = CloseResult
              this.parse(event)
            case startElement: StartElement if startElement.localName == "msg" => 
              position = Position.OpenMsg
              this.parse(event)
            case event: ParseEvent =>
              unexpectedEventHandle("before requestID element.", event, this)
          }
        case Position.OpenMsg =>
          event match {
            case startElement: StartElement if startElement.localName == "msg" => 
              position = Position.CloseMsg
              msgformat match {
                case Some("odf") =>
                  new ODFEventBuilder(Some(this),receiveTime)
                case Some(unknown: String) => throw OMIParserError(s"Unknown msgformat: $unknown. Do not know how to parse content inside msg element.")
                case None =>
                  throw OMIParserError(s"No msgformat for msg element in result element.")
              }
            case event: ParseEvent =>
              unexpectedEventHandle("before msg element.",event, this)
          }
        case Position.CloseMsg =>
          event match {
            case endElement: EndElement if endElement.localName == "msg" => 
              position = CloseResult
              this
            case event: ParseEvent =>
              unexpectedEventHandle("before msg element closing.", event, this)
          }
        case CloseResult =>
          event match {
            case event: ParseEvent if( complete )=>
              unexpectedEventHandle("after complete result.", event, this)
            case endElement: EndElement if endElement.localName == "result" => 
              complete = true
              previous match {
                case Some( builder: ResponseEventBuilder ) => builder.addResult( build)
                case Some( builder ) =>
                  throw OMIParserError("ResultEventBuilder after wrong EventBuilder. Previous should be response.")
                case None =>
                  this
              }
            case event: ParseEvent =>
              unexpectedEventHandle("before result element closing.", event, this)
          }

      }
    }
}
