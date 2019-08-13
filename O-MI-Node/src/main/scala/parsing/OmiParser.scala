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
package parsing

import java.io.File
import java.sql.Timestamp

import javax.xml.transform.Source
import javax.xml.transform.stream.StreamSource
import parsing.xmlGen.xmlTypes
import types.OmiTypes._
import types.ParseError._
import types._
import types.odf._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node}

/** Parser for messages with O-MI protocol */
object OmiParser extends Parser[OmiParseResult] {

  protected[this] override def schemaPath: Array[Source] = Array[Source](
                                                                          new StreamSource(getClass
                                                                                             .getClassLoader.getResourceAsStream("omi-old.xsd")),
                                                                          new StreamSource(getClass
                                                                                             .getClassLoader.getResourceAsStream("odf-old.xsd")),
                                                                          new StreamSource(getClass
                                                                                             .getClassLoader.getResourceAsStream("omi.xsd")),
                                                                          new StreamSource(getClass
                                                                                             .getClassLoader.getResourceAsStream("odf.xsd"))
  )

  /**
    * Public method for parsing the xml file into OmiParseResults.
    *
    * @param file XML formatted string to be parsed. Should be in O-MI format.
    * @return OmiParseResults
    */
  def parse(file: File): OmiParseResult = {
    val parsed = Try(
      XMLParser.loadFile(file)
    )

    parseTry(parsed)
  }
  def parse(filepath: java.nio.file.Path): OmiParseResult = {
    val parsed = Try{
      val is = java.nio.file.Files.newInputStream(filepath)
      XMLParser.load(is)
    }

    parseTry(parsed)
  }


  /**
    * Public method for parsing the xml string into OmiParseResults.
    *
    * @param xml_msg XML formatted string to be parsed. Should be in O-MI format.
    * @return OmiParseResults
    */
  def parse(xml_msg: String): OmiParseResult = {
    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val parsed = Try(
      XMLParser.loadString(xml_msg)
    )
    parseTry(parsed)

  }

  private def parseTry(parsed: Try[Elem]): OmiParseResult = {
    parsed match {
      case Success(root) => parseOmi(root)
      case Failure(f) => Left(Vector(ScalaXMLError(f.getMessage)))
    }
  }

  /**
    * Public method for parsing the xml root node into OmiParseResults.
    *
    * @param root XML formatted string to be parsed. Should be in O-MI format.
    * @return OmiParseResults
    */
  @deprecated("Not supported because of xml external entity attack fix, use this.XMLParser! -- TK", "2016-04-01")
  def parse(root: xml.Node): OmiParseResult = parseOmi(root)

  private def parseOmi(root: xml.Node): OmiParseResult = schemaValidation(root) match {
    case errors: Seq[ParseError] if errors.nonEmpty =>
      Left(errors)
    case empty: Seq[ParseError] if empty.isEmpty =>
      Try {
        xmlGen.scalaxb.fromXML[xmlTypes.OmiEnvelopeType](root)
      } match {
        case Failure(e) =>
          //println( s"Exception: $e\nStackTrace:\n")
          e.printStackTrace()
          Left(Vector(ScalaxbError(e.getMessage)))

        case Success(envelope) =>
          Try {
            //protocol version check
            if (envelope.version != supportedVersion) {
              throw new Exception(s"Unsupported protocol version: ${
                envelope
                  .version
              } current supported Version is $supportedVersion")
            }

            // Try to recognize unsupported features
            envelope.omienvelopetypeoption.value match {
              case request: xmlTypes.RequestBaseType if request.nodeList.isDefined =>
                throw new NotImplementedError("nodeList attribute functionality is not supported")
              case _ => //noop
            }

            envelope.omienvelopetypeoption.value match {
              case read: xmlTypes.ReadRequestType => parseRead(read, parseTTL(envelope.ttl))
              case write: xmlTypes.WriteRequestType => parseWrite(write, parseTTL(envelope.ttl))
              case delete: xmlTypes.DeleteRequestType => parseDelete(delete, parseTTL(envelope.ttl))
              case call: xmlTypes.CallRequestType => parseCall(call, parseTTL(envelope.ttl))
              case cancel: xmlTypes.CancelRequestType => parseCancel(cancel, parseTTL(envelope.ttl))
              case response: xmlTypes.ResponseListType => parseResponse(response, parseTTL(envelope.ttl))
              case _ => throw new Exception("Unknown request type returned by scalaxb")
            }
          } match {
            case Success(res) => res
            case Failure(e: ParseError) =>
              Left(Vector(e))
            case Failure(e) =>
              //println( s"Exception: $e\nStackTrace:\n")
              e.printStackTrace()
              Left(Vector(OMIParserError(e.getMessage)))
          }
      }
  }

  def parseInterval(v: Double): Duration =
    v match {
      case -1.0 => -1.seconds
      case -2.0 => -2.seconds
      case w if w >= 0 => w.seconds
      case _ => throw new IllegalArgumentException("Illegal interval, only positive or -1 and -2  are allowed.")
    } // fixes problem with duration: -1.0.seconds == -999999999 nanoseconds

  def parseInterval(v: String): Duration =
    Try(v.toDouble) match {
      case Success(d) => parseInterval(d)
      case _ => throw new IllegalArgumentException("Could not parse Interval as Double")
    } // fixes problem with duration: -1.0.seconds == -999999999 nanoseconds

  def parseTTL(v: Double): Duration =
    v match {
      case -1.0 => Duration.Inf
      case 0.0 => Duration.Inf
      case w if w > 0 => w.seconds
      case _ => throw new IllegalArgumentException("Negative Interval, diffrent than -1 isn't allowed.")
    }

  def parseTTL(v: String): Duration =
    Try(v.toDouble) match {
      case Success(d) => parseTTL(d)
      case _ => throw new IllegalArgumentException("Could not parse TTL")
    }


  def parseRequestID(id: xmlTypes.IdType): Long = id.value.trim.toLong

  def parseRequestID(id: String): Long = id.trim.toLong //ID might not be long!

  private[this] def parseRead(read: xmlTypes.ReadRequestType, ttl: Duration): OmiParseResult = {
    val callback = read.callback.map { addr => RawCallback(addr.toString) }
    if (read.requestID.nonEmpty) {
      Right(Vector(
        PollRequest(
          callback,
          OdfCollection(read.requestID.map(parseRequestID): _*),
          ttl
        )))
    } else {
      read.msg match {
        case Some(msg) => {
          val odfParseResult = parseMsg(msg, read.msgformat)
          odfParseResult match {
            case Left(errors)  => Left(errors.asScala.toVector)
            case Right(odf) =>
              read.interval match {
                case None =>
                  if (read.newest.nonEmpty && read.oldest.nonEmpty)
                    Left(Vector(OMIParserError("Invalid Read request, Can not query oldest and newest values at same time.")))
                  else
                    Right(Vector(
                                    ReadRequest(
                                                 odf,
                                                 gcalendarToTimestampOption(read.begin),
                                                 gcalendarToTimestampOption(read.end),
                                                 read.newest.map(_.toInt),
                                                 read.oldest.map(_.toInt),
                                                 None,
                                                 callback,
                                                 ttl
                                               )
                                  ))
                case Some(interval) =>
                  if (read.newest.nonEmpty && read.oldest.nonEmpty)
                    Left(Vector(OMIParserError("Invalid Read request, Can not query oldest and newest values at same time.")))
                  else
                    Right(Vector(
                                    SubscriptionRequest(
                                                         parseInterval(interval),
                                                         odf,
                                                         read.newest.map(_.toInt),
                                                         read.oldest.map(_.toInt),
                                                         callback,
                                                         ttl
                                                       )
                                  ))
              }
          }
        }
        case None => {
          Left(
            Vector(
              OMIParserError("Invalid Read request, needs either of \"omi:msg\" or \"omi:requestID\" elements.")
            )
          )
        }
      }
    }
  }

  private[this] def parseWrite(write: xmlTypes.WriteRequestType, ttl: Duration): OmiParseResult = {
    write.msg match {
      case None =>
        Left(Vector(OMIParserError("Write request without msg.")))
      case Some(msg: xmlTypes.MsgType) =>
        val odfParseResult = parseMsg(msg, write.msgformat)
        val callback = write.callback.map { addr => RawCallback(addr.toString) }
        odfParseResult match {
          case Left(errors)  => Left(errors.asScala.toVector)
          case Right(odf) =>
            Right(Vector(
              WriteRequest(
                odf,
                callback,
                ttl
              )
            ))
        }
    }
  }

  private[this] def parseCall(call: xmlTypes.CallRequestType, ttl: Duration): OmiParseResult = {
    call.msg match {
      case None =>
        Left(Vector(OMIParserError("Call request without msg.")))
      case Some(msg: xmlTypes.MsgType) =>
        val odfParseResult = parseMsg(msg, call.msgformat)
        val callback = call.callback.map { addr => RawCallback(addr.toString) }
        odfParseResult match {
          case Left(errors)  => Left(errors.asScala.toVector)
          case Right(odf) =>
            Right(Vector(
              CallRequest(
                odf,
                callback,
                ttl
              )
            ))
        }
    }
  }

  private[this] def parseDelete(delete: xmlTypes.DeleteRequestType, ttl: Duration): OmiParseResult = {
    delete.msg match {
      case None =>
        Left(Vector(OMIParserError("Delete request without msg.")))
      case Some(msg: xmlTypes.MsgType) =>
        val odfParseResult = parseMsg(msg, delete.msgformat)
        val callback = delete.callback.map { addr => RawCallback(addr.toString) }
        odfParseResult match {
          case Left(errors)  => Left(errors.asScala.toVector)
          case Right(odf) =>
            Right(Vector(
              DeleteRequest(
                odf,
                callback,
                ttl
              )
            ))
        }
    }
  }

  private[this] def parseCancel(cancel: xmlTypes.CancelRequestType, ttl: Duration): OmiParseResult = {
    Right(Vector(
      CancelRequest(
        OdfCollection(cancel.requestID.map(parseRequestID): _*),
        ttl
      )
    ))
  }

  private[this] def parseResponse(response: xmlTypes.ResponseListType, ttl: Duration): OmiParseResult = Try {
    Vector(
      ResponseRequest(
        OdfCollection(response.result.map {
          result =>
            OmiResult(
              OmiReturn(
                result.returnValue.returnCode,
                result.returnValue.description
              ),
            OdfCollection( result.requestID.map(parseRequestID) : _* ),
            result.msg.map {
              msg: xmlGen.xmlTypes.MsgType =>
                //TODO: figure right type parameter
                val odfParseResult = parseMsg(msg, result.msgformat)
                odfParseResult match {
                  case Left(errors) => throw combineErrors(errors.asScala.toVector)
                  case Right(odf) => odf
                }
            }
            )
        }: _*)
        , ttl)
    )
  } match {
    case Success(requests: Iterable[OmiRequest]) => Right(requests)
    case Failure(error: ParseError) => Left(Vector(error))
    case Failure(t) => throw t
  }

  private[this] def parseMsg(msg: xmlGen.xmlTypes.MsgType, format: Option[String]): OdfParseResult = {
    if (msg.mixed.isEmpty)
      Left(Vector(OMIParserError("Empty msg element.")).asJava)
    else {
      val xmlMsg = xmlGen.scalaxb.toXML[xmlGen.xmlTypes.MsgType](msg, Some("omi.xsd"), Some("msg"), xmlGen.defaultScope)

      /*
        msg.mixed.map{
        case dr: xmlGen.scalaxb.DataRecord[_] => 
          xmlGen.scalaxb.DataRecord.toXML(dr,None,Some(),xmlGen.defaultScope,false)
      }.foldLeft(NodeSeq.Empty){
        case (res: NodeSeq, ns: NodeSeq) => res ++ ns
      }*/
      val hO = (xmlMsg \ "Objects").headOption
      (format, hO) match {
        case (Some("odf"), Some(objects)) =>
          parseOdf(objects)
        case (Some("odf.xsd"), Some(objects)) =>
          parseOdf(objects)
        case (Some("odf"), None) => 
          Left(Vector(OMIParserError("No Objects found in msg.")).asJava)
        case (Some("odf.xsd"), None) => 
          Left(Vector(OMIParserError("No Objects found in msg.")).asJava)
        case (None,_) =>  Left(Vector(OMIParserError("Empty msg element.")).asJava)
        case (Some(str),_) =>  Left(Vector(OMIParserError("Unknown format for msg.")).asJava)
      }
    }
  }

  private[this] def parseOdf(node: Node): OdfParseResult = ODFParser.parse(node)

  def gcalendarToTimestampOption(gcal: Option[javax.xml.datatype.XMLGregorianCalendar]): Option[Timestamp] = gcal match {
    case None => None
    case Some(cal) => Some(new Timestamp(cal.toGregorianCalendar().getTimeInMillis));
  }

  def uriToStringOption(opt: Option[java.net.URI]): Option[String] = opt map {
    uri => uri.toString
  }
}


