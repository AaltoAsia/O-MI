package parsing

import sensorDataStructure._
import scala.xml._

object OmiParser extends Parser{
  private def parseODF(msg:Node) = OdfParser.parse( new PrettyPrinter( 80, 2 ).format( msg ) )
  def parse(xml_msg: String): Option[Seq[ParseMsg]]={
    val root = XML.loadString( xml_msg )
    if( root.label != "omi:omiEnvelope" )
      Some( new ParseError( "XML's root isn't omi:Envelope" ) )
    val request = root.head
    val ttl = ( root \ "@ttl" ).headOption.getOrElse{
      return Some( Seq( new ParseError("No ttl present in O-MI Envelope" ) ) ) 
    }.text
    request.label match {
      case "omi:write"  => {
        ( request\"@msgformat" ).text match{ 
          case "odf" => {
            val odf = parseODF( ( request \ "{omi}msg" ).head )
            errorsOrOdf( ttl, odf )( ( _ttl, nodes ) => SensorWrite( _ttl, nodes ) )
          }
          case _ => Some( new ParseError( "Unknown message format." ) )
        }        
      } 
      case "omi:read"  => {
        if( !( request\"@interval" ).headOption.isEmpty )
          Some( new ParseError( "Unimplemented O-MI part." ) )
        else
        ( request\"@msgformat" ).text match{ 
          case "odf" => {
            val odf = parseODF( ( request \ "{omi}msg" ).head )
            errorsOrOdf( ttl, odf )( ( _ttl, nodes ) => OneTimeRead( _ttl, nodes ) )
          }
          case _ => Some( new ParseError( "Unknown message format." ) )
        }
      }
      case "omi:cancel"  => Some( new ParseError( "Unimplemented O-MI node." ) ) 
      case "omi:response"  => Some( new ParseError( "Unimplemented O-MI node." ) ) 
    }
    None
  }

  private def errorsOrOdf(
                  ttl:String,
                  odf:Seq[ OdfParser.ParseResult ]) 
                  (f:(String,Seq[OdfParser.ODFNode]) => ParseMsg)
                  : Option[ Seq[ ParseMsg ] ] = {
    val errors = odf.filter(_.isLeft)
    if( errors.isEmpty ) 
      Some( Seq( f(ttl, odf.filter( _.isRight ).map( _.right.get ) ) ) )
    else
      Some( errors.map( _.left.get ) ) 
  }

}

abstract sealed trait ParseMsg
case class ParseError(msg:String) extends ParseMsg
case class OneTimeRead(ttl:String , sensors: Seq[OdfParser.ODFNode]) extends ParseMsg
case class SensorWrite(ttl:String , sensors: Seq[OdfParser.ODFNode]) extends ParseMsg

