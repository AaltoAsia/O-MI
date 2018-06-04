
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

package types
  /** case class that represents parsing error
   *  @param msg error message that describes the problem.
   */
  object ParseError{
    def combineErrors( errors: Iterable[ParseError] ) : ParseError = ParseErrorList(
      errors.map{ e => e.getMessage }.mkString("\n")
    )
  }
  class ParseError( msg: String, sourcePrefix: String) extends Exception(sourcePrefix +msg)
  case class ScalaXMLError(msg: String) extends ParseError( msg, "Scala XML error: " )
  case class ScalaxbError(msg: String) extends ParseError( msg, "Scalaxb error: " )
  case class SchemaError(msg: String) extends ParseError( msg, "Schema error: ")
  case class ODFParserError(msg: String) extends ParseError( msg, "O-DF Parser error: " )
  case class OMIParserError(msg: String) extends ParseError( msg, "O-MI Parser error: " )
  case class ParseErrorList(msg: String) extends ParseError(msg, "")
  case class Warp10ParseError(msg: String) extends ParseError(msg, "Warp10 Parse error: ")
