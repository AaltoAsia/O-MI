package parsing

import sensorDataStructure._
import scala.xml._

object OdfParser extends Parser{
  def parse( xml_msg: String): Seq[ ParseResult ]  = {
    val root = XML.loadString( xml_msg ) 
    if(root.label != "Objects")
      Seq( ParseError( "ODF doesn't have Objects as root.") )

    ( root \ "Object" ).flatMap( obj =>{
     parseNode( obj, "/Objects")
    } )
  }

  trait ODFNodeType
  case class Object() extends ODFNodeType 
  case class Infoitem() extends ODFNodeType   
  case class MetaData() extends ODFNodeType   

  case class ODFNode( path: String, nodeType: ODFNodeType, value: Option[String], time: Option[String], metadata: Option[String])

  type ParseResult = Either[ ParseError, ODFNode ]
  
  private def parseNode( obj: Node, currentPath: String ) : Seq[ ParseResult ] = {
    obj.label match {
      case "Object" => {
        val id = (obj \ "id").headOption.getOrElse( 
          return Seq( Left( ParseError( "No id for Object." ) ) ) 
        ) 
        val path = currentPath + "/" + id.text 
        val subobjs = obj \ "Object"
        val infoitems = obj \ "InfoItem"
        if( infoitems.isEmpty && subobjs.isEmpty ){
          Seq( Right( new ODFNode( path, new Object, None, None, None) ) )
        } else {
          val eithers : Seq[ ParseResult ] = 
            subobjs.flatMap{
              sub: Node => parseNode( sub, path)
          }
          eithers ++ infoitems.flatMap{ item: Node =>
              parseNode( item, path ) 
            }
        }
      }
      case "InfoItem" => {
        val name = (obj \ "@name").headOption.getOrElse( 
          return Seq( Left( ParseError( "No name for InfoItem." ) ) ) 
        ) 
        val path = currentPath + "/" + name.text
        val values = ( obj \ "value" ).headOption match {
          case Some(node : Node)  => {
            Some(node.text)
          }
          case None => None
        }
        val time = (obj \ "value").headOption match{
          case None => None
          case Some(v) => ( v \ "@dateTime" ).headOption match {
              case Some(t) => Some("dateTime=\""+t.text+"\"")
              case None => (v \ "@unixTime").headOption match { 
                case Some(u) => Some("unixtime=\""+u.text+"\"") 
                case None => None
              } 
            }
        }

        val metadata = ( obj \ "MetaData" ).headOption match {
          case Some( node : Node)   => {
            Some(node.text)
          }
          case None => None
        }
        Seq( Right( new ODFNode( path, new Infoitem, values, time, metadata) ) )    
      }
      case _ => Seq( Left( new ParseError( "Unknown node in O-DF. " + currentPath ) ) )
    }
  }
}


