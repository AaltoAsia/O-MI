/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package parsing

import types._
import OmiTypes._
import OdfTypes._

import xmlGen.xmlTypes
import java.sql.Timestamp
import java.io.File

import scala.concurrent.duration._

import scala.xml._
import scala.util.{Try, Success, Failure}
import javax.xml.transform.stream.StreamSource

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}

/** Parser for messages with O-MI protocol*/
object OmiParser extends Parser[OmiParseResult] {

   protected[this] override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("omi.xsd"))

  /**
   * Public method for parsing the xml file into OmiParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-MI format.
   *  @return OmiParseResults
   */
  def parse(file: File): OmiParseResult = {
    val root = Try(
      XML.loadFile(file)
    ) match {
      case Success(s) => s
      case Failure(f) => return Left( Iterable( ParseError(s"OmiParser: Invalid XML: ${f.getMessage}")))
    }

   parse( root )
  }


  /**
   * Public method for parsing the xml string into OmiParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-MI format.
   *  @return OmiParseResults
   */
  def parse(xml_msg: String): OmiParseResult = {
    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val root = Try(
      XML.loadString(xml_msg)
    ) match {
      case Success(s) => s
      case Failure(f) => return Left( Iterable( ParseError(s"OmiParser: Invalid XML: ${f.getMessage}")))
    }

    parse( root )
  }

  /**
   * Public method for parsing the xml root node into OmiParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-MI format.
   *  @return OmiParseResults
   */
  def parse(root: xml.Node ): OmiParseResult = {
    val schema_err = schemaValidation(root)
    if (schema_err.nonEmpty)
      return Left(schema_err.map { pe: ParseError => ParseError("OmiParser: " + pe.msg) })
    Try{
      val envelope = xmlGen.scalaxb.fromXML[xmlTypes.OmiEnvelope](root)

      // Try to recognize unsupported features
      envelope.omienvelopeoption.value match {
        case request: xmlTypes.RequestBaseTypable if request.nodeList.isDefined =>
          throw new NotImplementedError("nodeList attribute functionality is not supported")
        case _ => //noop
      }

      envelope.omienvelopeoption.value match {
        case read: xmlTypes.ReadRequest => parseRead(read, parseTTL(envelope.ttl))
        case write: xmlTypes.WriteRequest => parseWrite(write, parseTTL(envelope.ttl))
        case cancel: xmlTypes.CancelRequest => parseCancel(cancel, parseTTL(envelope.ttl))
        case response: xmlTypes.ResponseListType => parseResponse(response, parseTTL(envelope.ttl))
        case _ => throw new Exception("Unknown request type returned by scalaxb")
      }
    } match {
      case Success(res) => res
      case Failure(e) => 
        return Left( Iterable( ParseError(e + " thrown when parsed.") ) )
      case _ => throw new Exception("Unknown end state from OmiParser.")
    }
  }

  // fixes problem with duration: -1.0.seconds == -999999999 nanoseconds
  def parseInterval(v: Double) =
  v match{
    case -1.0 =>  -1.seconds
    case w if w > 0 => w.seconds
    case _ => throw new IllegalArgumentException("Negative Interval, diffrent than -1 isn't allowed.")
  }
  def parseTTL(v: Double)      =
  v match{
    case -1.0 => Duration.Inf
    case 0.0 => Duration.Inf
    case w if w > 0 => w.seconds
    case _ => throw new IllegalArgumentException("Negative Interval, diffrent than -1 isn't allowed.")
  }

  private[this] def parseRequestID(id: xmlTypes.IdType): Long = id.value.trim.toLong
  
  private[this] def parseRead(read: xmlTypes.ReadRequest, ttl: Duration): OmiParseResult = 
    read.requestID.nonEmpty match {
      case true =>
        Right(Iterable(
          PollRequest(
            ttl,
            uriToStringOption(read.callback),
            read.requestID map parseRequestID
          )))
        case false =>
          read.msg match {
            case Some(msg) =>
              val odfParseResult = parseMsg(read.msg, read.msgformat)
              odfParseResult match {
                case Left(errors)  => Left(errors)
                case Right(odf) => 
                  read.interval match {
                    case None =>
                      Right(Iterable(
                        ReadRequest(
                          ttl,
                          odf,
                          gcalendarToTimestampOption(read.begin),
                          gcalendarToTimestampOption(read.end),
                          read.newest,
                          read.oldest,
                          uriToStringOption(read.callback)
                        )
                      ))
                    case Some(interval) =>
                      Right(Iterable(
                        SubscriptionRequest(
                          ttl,
                          parseInterval(interval),
                          odf,
                          read.newest,
                          read.oldest,
                          uriToStringOption(read.callback)
                        )
                      ))
                  }
              }

            case None =>
              Left(
                Iterable(
                  ParseError("Invalid Read request need either of \"omi:msg\" or \"omi:requestID\" nodes.")
                )
              )
          }
    }

  private[this] def parseWrite(write: xmlTypes.WriteRequest, ttl: Duration): OmiParseResult = {
    val odfParseResult = parseMsg(write.msg, write.msgformat)
    odfParseResult match {
      case Left(errors)  => Left(errors)
      case Right(odf) =>
      Right(Iterable(
        WriteRequest(
          ttl,
          odf,
          uriToStringOption(write.callback))))
      }
  }

  private[this] def parseCancel(cancel: xmlTypes.CancelRequest, ttl: Duration): OmiParseResult = {
    Right(Iterable(
      CancelRequest(
        ttl,
        cancel.requestID.map(parseRequestID).toIterable
      )
    ))
  }
  private[this] def parseResponse(response: xmlTypes.ResponseListType, ttl: Duration): OmiParseResult = {
    Right(Iterable(
      ResponseRequest(
        response.result.map {
          case result =>
            OmiResult(
              result.returnValue.value,
              result.returnValue.returnCode,
              result.returnValue.description,
              result.requestID.map(parseRequestID).toIterable,
              if (result.msg.isEmpty)
                None
              else {
                val odfParseResult = parseMsg(result.msg, result.msgformat)
                odfParseResult match {
                  case Left(errors)  => return Left(errors)
                  case Right(odf) => Some(odf)
                }
              }
          )
        }.toIterable
      , ttl)
    ))
  }

  private[this] def parseMsg(msgO: Option[xmlGen.scalaxb.DataRecord[Any]], format: Option[String]): OdfParseResult = msgO match{
      case None =>
        Left(Iterable(ParseError("OmiParser: No msg element found in write request.")))
      case Some(msg) =>
    if (format.isEmpty) 
      return Left(Iterable(ParseError("OmiParser: Missing msgformat attribute.")))

    val data = msg.as[Elem]
    format match {
      case Some("odf") =>
        val odf = (data \ "Objects")
        odf.headOption match {
          case Some(head) =>
            parseOdf(head)
          case None =>
            Left(Iterable(ParseError("No Objects child found in msg.")))
        }
      case _ =>
        Left(Iterable(ParseError("Unknown msgformat attribute")))
    }
  }
  private[this] def parseOdf(node: Node): OdfParseResult = OdfParser.parse(node)
  private[this] def gcalendarToTimestampOption(gcal: Option[javax.xml.datatype.XMLGregorianCalendar]): Option[Timestamp] = gcal match {
    case None => None
    case Some(cal) => Some(new Timestamp(cal.toGregorianCalendar().getTimeInMillis()));
  }
  private[this] def uriToStringOption(opt: Option[java.net.URI]): Option[String] = opt match {
    case None => None
    case Some(uri) => Some(uri.toString)
  }
}


