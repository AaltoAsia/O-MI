package parsing

import sensorDataStructure._
import scala.xml._

object OdfParser extends Parser{
  def parse( xml_msg: String): Seq[ ParseResult ]  = {
    val root = XML.loadString( xml_msg ) 
    ( root\"Object" ).flatMap( obj =>{
     parseNode( obj, "Objects/")
    } )
  }

  trait ODFNodeType
  case class Object() extends ODFNodeType 
  case class Infoitem() extends ODFNodeType   
  case class MetaData() extends ODFNodeType   

  case class ODFNode( path: String, nodeType: ODFNodeType, data: Node)

  type ParseResult = Either[ ParseError, ODFNode ]
  
  private def parseNode( obj: Node, currentPath: String ) : Seq[ ParseResult ] = {
    val path = currentPath + obj.label
    obj.label match {
      case "Object" => {
        val subobjs = obj \ "Object"
        val infoitems = obj \ "Infoitem"
        if( infoitems.isEmpty && subobjs.isEmpty ){
          Seq( Right( new ODFNode( path, new Object, obj ) ) )
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
      case "Infoitem" => {
        val values = ( obj \ "value" ).headOption.map( _.text )
        val metadata = ( obj \ "MetaData" ).headOption
        Seq( Right( new ODFNode( path, new Infoitem, obj) ) )    
      }
      case _ => Seq( Left( new ParseError( "Unknown node in O-DF. " + path ) ) )
    }
  }
}


