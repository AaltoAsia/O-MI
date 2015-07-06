package parsing

import types._
import types.OmiTypes._
import types.OdfTypes._
import scala.xml._
import scala.util.{Try, Success, Failure}
import java.io.StringReader
import java.io.IOException
import org.xml.sax.SAXException;
//Schema validation
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator

/**
 * Parser trait that parsers inherit,
 * defines methods for getting child objects, getting parameters and schema validation.
 * Also forces all parsers to define parse method and schemaPath method
 */
abstract trait Parser[Result] {

  def parse(xml_msg: String) : Result
  protected def schemaPath : javax.xml.transform.Source
  
  /**
   * function for checking does given string confort a schema
   * @param xml String to check
   * @return ParseErrors found while checking, if empty, successful
   */
  def schemaValitation(xml: Node): Seq[ParseError] = {
    val factory : SchemaFactory =
      SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schema: Schema = factory.newSchema(schemaPath)
    val validator: Validator = schema.newValidator()
    Try {
      validator.validate(new StreamSource(new StringReader(xml.toString)))
    } match {
      case Success(a) =>
        Seq.empty;
      case Failure(e) => e match {
        case e: IOException =>
          Seq(ParseError("Invalid XML, IO failure: " + e.getMessage))
        case e: SAXException =>
          Seq(ParseError("Invalid XML, schema failure: " + e.getMessage))
        case e: Exception =>
          Seq(ParseError("Unknown exception: " + e.getMessage))
      }
    }
  }

}
