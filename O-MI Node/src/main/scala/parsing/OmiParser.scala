package parsing

import types._
import types.OmiTypes._
import types.OdfTypes._
import xmlGen.xmlTypes
import java.sql.Timestamp
import java.text.SimpleDateFormat
import scala.collection.mutable.Map
import scala.concurrent.duration._

import scala.xml._
import scala.util.{Try, Success, Failure}
import javax.xml.transform.stream.StreamSource
import scala.xml.Utility.trim
import org.xml.sax.SAXException

import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser extends Parser[OmiParseResult] {

   protected override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("omi.xsd"))

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
    ).getOrElse(
        return Left(Iterable(ParseError("OmiParser: Invalid XML")))
    )

    val schema_err = schemaValitation(root)
    if (schema_err.nonEmpty)
      return Left(schema_err.map { pe: ParseError => ParseError("OmiParser: " + pe.msg) })

    Try{
      val envelope = xmlGen.scalaxb.fromXML[xmlTypes.OmiEnvelope](root)
      envelope.omienvelopeoption.value match {
        case read: xmlTypes.ReadRequest => parseRead(read, parseTTL(envelope.ttl))
        case write: xmlTypes.WriteRequest => parseWrite(write, parseTTL(envelope.ttl))
        case cancel: xmlTypes.CancelRequest => parseCancel(cancel, parseTTL(envelope.ttl))
        case response: xmlTypes.ResponseListType => parseResponse(response, parseTTL(envelope.ttl))
      }
    } match {
      case Success(res) => res
      case Failure(e) => 
        return Left( Iterable( ParseError(e + " thrown when parsed.") ) )
    }
  }

  // fixes problem with duration: -1.0.seconds == -999999999 nanoseconds
  def parseInterval(v: Double) =
    if (v == -1.0) -1.seconds
    else if (v > 0) v.seconds // or: Math.round(v * 1000).milliseconds
    else throw new IllegalArgumentException("Negative Interval, diffrent than -1 isn't allowed.")
  def parseTTL(v: Double)      =
    if (v == -1.0 || v == 0.0) Duration.Inf
    else if (v > 0) v.seconds
    else throw new IllegalArgumentException("Negative TTL, diffrent than -1 isn't allowed.")

  
  private def parseRead(read: xmlTypes.ReadRequest, ttl: Duration): OmiParseResult = {
    if (read.requestID.nonEmpty) {
      Right(Iterable(
        PollRequest(
          ttl,
          uriToStringOption(read.callback),
          read.requestID.map { id => id.value.toInt })))
    } else if( read.msg.nonEmpty ){
      val odf = parseMsg(read.msg, read.msgformat)
      val errors = OdfTypes.getErrors(odf)

      if (errors.nonEmpty)
        return Left(errors)

      read.interval match {
        case None =>
          Right(Iterable(
            ReadRequest(
              ttl,
              odf.right.get,
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
              odf.right.get,
              read.newest,
              read.oldest,
              uriToStringOption(read.callback)
            )
          ))
      }
    } else {
      Left(
        Iterable(
          ParseError("Invalid Read request need either of \"omi:msg\" or \"omi:requestID\" nodes.")
        )
      )
    }
  }

  private def parseWrite(write: xmlTypes.WriteRequest, ttl: Duration): OmiParseResult = {
    val odf = parseMsg(write.msg, write.msgformat)
    val errors = OdfTypes.getErrors(odf)

    if (errors.nonEmpty)
      return Left(errors)
    else
      Right(Iterable(
        WriteRequest(
          ttl,
          odf.right.get,
          uriToStringOption(write.callback))))
  }

  private def parseCancel(cancel: xmlTypes.CancelRequest, ttl: Duration): OmiParseResult = {
    Right(Iterable(
      CancelRequest(
        ttl,
        cancel.requestID.map { id => id.value.toInt }.toIterable
      )
    ))
  }
  private def parseResponse(response: xmlTypes.ResponseListType, ttl: Duration): OmiParseResult = {
    Right(Iterable(
      ResponseRequest(
        response.result.map {
          case result =>

            OmiResult(
              result.returnValue.value,
              result.returnValue.returnCode,
              result.returnValue.description,
              if (result.requestID.nonEmpty) {
                asJavaIterable(Iterable(result.requestID.get.value.toInt))
              } else {
                asJavaIterable(Iterable.empty[Int])
              },
              if (result.msg.isEmpty)
                None
              else {
                val odf = parseMsg(result.msg, result.msgformat)
                val errors = OdfTypes.getErrors(odf)
                if (errors.nonEmpty)
                  return Left(errors)
                else
                  Some(odf.right.get)
              })
        }.toIterable
      )
    ))
  }

  private def parseMsg(msg: Option[xmlGen.scalaxb.DataRecord[Any]], format: Option[String]): OdfParseResult = {
    if (msg.isEmpty)
      return Left(Iterable(ParseError("OmiParser: No msg element found in write request.")))
    
    if (format.isEmpty) 
      return Left(Iterable(ParseError("OmiParser: Missing msgformat attribute.")))

    val data = msg.get.as[Elem]
    format.get match {
      case "odf" =>
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
  private def parseOdf(node: Node): OdfParseResult = OdfParser.parse(node)
  private def gcalendarToTimestampOption(gcal: Option[javax.xml.datatype.XMLGregorianCalendar]): Option[Timestamp] = gcal match {
    case None => None
    case Some(cal) => Some(new Timestamp(cal.toGregorianCalendar().getTimeInMillis()));
  }
  private def uriToStringOption(opt: Option[java.net.URI]): Option[String] = opt match {
    case None => None
    case Some(uri) => Some(uri.toString)
  }
}


