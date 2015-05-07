package parsing

import parsing.Types._
import java.sql.Timestamp
import scala.xml._
import scala.util.Try
import scala.collection.mutable.Map
import java.text.SimpleDateFormat
import javax.xml.transform.stream.StreamSource
import scala.xml.Utility.trim
import org.xml.sax.SAXException
/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser extends Parser[ParseMsg] {
  private val implementedRequest = Seq("read", "write", "cancel", "response")
  private def dateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss")

  override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("omi.xsd"))

  /**
    * Parse the given XML string into sequence of ParseMsg classes
    *
    * @param xml_msg O-MI formatted message that is to be parsed
    * @return sequence of ParseMsg classes, different message types are defined in
    *         the TypeClasses.scala file
    */
  def parse(xml_msg: String): Seq[ParseMsg] = {
    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(new ParseError("Invalid XML")))
    val schema_err = schemaValitation(root)
    if (schema_err.nonEmpty)
      return Seq( ParseError( schema_err.map{err => err.msg}.mkString("\n") )) 

    val envelope = scalaxb.fromXML[OmiEnvelope](root)  
    envelope.omienvelopeoption.value match {
      case read: ReadRequest => parseRead(read, envelope.ttl )
      case write: WriteRequest => parseWrite(write, envelope.ttl)
      case cancel: CancelRequest => parseCancel(cancel, envelope.ttl)
      case response: ResponseListType =>  parseResponse(response, envelope.ttl)
    }
  }
  private def parseRead(read: ReadRequest, ttl: Double )  : Seq[ParseMsg] = {
    if( read.msg.isEmpty ) {
      Seq( 
        OneTimeRead(
          ttl,
          Seq.empty,
          callback = read.callback match {
            case None => None
            case Some(uri) => Some(uri.toString)
          },
          requestId = read.requestId.map{id => id.value}
        )
      )
    } else {
      val odf = parseMsg(read.msg, read.msgformat)
      val errors = getErrors(odf) 

      if(errors.nonEmpty)
        return errors

      if(read.interval.isEmpty){
        Seq( 
          OneTimeRead( 
            ttl,
            getObjects(odf),
            read.begin match {
              case None => None
              case Some(cal) => Some( new Timestamp(cal.toGregorianCalendar().getTimeInMillis()));
            },
            read.end match {
              case None => None
              case Some(cal) => Some( new Timestamp(cal.toGregorianCalendar().getTimeInMillis()));
            },
            read.newest,
            read.oldest,
            read.callback match {
              case None => None
              case Some(uri) => Some(uri.toString)
            },
            read.requestId.map{id => id.value.toString }.toSeq
          )
        ) 
      } else {
        Seq( 
          Subscription( 
            ttl,
            read.interval.get,
            getObjects(odf),
            read.callback match{
              case None => None
              case Some(uri) => Some(uri.toString)
            }
          )
        )
      } 
    }
  }

  private def parseWrite(write: WriteRequest, ttl: Double) : Seq[ParseMsg]  = {
    val odf = parseMsg(write.msg, write.msgformat)
    val errors = getErrors(odf) 

    if(errors.nonEmpty)
      return errors
    else
      Seq( 
      Write(
        ttl,
        getObjects(odf),
        write.callback match {
          case None => None
          case Some(uri) => Some(uri.toString)
        }
      )
  )
  } 

  private def parseCancel(cancel: CancelRequest , ttl: Double)  : Seq[ParseMsg] = { 
    Seq( 
      Cancel(
        ttl,
        cancel.requestId.map{id => id.value.toString }.toSeq
      )
    )
  }
  private def parseResponse(response: ResponseListType, ttl: Double) : Seq[ParseMsg] = {
    response.result.map{
      case result => 
      val odf = parseMsg(result.msg, result.msgformat)
      val errors = getErrors(odf) 

      if(errors.nonEmpty)
        return errors
      else
        Result(
        result.returnValue.value,
        result.returnValue.returnCode,
        Some(getObjects(odf)),
        result.requestId.map{id => id.value }.toSeq
      )
    }
  }


    private def parseMsg(msg: Option[scalaxb.DataRecord[Any]], format: Option[String]) : Seq[OdfParseResult] = {
      if(msg.isEmpty)
        throw new ParseError("No msg element found in write request.")

      format.get match {
        case "omi.xsd" => parseOdf(msg.get.as[Elem])
        case _ => throw new ParseError("Unknown msgformat attribute or not found for msg.")  
      } 
    }
    private def parseOdf(node: Node) : Seq[OdfParseResult]  = OdfParser.parse(node.toString) 
  }


