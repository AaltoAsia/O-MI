package parsing

import scala.collection.mutable.Map
import scala.xml._
import scala.util.Try
 
import java.io.File;
import java.io.StringReader
import java.io.IOException

//Schema validation
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator
 
import org.xml.sax.SAXException;

/** Object for parsing data in O-DF format into sequence of ParseResults. */
object OdfParser {
  
  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  type ParseResult = Either[ParseError, OdfNode]
  
  /** Public method for parsing the xml string into seq of ParseResults.
   *  
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return Seq of ParseResults
   */
  def parse(xml_msg: String): Seq[ParseResult] = {
    val schema_err = validateOdfSchema(xml_msg)
       if( schema_err.nonEmpty )
         return schema_err.map{ e => Left(e)}

   val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(Left(ParseError("Invalid XML"))))
    if (root.label != "Objects")
      return Seq(Left(ParseError("ODF doesn't have Objects as root.")))
    else
      (root \ "Object").flatMap(obj => {
        parseNode(obj, Seq(root.label))
      })
  }

  /** Private method that is called recursively to parse the given obj Node
   *  
   * @param obj scala.xml.Node that should have Object or InfoItem as label
   * @param currectPath String that contains the path to the current object
   *        e.g. "/Objects/SmartHouse/SmartFridge"
   * @return Seq of ParseResults.
   */
  private def parseNode(node: Node, currentPath: Seq[String]): Seq[ParseResult] = {
    node.label match {
      /* Found an Object*/
      case "Object" => {
        parseObject(node, currentPath)
      }
      //TODO check this!!
      /* Found an InfoItem*/
      case "InfoItem" => {
        parseInfoItem(node, currentPath)
      }
      //Unreachable code?
      case _ => Seq( Left( ParseError( "Unknown node in O-DF at path: " + currentPath.mkString( "/") ) ) )
    }
  }

  private type InfoItemResult = Either[ParseError, OdfInfoItem]
  private def parseInfoItem(obj: Node, currentPath: Seq[String])
                            : Seq[ Either[ ParseError, OdfInfoItem] ] = {
         var parameters: Map[String, Either[ ParseError, String ]] = Map(
          "name" -> getParameter( obj, "name")
        )

        val path = parameters( "name") match{
          case Right( name : String) =>
            currentPath :+ name
          case Left( err: ParseError) =>
            return Seq( Left( err) )
        }

        val values = obj \ "value" 
        val timedValues: Seq[ TimedValue] = values.toSeq.map{ value : Node => 
          val time = getParameter(value, "unixTime", true).right.get 
          if( time.isEmpty )
            TimedValue( getParameter(value, "dateTime", true).right.get , value.text ) 
          else 
            TimedValue( time, value.text )
        }

        val metadata = (obj \ "MetaData").headOption match {
          case Some(node: Node) => {
            Some(node.text)
          }
          case None => None
        }
        Seq( Right( OdfInfoItem( path, timedValues, metadata) ) )
  }
      




  private type ObjectResult = Either[ParseError, OdfObject]
  private def parseObject(obj: Node, currentPath: Seq[String])
                            : Seq[ ObjectResult ] = {
    val id = (obj \ "id")
    if ( id.isEmpty ) 
      return Seq( Left( ParseError( "No id for Object.") ) )
    val path = currentPath :+ id.head.text
    val subobjs = obj \ "Object"
    val infoitems = obj \ "InfoItem"
    if (infoitems.isEmpty && subobjs.isEmpty) {
      Seq( Right( OdfObject( path, Seq.empty[ OdfObject], Seq.empty[ OdfInfoItem], None) ) )
    } else {
      val eithersObjects: Seq[ ObjectResult] = subobjs.flatMap { 
        sub: Node => parseObject(sub, path) 
      }
      val eithersInfoItems: Seq[ InfoItemResult] = infoitems.flatMap { item: Node =>
        parseInfoItem(item, path)
      }
      val errors: Seq[ Either[ ParseError, OdfObject] ] = 
        eithersObjects.filter{res => res.isLeft}.map{ left => Left( left.left.get) } ++ 
        eithersInfoItems.filter{res => res.isLeft}.map{ left => Left( left.left.get) }
      val childs : Seq[ OdfObject ] =
        eithersObjects.filter{res => res.isRight}.map{ right => right.right.get }
      val sensors : Seq[ OdfInfoItem ] =
        eithersInfoItems.filter{res => res.isRight}.map{ right => right.right.get }
      if( errors.nonEmpty)
        return errors
      else
        return Seq( Right( OdfObject( path, childs, sensors, None) ) )
    }
  }





  private def getParameter( node: Node, 
                    paramName: String,
                    tolerateEmpty: Boolean = false, 
                    validation: String => Boolean = _ => true) 
                  : Either[ ParseError, String ] = {
    val parameter = ( node \ "@$paramName" ).text
    if( parameter.isEmpty && !tolerateEmpty )
      return Left( ParseError( "No $paramName parameter found in " + node.label ) )
    else if( validation( parameter ) )
      return Right( parameter )
    else
      return Left( ParseError( "Invalid $paramName parameter" ) )
  }

  def validateOdfSchema( xml: String) : Seq[ ParseError] = {
    try {
      val xsdPath = "./src/main/resources/odfschema.xsd"
      val factory : SchemaFactory =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema: Schema = factory.newSchema(new File(xsdPath))
      val validator: Validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(xml)))
    } catch { 
      case e: IOException => 
        //TODO: log these instead of println
        println(e.getMessage()) 
        Seq( ParseError("Invalid XML, schema failure") )
      case e: SAXException =>
        println(e.getMessage()) 
        Seq( ParseError("Invalid XML, schema failure") )
    }
    return Seq.empty;
   }

}
