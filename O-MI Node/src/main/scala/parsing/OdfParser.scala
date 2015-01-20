package parsing

import sensorDataStructure._
import scala.xml._
import scala.util.Try

/** Object for parsing data in O-DF format into sequence of ParseResults. */
object OdfParser {
  
  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  type ParseResult = Either[ParseError, ODFNode]
  
  /** Public method for parsing the xml string into seq of ParseResults.
   *  
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return Seq of ParseResults
   */
  def parse(xml_msg: String): Seq[ParseResult] = {
    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(Left(ParseError("Invalid XML"))))
    if (root.label != "Objects")
      return Seq(Left(ParseError("ODF doesn't have Objects as root.")))
    else
      (root \ "Object").flatMap(obj => {
        parseNode(obj, "/Objects")
      })
  }

  /** Private method that is called recursively to parse the given obj Node
   *  
   * @param obj scala.xml.Node that should have Object or InfoItem as label
   * @param currectPath String that contains the path to the current object
   *        e.g. "/Objects/SmartHouse/SmartFridge"
   * @return Seq of ParseResults.
   */
  private def parseNode(obj: Node, currentPath: String): Seq[ParseResult] = {
    obj.label match {
      /* Found an Object*/
      case "Object" => {
        val id = (obj \ "id").text
        if (id.isEmpty()) 
          return Seq(Left(ParseError("No id for Object.")))
        val path = currentPath + "/" + id
        val subobjs = obj \ "Object"
        val infoitems = obj \ "InfoItem"
        if (infoitems.isEmpty && subobjs.isEmpty) {
          Seq(Right(new ODFNode(path, NodeObject, None, None, None)))
        } else {
          val eithers: Seq[ParseResult] =
            subobjs.flatMap {
              sub: Node => parseNode(sub, path)
            }
          eithers ++ infoitems.flatMap { item: Node =>
            parseNode(item, path)
          }
        }
      }
      //TODO check this!!
      /* Found an InfoItem*/
      case "InfoItem" => {
        val name = (obj \ "@name").text
        if (name.isEmpty())
          return Seq(Left(ParseError("No name for InfoItem.")))
        val path = currentPath + "/" + name
        val values = (obj \ "value").headOption match {
          case Some(node: Node) => {
            Some(node.text)
          }
          case None => None
        }
        val time = (obj \ "value").headOption match {
          case None => None
          case Some(v) => (v \ "@dateTime").headOption match {
            case Some(t) => Some("dateTime=\"" + t.text + "\"")
            case None => (v \ "@unixTime").headOption match {
              case Some(u) => Some("unixtime=\"" + u.text + "\"")
              case None => None
            }
          }
        }

        val metadata = (obj \ "MetaData").headOption match {
          case Some(node: Node) => {
            Some(node.text)
          }
          case None => None
        }
        Seq(Right(new ODFNode(path, InfoItem, values, time, metadata)))
      }
      //Unreachable code?
      case _ => Seq(Left(new ParseError("Unknown node in O-DF. " + currentPath)))
    }
  }
}
