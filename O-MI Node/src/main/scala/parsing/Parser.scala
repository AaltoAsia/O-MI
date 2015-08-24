/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  https://github.com/AaltoAsia/O-MI/blob/master/LICENSE.txt

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package parsing

import types.ParseError

import scala.xml.Node
import scala.util.{Try, Success, Failure}
import java.io.{StringReader, IOException}
import org.xml.sax.SAXException;
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory, Validator}

/**
 * Parser trait that parsers inherit,
 * defines methods for getting child objects, getting parameters and schema validation.
 * Also forces all parsers to define parse method and schemaPath method
 */
abstract trait Parser[Result] {

  def parse(xml_msg: String) : Result
  protected[this] def schemaPath : javax.xml.transform.Source
  
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
        case e: Exception=>
          Seq(ParseError("Unknown exception: " + e.getMessage))
        case t => throw t
      }
    }
  }

}
