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

import java.sql.Timestamp

import org.json4s.JObject
import org.json4s.native.JsonMethods.parse
import org.specs2._
import org.specs2.matcher.MatchResult
import org.specs2.specification.core.SpecStructure
import types.OmiTypes._
import types.odf.{Description, ImmutableODF, InfoItem, IntValue, MetaData, ODF, Object, Objects, StringPresentedValue, Value}
import types.{ODFParserError, OMIParserError, ParseError, ParseErrorList, Path}

import scala.util.Try

class JSONParserTest extends Specification {
  def is: SpecStructure =
    s2"""
      This is a specification for testing the O-MI & O-DF JSON Parser

      OMI JSON Parser should give the correct response for a
        message with
          incorrect JSON                  $e1
          missing request                 $e2
          missing ttl                     $e3
          missing version                 $e4
          correct omiEnvelope             $e5
        read request with
          correct message                 $e100
          callback address                $e101
          both newest and oldest present  $e102
          both newest and begin present   $e103
          both begin and end present      $e104
        poll request with
          correct message                 $e105
        write with
          correct message                 $e200
        response with
          correct message                 $e300
          missing return                  $e301
        cancel with
          correct message                 $e400
          invalid message                 $e401
        call with
          correct message                 $e500
        delete with
          correct message                 $e600
        subscription with
          interval subscription           $e700
          invalid interval subscription   $e701
          -1 interval subscription        $e702
          event subscription              $e703
        ODF
          correct message                 $e800
          correct message                 $e801
       """
"""
      ODF JSON Parser should give certain result for message with
        correct format
        incorrect XML
        incorrect label

      """
""""""
  val parser = new JSONParser



  def e1 = {
    parseOmi("<omiEnvelope/>", Left("OMIParserError"))
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
      , Left("OMIParserError"))
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
      , Left("OMIParserError"))
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
      , Left("OMIParserError"))
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
      , Right("read")
    )
  }
  //READ
  def e100 = {
    parseOmi(
      """
        {
          "omiEnvelope": {
            "version": "1.0",
            "ttl": 0,
            "read": {
              "msgformat": "odf",
              "msg": {
                "Objects": {}
              }
            }
          }
        }
      """,
      Right("read")
    )
  }

  def e101 = {
    parseOmi(
      """
        {
          "omiEnvelope": {
            "version": "1.0",
            "ttl": 0,
            "read": {
              "callback": "http://localhost",
              "msgformat": "odf",
              "msg": {
                "Objects": {}
              }
            }
          }
        }
      """,
      Right("read")
    ) and parseOmi(
      """
        {
          "omiEnvelope": {
            "version": "1.0",
            "ttl": 0,
            "read": {
              "callback": "https://www.google.com",
              "msgformat": "odf",
              "msg": {
                "Objects": {}
              }
            }
          }
        }
      """,
      Right("read")
    )
  }

  def e102 = {
    parseOmi(
      """
        {
          "omiEnvelope": {
            "version": "1.0",
            "ttl": 0,
            "read": {
              "msgformat": "odf",
              "msg": {
                "Objects": {}
              },
              "oldest": 3,
              "newest": 3
            }
          }
        }
      """
      , Left("OMIParserError"))
  }
  def e103 = {
    parseOmi(
      """
        {
          "omiEnvelope": {
            "version": "1.0",
            "ttl": 0,
            "read": {
              "msgformat": "odf",
              "msg": {
                "Objects": {}
              },
              "begin": "2019-07-29T21:00:00.000Z"
              "newest": 3

            }
          }
        }
      """,
      Right("read")
      )
  }

  def e104 = {
    parseOmi(
      """
        {
          "omiEnvelope": {
            "version": "1.0",
            "ttl": 0,
            "read": {
              "msgformat": "odf",
              "msg": {
                "Objects": {}
              },
              "begin": "2019-07-29T21:00:00.000Z",
              "end": "2019-07-31T21:00:00.000Z"
            }
          }
        }
      """,
      Right("read")
    )
  }

  def e105 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "read": {
        |      "requestID": "1"
        |    }
        |  }
        |}
      """.stripMargin
      ,
      Right("poll")
    )
  }

  //WRITE
  def e200 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "write": {
        |      "msgformat": "odf",
        |      "msg": {
        |        "Objects": {}
        |      }
        |    }
        |  }
        |}
      """.stripMargin,
      Right("write"))
  }
  //RESPONSE
  def e300 = {
    parseOmi(
     """
       |{
       |  "omiEnvelope": {
       |    "version": "1.0",
       |    "ttl": 10,
       |    "response": {
       |      "result": {
       |        "msgformat": "odf",
       |        "return": {
       |          "returnCode": "200"
       |        },
       |        "msg": {}
       |      }
       |    }
       |  }
       |}
     """.stripMargin,
      Right("response")
    ) and
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 10,
        |    "response": {
        |      "result": [
        |        {
        |          "msgformat": "odf",
        |          "return": {
        |            "returnCode": "200"
        |          },
        |          "msg": {}},
        |        {
        |          "msgformat": "odf",
        |          "return": {
        |            "returnCode": "200"
        |          },
        |          "msg": {},
        |        }]
        |    }
        |  }
        |}
      """.stripMargin,
      Right("response")
    )
  }
  def e301 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 10,
        |    "response": {
        |      "result": {
        |        "msgformat": "odf",
        |        "msg": {}
        |      }
        |    }
        |  }
        |}
      """.stripMargin
      , Left("OMIParserError")
    )
  }
  //CANCEL
  def e400 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "cancel": {
        |      "requestID": "1"
        |    }
        |  }
        |}
      """.stripMargin
    ,Right("cancel"))
  }
  def e401 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "cancel": {
        |      "requestID": ["1", "2"]
        |    }
        |  }
        |}
      """.stripMargin
      , Right("cancel"))
  }
  //CALL
  def e500 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "call": {
        |      "callback": "http://localhost",
        |    }
        |  }
        |}
      """.stripMargin,
      Right("call")
    )
  }
  //DELETE
  def e600 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "delete": {
        |      "callback": "http://localhost",
        |      "msgformat": "odf",
        |      "msg": {
        |        "Objects": {}
        |      }
        |    }
        |  }
        |}
      """.stripMargin,
      Right("delete")
    )
  }
  //SUBSCRIPTION
  def e700 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "read": {
        |      "callback": "0",
        |      "msgformat": "odf",
        |      "msg": {
        |        "Objects": {}
        |      },
        |      "interval": 5
        |    }
        |  }
        |}
      """.stripMargin,
      Right("subscription")
    )
  }
  def e701 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "read": {
        |      "callback": "0",
        |      "msgformat": "odf",
        |      "msg": {
        |        "Objects": {}
        |      },
        |      "interval": -5
        |    }
        |  }
        |}
      """.stripMargin,
      Left("OMIParserError")
    )
  }
  def e702 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "read": {
        |      "callback": "0",
        |      "msgformat": "odf",
        |      "msg": {
        |        "Objects": {}
        |      },
        |      "interval": -1
        |    }
        |  }
        |}
      """.stripMargin,
      Right("subscription")
    )
  }
  def e703 = {
    parseOmi(
      """
        |{
        |  "omiEnvelope": {
        |    "version": "1.0",
        |    "ttl": 0,
        |    "read": {
        |      "callback": "0",
        |      "msgformat": "odf",
        |      "msg": {
        |        "Objects": {}
        |      },
        |      "interval": -2
        |    }
        |  }
        |}
      """.stripMargin,
      Right("subscription")
    )
  }
  //ODF
  def e800 = {
    parseOdf(
      """
        |{
        |  "Objects": {
        |    "Object": {
        |      "id": "OMI-Service",
        |      "Object": {
        |        "id": "Settings",
        |        "InfoItem": {
        |          "name": "num-latest-values-stored"
        |        }
        |      }
        |    }
        |  }
        |}
      """.stripMargin) and
    parseOdf(
      """
        |{
        |"Objects": {
        |  "Object": {
        |    "id": "OMI-Service",
        |    "Object": {
        |      "id": "Settings",
        |      "InfoItem": {
        |        "name": "num-latest-values-stored",
        |        "description": {
        |          "text": ""
        |        },
        |        "values": {
        |          "type": "xs:int",
        |          "dateTime": "2019-06-04T13:37:59.311+03:00",
        |          "unixTime": 1559644679,
        |          "value": 50
        |        }
        |      }
        |    }
        |  }
        |}
        |}
      """.stripMargin
    )
  }

  def e801 = {
    val msg = """
        |{
        |          "Objects": {
        |            "Object": {
        |              "id": "OMI-Service",
        |              "Object": {
        |                "id": "Settings",
        |                "InfoItem": {
        |                  "name": "num-latest-values-stored",
        |                  "description": {
        |                    "text": "Number of latest values (per sensor) that will be saved to the DB"
        |                  },
        |                  "MetaData": {
        |                    "InfoItem": {
        |                      "name": "meta",
        |                      "values":{"unixTime": 100, "value":"2"}
        |                    }
        |                  },
        |                  "values": {
        |                    "type": "xs:int",
        |                    "unixTime": 100,
        |                    "value": "50"
        |                  }
        |                }
        |              }
        |            }
        |          }
        |}
        |
      """.stripMargin
    val result = Try(parse(msg).asInstanceOf[JObject].obj.toMap.get("Objects").get).flatMap(parser.parseObjects(_))
    result must beSuccessfulTry{
      ImmutableODF(
              Vector(
                Objects(),
                Object(Path("Objects/OMI-Service")),
                Object(Path("Objects/OMI-Service/Settings")),
                InfoItem(
                  "num-latest-values-stored",
                  Path("Objects/OMI-Service/Settings/num-latest-values-stored"),
                  None,
                  Vector.empty,
                  Set(Description("Number of latest values (per sensor) that will be saved to the DB")),
                  Vector(IntValue(50, new Timestamp(100000))),
                  Some(MetaData(Vector(InfoItem(Path("Objects/OMI-Service/Settings/num-latest-values-stored/MetaData/meta"),Vector(StringPresentedValue("2", new Timestamp(100000))))))),
                  Map.empty)
              ))}



  }

  def parseOmi(msg: String, check: Either[String,String]): MatchResult[OmiParseResult] = {

    val result = parser.parse(msg)
   check.fold(error => result must beLeft{
     parseErrors: Iterable[ParseError] => parseErrors.headOption must beSome {
       parseError: ParseError => errorType(parseError) must beEqualTo(error).ignoreCase
     }
   },classes => result must beRight.like{case i => i.headOption must beSome(correctClass(classes))})
  }
  //def parseOmi(msg: String, error: Option[String] = None): MatchResult[OmiParseResult] = {

  //  val result = parser.parse(msg)

  //  error.fold(result must beRight){
  //    err => result must beLeft {
  //      parseErrors: Iterable[ParseError] => parseErrors.headOption must beSome{
  //        parseError: ParseError => errorType(parseError) must beEqualTo(err).ignoreCase
  //      }
  //    }
  //  }
  //}

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
  def correctClass(s: String) = s match {
    case "read" => haveClass[ReadRequest]
    case "poll" => haveClass[PollRequest]
    case "write" => haveClass[WriteRequest]
    case "response" => haveClass[ResponseRequest]
    case "call" => haveClass[CallRequest]
    case "delete" => haveClass[DeleteRequest]
    case "cancel" => haveClass[CancelRequest]
    case "subscription" => haveClass[SubscriptionRequest]
  }
}
