package parsing
import parsing.Types._

import scala.collection.mutable.Map

import scala.xml._
import scala.util.Try

import java.io.File;
import java.io.StringReader
import java.io.IOException
import java.sql.Timestamp
import java.text.SimpleDateFormat

//Schema validation
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator
import scala.xml.Utility.trim
import org.xml.sax.SAXException;

/** Object for parsing data in O-DF format into sequence of ParseResults. */
object OdfParser extends Parser[OdfParseResult] {

  override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("odf.xsd"))

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/

  /**
   * Public method for parsing the xml string into seq of ParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return Seq of ParseResults
   */
  def parse(xml_msg: String): Seq[OdfParseResult] = {
    val schema_err = schemaValitation(xml_msg)
    if (schema_err.nonEmpty)
      return schema_err.map { e => Left(e) }

    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(Left(ParseError("Invalid XML"))))
      (root \ "Object").flatMap(obj => {
        parseNode(obj, Seq(root.label))
      })
  }

  /**
   * Private method that is called recursively to parse the given obj Node
   *
   * @param obj scala.xml.Node that should have Object or InfoItem as label
   * @param currectPath String that contains the path to the current object
   *        e.g. "/Objects/SmartHouse/SmartFridge"
   * @return Seq of ParseResults.
   */
  private def parseNode(node: Node, currentPath: Seq[String]): Seq[OdfParseResult] = {
    node.label match {
      /* Found an Object*/
      case "Object" => {
        parseObject(node, currentPath)
      }
      //Unreachable code?
      case _ => Seq(Left(ParseError("Unknown node in O-DF at path: " + currentPath.mkString("/"))))
    }
  }
  
  //Changed to def from val because of concurrency problems
  private def dateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss")
  /**
   * private helper type for parseInfoItem
   *
   */
  private type InfoItemResult = Either[ParseError, OdfInfoItem]
  /**
   * private helper function for parsing an InfoItem
   * @param node to parse, should be InfoItem
   * @param current path parsed from xml
   */
  private def parseInfoItem(node: Node, currentPath: Path): Seq[InfoItemResult] = {
    //Get parameters
    var parameters: Map[String, Either[ParseError, String]] = Map(
      "name" -> getParameter(node, "name"))

    //Get subnodes
    val subnodes = Map(
      "value" -> getChilds(node, "value", false, true, true))
    val metaDataOpt = (node \ "MetaData").headOption
    val metaData = if(metaDataOpt.nonEmpty)
      trim(metaDataOpt.get).toString
    else
      ""
    //Check for malformed parameters and subnodes.
    val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
    if (errors.nonEmpty)
      return errors.map(e => Left(e)).toSeq

    //Update path
    val path = currentPath / parameters("name").right.get

    //Go througth value sub nodes.
    val timedValues: Seq[TimedValue] = subnodes("value").right.get.toSeq.map { value: Node =>
      var timeStr = getParameter(value, "unixTime", true).right.get
      if (timeStr.isEmpty) {
        timeStr = getParameter(value, "dateTime", true).right.get
        if (timeStr.isEmpty){
          TimedValue(None, value.text)
        } else {
          val timestamp = new Timestamp(dateFormat.parse(timeStr).getTime)
          TimedValue(Some(timestamp), value.text)
        }
      } else {
        try {
          val timestamp = new Timestamp(timeStr.toLong*1000)
          TimedValue(Some(timestamp), value.text)
        } catch {
          case e: Exception =>//TODO: better error msg.
            return Seq( Left( ParseError( "unixTime have invalid value." ) ) )
        }
      }
    }

    //Check MetaData
    if(metaData.nonEmpty)
      Seq(Right(OdfInfoItem(path, timedValues,Some(InfoItemMetaData(metaData)))))
    else
      Seq(Right(OdfInfoItem(path, timedValues,None)))

  }
  /**
   * private helper type for parseObject
   *
   */
  private type ObjectResult = Either[ParseError, OdfObject]

  /**
   * private helper function for parsing an Object, recursive
   * @param node to parse, should be an Object
   * @param current path parsed from xml
   */
  private def parseObject(node: Node, currentPath: Path): Seq[ObjectResult] = {
    //Get subnodes
    val subnodes = Map(
      "id" -> getChild(node, "id"),
      "Object" -> getChilds(node, "Object", true, true, true),
      "InfoItem" -> getChilds(node, "InfoItem", true, true, true)
    )
    //Check for malformed subnodes
    val errors = subnodes.filter(_._2.isLeft).map(_._2.left.get)
    if (errors.nonEmpty)
      return errors.map(e => Left(e)).toSeq

    //Update path
    val path = currentPath / subnodes("id").right.get.text
    //If no InfoItems found return, else go througth sub Objects and InfoItems
    if (subnodes("InfoItem").right.get.isEmpty && subnodes("Object").right.get.isEmpty) {
      Seq(Right(OdfObject(path, Seq.empty[OdfObject], Seq.empty[OdfInfoItem])))
    } else {
      val eithersObjects: Seq[ObjectResult] = subnodes("Object").right.get.flatMap {
        sub: Node => parseObject(sub, path)
      }
      val eithersInfoItems: Seq[InfoItemResult] = subnodes("InfoItem").right.get.flatMap { item: Node =>
        parseInfoItem(item, path)
      }
      val errors: Seq[Either[ParseError, OdfObject]] =
        eithersObjects.filter { res => res.isLeft }.map { left => Left(left.left.get) } ++
          eithersInfoItems.filter { res => res.isLeft }.map { left => Left(left.left.get) }
      val childs: Seq[OdfObject] =
        eithersObjects.filter { res => res.isRight }.map { right => right.right.get }
      val sensors: Seq[OdfInfoItem] =
        eithersInfoItems.filter { res => res.isRight }.map { right => right.right.get }
      if (errors.nonEmpty)
        return errors
      else
        return Seq(Right(OdfObject(path, childs, sensors)))
    }
  }


}
