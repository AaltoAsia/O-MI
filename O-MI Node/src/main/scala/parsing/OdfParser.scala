package parsing


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

import org.xml.sax.SAXException;

/** Object for parsing data in O-DF format into sequence of ParseResults. */
object OdfParser {

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  type ParseResult = Either[ParseError, OdfObject]

  /**
   * Public method for parsing the xml string into seq of ParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return Seq of ParseResults
   */
  def parse(xml_msg: String): Seq[ParseResult] = {
    val schema_err = validateOdfSchema(xml_msg)
    if (schema_err.nonEmpty)
      return schema_err.map { e => Left(e) }

    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(Left(ParseError("Invalid XML"))))
    if (root.label != "Objects")
      return Seq(Left(ParseError("ODF doesn't have Objects as root.")))
    else
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
  private def parseNode(node: Node, currentPath: Seq[String]): Seq[ParseResult] = {
    node.label match {
      /* Found an Object*/
      case "Object" => {
        parseObject(node, currentPath)
      }
      //Unreachable code?
      case _ => Seq(Left(ParseError("Unknown node in O-DF at path: " + currentPath.mkString("/"))))
    }
  }

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
  private def parseInfoItem(node: Node, currentPath: Seq[String]): Seq[InfoItemResult] = {
    var parameters: Map[String, Either[ParseError, String]] = Map(
      "name" -> getParameter(node, "name"))

    val subnodes = Map(
      "value" -> getChilds(node, "value", false, true, true),
      "MetaData" -> getChild(node, "MetaData", true, true))

    val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
    if (errors.nonEmpty)
      return errors.map(e => Left(e)).toSeq

    val path = currentPath :+ parameters("name").right.get

    val dateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss")
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
          val timestamp = new Timestamp(timeStr.toInt/1000)
          TimedValue(Some(timestamp), value.text)
        } catch {
          case e: Exception =>//TODO: better error msg.
            return Seq( Left( ParseError( "unixTime have invalid value." ) ) )
        }
      }
    }

    Seq(Right(OdfInfoItem(path, timedValues, subnodes("MetaData").right.get.text)))
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
  private def parseObject(node: Node, currentPath: Seq[String]): Seq[ObjectResult] = {
    val subnodes = Map(
      "id" -> getChild(node, "id"),
      "Object" -> getChilds(node, "Object", true, true, true),
      "InfoItem" -> getChilds(node, "InfoItem", true, true, true)
    )

    val errors = subnodes.filter(_._2.isLeft).map(_._2.left.get)
    if (errors.nonEmpty)
      return errors.map(e => Left(e)).toSeq

    val path = currentPath :+ subnodes("id").right.get.text
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

  /**
   * private helper function for getting parameter of an node.
   * Handles error cases.
   * @param node were parameter should be.
   * @param parameter's label
   * @param is nonexisting parameter accepted, is parameter's existent mandatory
   * @param validation function if parameter musth confor some format
   * @return Either ParseError or parameter as String
   */
  private def getParameter(node: Node,
    paramName: String,
    tolerateEmpty: Boolean = false,
    validation: String => Boolean = _ => true): Either[ParseError, String] = {
    val parameter = (node \ s"@$paramName").text
    if (parameter.isEmpty && !tolerateEmpty)
      return Left(ParseError(s"No $paramName parameter found in ${node.label}."))
    else if (validation(parameter))
      return Right(parameter)
    else
      return Left(ParseError(s"Invalid $paramName parameter in ${node.label}."))
  }

  /**
   * private helper function for getting child of an node.
   * Handles error cases.
   * @param node were parameter should be.
   * @param child's label
   * @param is child allowed to have empty value
   * @param is nonexisting childs accepted, is child's existent mandatory
   * @return Either ParseError or sequence of childs found
   */
  private def getChild(node: Node,
    childName: String,
    tolerateEmpty: Boolean = false,
    tolerateNonexist: Boolean = false): Either[ParseError, Seq[Node]] = {
    val childs = (node \ s"$childName")
    if (!tolerateNonexist && childs.isEmpty)
      return Left(ParseError(s"No $childName child found in ${node.label}."))
    else if (!tolerateEmpty && childs.nonEmpty && childs.head.text.isEmpty )
      return Left(ParseError(s"$childName's value not found in ${node.label}."))
    else
      return Right(childs)
  }

  /**
   * private helper function for getting child of an node.
   * Handles error cases.
   * @param node were parameter should be.
   * @param child's label
   * @param is child allowed to have empty value
   * @param is nonexisting childs accepted, is child's existent mandatory
   * @param is multiple childs accepted
   * @return Either ParseError or sequence of childs found
   */
  private def getChilds(node: Node,
    childName: String,
    tolerateEmpty: Boolean = false,
    tolerateNonexist: Boolean = false,
    tolerateMultiple: Boolean = false): Either[ParseError, Seq[Node]] = {
    val childs = (node \ s"$childName")
    if (!tolerateNonexist && childs.isEmpty)
      return Left(ParseError(s"No $childName child found in ${node.label}."))
    else if (!tolerateMultiple && childs.size > 1)
      return Left(ParseError(s"Multiple $childName childs found in ${node.label}."))
    else if (!tolerateEmpty && childs.nonEmpty && childs.contains{ n: Node => n.text.isEmpty }  )
      return Left(ParseError(s"$childName's value not found in ${node.label}."))
    else
      return Right(childs)
  }

  /**
   * function for checking does given string confort O-DF schema
   * @param String to check
   * @return ParseErrors found while checking, if empty, successful
   */
  def validateOdfSchema(xml: String): Seq[ParseError] = {
    try {
      val xsdPath = "./src/main/resources/odf.xsd"
      val factory : SchemaFactory =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema: Schema = factory.newSchema(new File(xsdPath))
      val validator: Validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(xml)))
    } catch {
      case e: IOException =>
        //TODO: log these instead of println
        //        println(e.getMessage()) 
        return Seq(ParseError("Invalid XML, IO failure: " + e.getMessage))
      case e: SAXException =>
        //        println(e.getMessage()) 
        return Seq(ParseError("Invalid XML, schema failure: " + e.getMessage))
      case e: Exception =>
        return Seq(ParseError("Unknown exception: " + e.getMessage))
    }
    return Seq.empty;
  }

}
