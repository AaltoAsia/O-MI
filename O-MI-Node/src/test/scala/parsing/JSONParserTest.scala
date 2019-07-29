/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 + Copyright (c) 2015 Aalto University.                                       +
 +                                                                            +
 + Licensed under the 4-clause BSD (the "License");                           +
 + you may not use this file except in compliance with the License.           +
 + You may obtain a copy of the License at top most directory of project.     +
 +                                                                            +
 + Unless required by applicable law or agreed to in writing, software        +
 + distributed under the License is distributed on an "AS IS" BASIS,          +
 + WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   +
 + See the License for the specific language governing permissions and        +
 + limitations under the License.                                             +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package parsing

import org.json4s.JObject
import org.json4s.native.JsonMethods.parse
import org.specs2._
import org.specs2.matcher.MatchResult
import org.specs2.specification.core.SpecStructure
import types.OmiTypes.OmiParseResult
import types.odf.ODF
import types.{ODFParserError, OMIParserError, ParseError, ParseErrorList}

import scala.util.Try

class JSONParserTest extends Specification {
  def is: SpecStructure =
    s2"""
      This is a specification for testing the O-MI & O-DF JSON Parser

      OMI JSON Parser should give the correct response for a
        message with
          incorrect JSON      $e1
          missing request     $e2
          missing ttl         $e3
          missing version     $e4
          correct omiEnvelope $e5
        write request with
          correct message     $e100
          missing msgformat   $e101
          missing msg         $e103
          missing Objects     $e104
          no objects to parse $e105
        response message with
          correct message     $e200
          missing Objects     $e204
          missing result node $e205
          no objects to parse $e206
          missing return code $e207
        read request with
          correct message     $e300
          missing msgformat   $e301
          missing msg         $e303
          missing Objects     $e304
          no objects to parse $e305
        correct subscription  $e306
        cancel request with
          correct request     $e500
      ODF JSON Parser should give certain result for message with
        correct format      $e400
        incorrect XML       $e401
        incorrect label     $e402

      """


  val parser = new JSONParser



  def e1 = {
    parseOmi("<omiEnvelope/>", Some("OMIParserError"))
  }

  def e2 = {
    parseOmi(
      """
       {
         "omiEnvelope": {
         "version": "2",
         "ttl": 1
         }
       }
      """
      , Some("OMIParserError"))
  }

  def e3 = {
    parseOmi(
      """
       {
         "omiEnvelope": {
         "version": "2",
         "read": {}
         }
       }
      """
      , Some("OMIParserError"))
  }

  def e4 = {

    parseOmi(
      """
       {
         "omiEnvelope": {
         "ttl": 1,
         "read": {}
         }
       }
      """
      , Some("OMIParserError"))
  }

  def e5 = {
    parseOmi(
      """
       {
         "omiEnvelope": {
         "version": "2",
         "ttl": 1,
         "read": {}
         }
       }
      """
    )
  }
  //READ
  def e100 = {
    parseOmi(
      """
       {
         "omiEnvelope": {
         "version": "2",
         "ttl": 1,
         "read": {}
         }
       }
      """
    )
    ???
  }
  //WRITE
  def e200 = {
    ???
  }
  //RESPONSE
  def e300 = {
    ???
  }
  //CANCEL
  def e400 = {
    ???
  }
  //CALL
  def e500 = {
    ???
  }
  //DELETE
  def e600 = {
    ???
  }
  //SUBSCRIPTION
  def e700 = {
    ???
  }
  //ODF
  def e800 = {
    ???
  }


  def parseOmi(msg: String, error: Option[String] = None): MatchResult[OmiParseResult] = {

    val result = parser.parse(msg)

    error.fold(result must beRight){
      err => result must beLeft {
        parseErrors: Iterable[ParseError] => parseErrors.headOption must beSome{
          parseError: ParseError => errorType(parseError) must beEqualTo(err).ignoreCase
        }
      }
    }
  }

  def parseOdf(msg: String, error: Option[String] = None): MatchResult[Try[ODF]] = {
    val result = Try(parse(msg).asInstanceOf[JObject].obj.toMap.get("Objects").get).flatMap(parser.parseObjects(_))
    error.fold(result must beSuccessfulTry){
      err => result must beAFailedTry{
        parseError: Throwable => errorType(parseError) must beEqualTo(err).ignoreCase
      }
    }
  }

  def errorType(pe: Throwable) = pe match {
    case _: ODFParserError => "ODFParserError"
    case _: OMIParserError => "OMIParserError"
    case _: ParseErrorList => "ParserErrorList"
    case _ => throw pe

  }
}
