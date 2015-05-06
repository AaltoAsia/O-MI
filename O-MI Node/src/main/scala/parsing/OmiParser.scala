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
    val schema_err = schemaValitation(xml_msg)
    if (schema_err.nonEmpty)
      return Seq( ParseError( schema_err.map{err => err.msg}.mkString("\n") )) 

    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(new ParseError("Invalid XML")))
    val envelope = scalaxb.fromXML[OmiEnvelope](root)  
    envelope.omienvelopeoption.value match {
      case read: ReadRequest => {
        Seq( 
          try { 
            if( read.msg.isEmpty ) {
              new PollRequest(
                envelope.ttl,
                read.callback match {
                  case None => None
                  case Some(uri) => Some(uri.toString)
                },
                read.requestId.map{id => id.value.toInt }
              )

          } else if(read.interval.isEmpty){
            OneTimeRead( 
              envelope.ttl,
              parseMsg(read.msg, read.msgformat),
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
        } else {
          Subscription( 
            envelope.ttl,
            read.interval.get,
            parseMsg(read.msg, read.msgformat),
            read.callback match{
              case None => None
              case Some(uri) => Some(uri.toString)
            }
          )
      }
    } catch {
      case pe : ParseError => pe
    }
  )
      }
      case write: WriteRequest => {
        Seq( 
          try {
            Write(
              envelope.ttl,
              parseMsg(write.msg, write.msgformat),
              write.callback match {
                case None => None
                case Some(uri) => Some(uri.toString)
              }
            )
        } catch {
          case pe : ParseError => pe
        }
      )
  }
  case cancel: CancelRequest => { 
    Seq( 
      Cancel(
        envelope.ttl,
        cancel.requestId.map{id => id.value.toString }.toSeq
      )
  )
      }
      case response: ResponseListType => {
        response.result.map{
          case result => 
          try {
            Result(
              result.returnValue.value,
              result.returnValue.returnCode,
              Some(parseMsg(result.msg, result.msgformat)),
              result.requestId.map{id => id.value }.toSeq
            )
        } catch {
          case pe : ParseError => pe
        }
      }
    }
  }
}

private def parseMsg(msg: Option[scalaxb.DataRecord[Any]], format: Option[String]) : Seq[OdfObject]= {
  if(msg.isEmpty)
    throw new ParseError("No msg element found in write request.")

  format.get match {
    case "omi.xsd" => parseOdf(msg.get.as[Elem])
    case _ => throw new ParseError("Unknown msgformat attribute or not found for msg.")  
  } 
}
private def parseOdf(node: Node) : Seq[OdfObject] = OdfParser.parse(node.toString) 
}


