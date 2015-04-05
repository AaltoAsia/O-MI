package parsing
import parsing.Types._

import scala.xml._
import scala.util.Try

import java.io.File;
import java.net.URL;
import java.io.StringReader
import java.io.IOException

//Schema validation
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator
import org.xml.sax.SAXException;

abstract trait Parser[Result] {

  def stringOptioner(str: String) : Option[String] = if(str.nonEmpty) Some(str) else None
  def parse(xml_msg: String) : Seq[Result]
  def schemaPath : javax.xml.transform.Source

  /**
   * private helper function for getting parameter of an node.
   * Handles error cases.
   * @param node were parameter should be.
   * @param paramName parameter's label
   * @param tolerateEmpty is nonexisting parameter accepted, is parameter's existent mandatory
   * @param validation function if parameter musth confor some format
   * @return Either ParseError or parameter as String
   */
  protected def getParameter(
        node: Node,
        paramName: String,
        tolerateEmpty: Boolean = false,
        validation: String => Boolean = _ => true
      ): Either[ParseError, String] = {

    val parameter = (node \ s"@$paramName").text

    if (parameter.isEmpty && !tolerateEmpty)
      return Left(ParseError(s"No $paramName parameter found in ${node.label}."))
    else if (validation(parameter) || parameter.isEmpty)
      return Right(parameter)
    else
      return Left(ParseError(s"Invalid $paramName parameter in ${node.label}."))
  }

  /**
   * private helper function for getting child of an node.
   * Handles error cases.
   * @param node node were parameter should be.
   * @param childName child's label
   * @param tolerateEmpty is child allowed to have empty value
   * @param tolerateNonexist is nonexisting childs accepted, is child's existent mandatory
   * @return Either ParseError or sequence of childs found
   */
  protected def getChild(
        node: Node,
        childName: String,
        tolerateEmpty: Boolean = false,
        tolerateNonexist: Boolean = false
      ): Either[ParseError, Seq[Node]] = {

    val childs = (node \ s"$childName") map (stripNamespaces)

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
  * @param node node where parameter should be.
   * @param childName child's label
   * @param tolerateEmpty is child allowed to have empty value
   * @param tolerateNonexist is nonexisting childs accepted, is child's existent mandatory
   * @param tolerateMultiple is multiple childs accepted
   * @return Either ParseError or sequence of childs found
   */
  protected def getChilds(
        node: Node,
        childName: String,
        tolerateEmpty: Boolean = false,
        tolerateNonexist: Boolean = false,
        tolerateMultiple: Boolean = false
      ): Either[ParseError, Seq[Node]] = {

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
   * @param xml String to check
   * @return ParseErrors found while checking, if empty, successful
   */
  def schemaValitation(xml: String): Seq[ParseError] = {
    try {
      val xsdPath = schemaPath
      val factory : SchemaFactory =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema: Schema = factory.newSchema(xsdPath)
      val validator: Validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(xml)))
    } catch {
      case e: IOException =>
        return Seq(ParseError("Invalid XML, IO failure: " + e.getMessage))
      case e: SAXException =>
        return Seq(ParseError("Invalid XML, schema failure: " + e.getMessage))
      case e: Exception =>
        return Seq(ParseError("Unknown exception: " + e.getMessage))
    }
    return Seq.empty;
  }

  /**
   * Temp function for fixing tests
   */
  def stripNamespaces(node : Node) : Node = {
     node match {
         case e : Elem => 
             e.copy(scope = TopScope, child = e.child map (stripNamespaces))
         case _ => node;
     }
 }
}
