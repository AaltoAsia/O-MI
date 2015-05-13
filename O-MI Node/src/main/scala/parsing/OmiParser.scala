package parsing

import Types._
import Types.OmiTypes._
import Types.OdfTypes._
import java.sql.Timestamp
import scala.xml._
import scala.util.Try
import scala.collection.mutable.Map
import java.text.SimpleDateFormat
import javax.xml.transform.stream.StreamSource
import scala.xml.Utility.trim
import org.xml.sax.SAXException
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList

/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser extends Parser[OmiParseResult] {

  override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("omi.xsd"))

  /**
    * Parse the given XML string into sequence of ParseMsg classes
    *
    * @param xml_msg O-MI formatted message that is to be parsed
    * @return sequence of ParseMsg classes, different message types are defined in
    *         the TypeClasses.scala file
    */
  def parse(xml_msg: String): OmiParseResult = {
    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val root = Try(
      XML.loadString(xml_msg)
    ).getOrElse(
      return Left( Iterable( ParseError("OmiParser: Invalid XML" ) ) ) 
    )
    val schema_err = schemaValitation(root)
    if (schema_err.nonEmpty)
      return Left( schema_err.map{pe : ParseError => ParseError("OmiParser: "+ pe.msg)} ) 

    val envelope = xmlGen.scalaxb.fromXML[xmlGen.OmiEnvelope](root)  
    envelope.omienvelopeoption.value match {
      case read: xmlGen.ReadRequest => parseRead(read, envelope.ttl )
      case write: xmlGen.WriteRequest => parseWrite(write, envelope.ttl)
      case cancel: xmlGen.CancelRequest => parseCancel(cancel, envelope.ttl)
      case response: xmlGen.ResponseListType =>  parseResponse(response, envelope.ttl)
    }
  }
  private def parseRead(read: xmlGen.ReadRequest, ttl: Double )  : OmiParseResult = {
    if( read.msg.isEmpty ) {
      Right( Iterable( 
        PollRequest(
          ttl,
          uriToStringOption(read.callback),
          read.requestId.map{id => id.value.toInt}
        )
      ) )
    } else {
      val odf = parseMsg(read.msg, read.msgformat)
      val errors = OdfTypes.getErrors(odf) 

      if(errors.nonEmpty)
        return Left( errors.map{pe : ParseError => ParseError("OmiParser: "+ pe.msg)} ) 

      if(read.interval.isEmpty){
        Right( Iterable( 
          ReadRequest( 
            ttl,
            odf.right.get,
            gcalendarToTimestampOption(read.begin),
            gcalendarToTimestampOption(read.end),
            read.newest,
            read.oldest,
            uriToStringOption(read.callback)
          )
        ) ) 
      } else {
        Right( Iterable( 
          SubscriptionRequest( 
            ttl,
            read.interval.get,
            odf.right.get,
            read.newest,
            read.oldest,
            uriToStringOption(read.callback)
          )
        ) )
      } 
    }
  }

  private def parseWrite(write: xmlGen.WriteRequest, ttl: Double) : OmiParseResult  = {
    val odf = parseMsg(write.msg, write.msgformat)
    val errors = OdfTypes.getErrors(odf) 

    if(errors.nonEmpty)
      return Left( errors.map{pe : ParseError => ParseError("OmiParser: "+ pe.msg)} ) 
    else
      Right( Iterable( 
        WriteRequest(
          ttl,
          odf.right.get,
          uriToStringOption(write.callback)
        )
      ) )
  } 

  private def parseCancel(cancel: xmlGen.CancelRequest , ttl: Double)  : OmiParseResult = { 
    Right( Iterable( 
      CancelRequest(
        ttl,
        cancel.requestId.map{id => id.value.toInt }
      )
    ) )
  }
  private def parseResponse(response: xmlGen.ResponseListType, ttl: Double) : OmiParseResult = {
    Right( Iterable(
      ResponseRequest(
        response.result.map{
          case result => 
            val odf = parseMsg(result.msg, result.msgformat)
            val errors = OdfTypes.getErrors(odf) 

          if(errors.nonEmpty)
            return Left( errors.map{pe : ParseError => ParseError("OmiParser: "+ pe.msg)} ) 
          
          else
            OmiResult(
              result.returnValue.value,
              result.returnValue.returnCode,
              result.returnValue.description,
              if(result.requestId.nonEmpty){
                Iterable(result.requestId.get.value.toInt )
              } else {
                seqAsJavaList(Seq.empty)
              } ,
              Some(odf.right.get)
          )
        }
      )
    ) )
  }


    private def parseMsg(msg: Option[xmlGen.scalaxb.DataRecord[Any]], format: Option[String]) : OdfParseResult = {
      if(msg.isEmpty)
        return Left( Iterable( ParseError("OmiParser: No msg element found in write request.")))
      if(format.isEmpty) return Left( Iterable( ParseError("OmiParser: Missing msgformat attribute.")))

      val data = msg.get.as[Elem] 
      format.get match {
        case "odf" => 
          val odf = (data \ "Objects")
          if(odf.nonEmpty)
            parseOdf(odf.head)
          else 
            Left(Iterable(ParseError("No Objects child found in msg.")))
        case _ => return Left( Iterable( ParseError("OmiParser: Unknown msgformat attribute")  ))
      } 
    }
    private def parseOdf(node: Node) : OdfParseResult = OdfParser.parse(node) 
    private def gcalendarToTimestampOption(gcal: Option[javax.xml.datatype.XMLGregorianCalendar]) : Option[Timestamp] = gcal match {
      case None => None
      case Some(cal) => Some( new Timestamp(cal.toGregorianCalendar().getTimeInMillis()));
    } 
    private def uriToStringOption(opt: Option[java.net.URI]) : Option[String] = opt match {
      case None => None
      case Some(uri) => Some( uri.toString )
    } 
}


