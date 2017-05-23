/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package parsing

import java.io.{File, IOException, StringReader}
import java.sql.Timestamp
import java.util.Date
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory, Validator}

import scala.util.{Failure, Success, Try}
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, Node, XML}
import akka.http.scaladsl.model.RemoteAddress
import org.xml.sax.SAXException
import types.{SchemaError,ParseError}
import types.OmiTypes.UserInfo

/**
 * Parser trait that parsers inherit,
 * defines methods for check xml against a xml schema.
 * Also forces all parsers to define parse method and schemaPath method
 */
abstract trait Parser[Result] {

  //O-MI version this parser supports
  def supportedVersion = "1.0"
  // Secure parser that has a fix for xml external entity attack (and xml bomb)
  def XMLParser : XMLLoader[Elem] = {
    val spf = SAXParserFactory.newInstance()
    spf.setFeature("http://xml.org/sax/features/external-general-entities", false)
    spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val saxParser = spf.newSAXParser()
    XML.withSAXParser(saxParser)
  }

  def parse(xml_msg: String) : Result

  //@deprecated("Not supported because of xml external entity attack fix, use this.XMLParser! -- TK", "2016-04-01")
  //def parse(xml_msg: xml.Node) : Result
  
  def parse(xml_msg: File) : Result
  
  protected[this] def schemaPath : Array[javax.xml.transform.Source]
  
  /**
   * Method for checking does given xml confort schema of parser.
   * @param xml xml structure to check
   * @return ParseErrors found while checking, if empty, successful
   */
  def schemaValidation(xml: Node): Seq[ParseError] = {
    Try {
    val factory : SchemaFactory =
      SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)

    val schema: Schema = factory.newSchema(schemaPath)
    val validator: Validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(xml.toString)))
    } match {
      case Success(a) =>
        Seq.empty;
      case Failure(e) => 
        
        e match {
        case e: IOException =>
          Seq(SchemaError("IO failure: " + e.getMessage))
        case e: SAXException =>
          Seq(SchemaError(e.getMessage))
        case e: Exception=>
          Seq(SchemaError("Unknown exception: " + e.getMessage))
        case _ => throw e
      }
    }
  }

  def currentTime() : Timestamp= new Timestamp( new Date().getTime ) 
}
