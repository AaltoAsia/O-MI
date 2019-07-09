/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2019 Aalto University.                                        +
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
package odf
package parser

import java.sql.Timestamp
import java.time.{ZoneOffset,OffsetDateTime, ZoneId}
import javax.xml.datatype.DatatypeFactory
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.util.Try

import akka.NotUsed
import akka.util._
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import types._
import odf._
import types.OmiTypes.parser.{ResultEventBuilder,OdfRequestEventBuilder}
import utils._


import org.specs2.matcher.XmlMatchers._
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.specification.{Scope,AfterAll}

class OdfEventBuilderTest extends Specification {
  val testTimeStr ="2017-05-11T15:44:55.00+03:00"
  val gc = DatatypeFactory.newInstance().newXMLGregorianCalendar(testTimeStr).toGregorianCalendar()
  val testTime: Timestamp = Timestamp.from(gc.toInstant())
  sequential
  "EventBuilders" >> {
    "handle correctly description element" >> {

      "from correct events" >> {
        buildFromEvents(
          new DescriptionEventBuilder(None,testTime),
          Vector(
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description")
          )
        ) must beSuccessfulTry.withValue(Description("testing",Some("ENG")))
      }
      "with unexpect event after completion" >> {
        buildFromEvents(
          new DescriptionEventBuilder(None,testTime),
          Vector(
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            Characters("testing2"),
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect text content after complete description.")
      }
      "with unexpect event before element" >> {
        buildFromEvents(
          new DescriptionEventBuilder(None,testTime),
          Vector(
            Characters("testing2"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect text content before expected description element.")
      }
      "with unexpect event in description " >> {
        buildFromEvents(
          new DescriptionEventBuilder(None,testTime),
          Vector(
            StartElement("description",List(Attribute("lang", "ENG"))),
            StartElement("error",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of error element when expected text content.")
      }
    }
    "handle correctly id element" >> {
      "from correct events" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id"),
            Characters("testing"),
            EndElement("id")
          )
        ) must beSuccessfulTry.withValue(QlmID("testing"))
      }
      "from correct events with attributes" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id",List(Attribute("tagType", "ENG"),Attribute("idType", "ENG"),Attribute("startDate", testTimeStr),Attribute("endDate", testTimeStr))),
            Characters("testing"),
            EndElement("id")
          )
        ) must beSuccessfulTry.withValue(QlmID("testing",Some("ENG"),Some("ENG"),Some(testTime),Some(testTime)))
      }
      "from correct events with incorrect startDate attribute" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id",List(Attribute("startDate","error"))),
            Characters("testing"),
            EndElement("id")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Invalid startDate attribute for id element.")
      }
      "from correct events with incorrect endDate attribute" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id",List(Attribute("endDate","error"))),
            Characters("testing"),
            EndElement("id")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Invalid endDate attribute for id element.")
      }
      "with unexpected event before id element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            EndDocument,
            StartElement("id"),
            Characters("testing"),
            EndElement("id")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of document before expected id, name or altName element.")
      }
      "with unexpected event before content in id element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id"),
            EndElement("id"),
            Characters("testing"),
            EndElement("id")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of id element when expected text content.")
      }
      
      "with unexpected event after content in id element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id"),
            Characters("testing"),
            EndElement("ids"),
            EndElement("id")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of ids element before expected closing of id")
      }
      "with unexpected event after complete id element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("ids"),
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of ids element after complete id element.")
      }
    }
    "handle correctly name element" >> {
      "from correct events" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name"),
            Characters("testing"),
            EndElement("name")
          )
        ) must beSuccessfulTry.withValue(QlmID("testing"))
      }
      "from correct events with attributes" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name",List(Attribute("tagType", "ENG"),Attribute("idType", "ENG"),Attribute("startDate", "2017-05-11T15:44:55+03:00"),Attribute("endDate", "2017-05-11T15:44:55+03:00"))),
            Characters("testing"),
            EndElement("name")
          )
        ) must beSuccessfulTry.withValue(QlmID("testing",Some("ENG"),Some("ENG"),Some(testTime),Some(testTime)))
      }
      "from correct events with incorrect startDate attribute" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name",List(Attribute("startDate","error"))),
            Characters("testing"),
            EndElement("name")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Invalid startDate attribute for name element.")
      }
      "from correct events with incorrect endDate attribute" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name",List(Attribute("endDate","error"))),
            Characters("testing"),
            EndElement("name")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Invalid endDate attribute for name element.")
      }
      "with unexpected event before name element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            EndDocument,
            StartElement("name"),
            Characters("testing"),
            EndElement("name")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of document before expected id, name or altName element.")
      }
      "with unexpected event before content in name element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name"),
            EndElement("name"),
            Characters("testing"),
            EndElement("name")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of name element when expected text content.")
      }
      
      "with unexpected event after content in name element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name"),
            Characters("testing"),
            EndElement("names"),
            EndElement("name")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of names element before expected closing of name")
      }
      "with unexpected event after complete name element" >> {
        buildFromEvents(
          new IdEventBuilder(None,testTime),
          Vector(
            StartElement("name"),
            Characters("testing"),
            EndElement("name"),
            StartElement("names"),
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of names element after complete name element.")
      }
    }
    "parse correctly value element" >> {
      "from correct events with double value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(DoubleValue(1.2,testTime))
      }
      "from correct events with int value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:int"))),
            Characters("12"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(IntValue(12,testTime))
      }
      "from correct events with string value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value" ),
            Characters("test"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(StringPresentedValue("test",testTime))
      }
      "from correct events with boolean value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:boolean"))),
            Characters("true"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(BooleanValue(true,testTime))
      }
      "from correct events with unixTime" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("unixTime",s"${testTime.getTime / 1000}"))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(DoubleValue(1.2,testTime))
      }
      "from correct events with dateTime" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime",testTimeStr))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(DoubleValue(1.2,testTime))
      }
      "from correct events with unixTime and dateTime" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime",testTimeStr), Attribute("unixTime",s"${testTime.getTime / 1000}"))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beSuccessfulTry.withValue(DoubleValue(1.2,testTime))
      }
      "from correct events with invalid unixTime" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("unixTime",s"error${testTime.getTime / 1000}"))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Invalid unixTime for value.")
      }
      "from correct events with invalid dateTime" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime","error" + testTimeStr))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Invalid dateTime for value.")
      }
      "from unexpected event before value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            EndElement("error"),
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime",testTimeStr))),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of error element before expected value element.")
      }
      "from unexpected event before content in value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime",testTimeStr))),
            EndElement("error"),
            Characters("1.2"),
            EndElement("value")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of error element when expected text content.")
      }
      "from unexpected event before closing tag of value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime",testTimeStr))),
            Characters("1.2"),
            EndElement("error"),
            EndElement("value")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of error element before expected closing of Value.")
      }
      "from unexpected event after complete value" >> {
        buildFromEvents(
          new ValueEventBuilder(None,testTime),
          Vector(
            StartElement("value", List(Attribute("type","xs:double"), Attribute("dateTime",testTimeStr))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("error")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect end of error element after complete value.")
      }
    }
    "handle correctly InfoItem element" >> {
      val objPath = Path("Objects/TestObj")
      val iiPath = objPath / "test"
      val mdPath = iiPath / "MetaData"
      "from all correct events" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("name"),
            Characters("testing2"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("description",List(Attribute("lang", "FIN"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.3"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector(QlmID("testing2"),QlmID("testing1")),
            Set(Description("testing",Some("ENG")),Description("testing",Some("FIN"))),
            Vector(DoubleValue(1.3,testTime),DoubleValue(1.2,testTime)),
          Some(MetaData(
            Vector(
              InfoItem(
                "test2",
                mdPath/"test2"
              ),
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          ))
          )
        )
      }
      "from correct events with description and value" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set(Description("testing",Some("ENG"))),
            Vector(DoubleValue(1.2,testTime))
          )
        )
      }
      "from correct events with description and MetaData" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set(Description("testing",Some("ENG"))),
            Vector.empty,
          Some(MetaData(
            Vector(
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          ))
          )
        )
      }
      "from correct events with MetaData and value" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set.empty,
            Vector(DoubleValue(1.2,testTime)),
            Some(MetaData(
              Vector(
                InfoItem(
                  "test1",
                  mdPath/"test1"
                )
              )
            )),
            Map.empty
          )
        )
      }
      "from correct events with description, MetaData and value" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set(Description("testing",Some("ENG"))),
            Vector(DoubleValue(1.2,testTime)),
          Some(MetaData(
            Vector(
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          ))
          )
        )
      }
      "from correct events with additional name and value" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector(QlmID("testing1")),
            Set.empty,
            Vector(DoubleValue(1.2,testTime))
          )
        )
      }
      "from correct events with additional name and MetaData" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
            InfoItem(
              "test",
              iiPath,
              Some("test"),
              Vector(QlmID("testing1")),
              Set.empty,
              Vector.empty,
              Some(MetaData(
                Vector(
                  InfoItem(
                    "test1",
                    mdPath/"test1"
                  )
                )
              )),
              Map.empty
          )
        )
      }
      "from correct events with additional name, value and MetaData" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector(QlmID("testing1")),
            Set.empty,
            Vector(DoubleValue(1.2,testTime)),
          Some(MetaData(
            Vector(
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          )),
            Map.empty
          )
        )
      }
      "from correct events with additional name and description" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector(QlmID("testing1")),
            Set(Description("testing",Some("ENG"))),
            Vector.empty
          )
        )
      }
      "from correct events with additional name, description and MetaData" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector(QlmID("testing1")),
            Set(Description("testing",Some("ENG"))),
            Vector.empty,
          Some(MetaData(
            Vector(
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          ))
          )
        )
      }
      "from correct events with additional name" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector(QlmID("testing1")),
            Set.empty,
            Vector.empty
          )
        )
      }
      "from correct events with description" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set(Description("testing",Some("ENG"))),
            Vector.empty
          )
        )
      }
      "from correct events with value" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set.empty,
            Vector(DoubleValue(1.2,testTime))
          )
        )
      }
      "from correct events with MetaData" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set.empty,
            Vector.empty,
          Some(MetaData(
            Vector(
              InfoItem(
                "test2",
                mdPath/"test2"
              ),
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          )),
            Map.empty
          )
        )
      }
      "from correct events with no sub elements" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            EndElement("InfoItem")
          )
        ) must beSuccessfulTry.withValue(
          InfoItem(
            "test",
            iiPath,
            Some("test"),
            Vector.empty,
            Set.empty,
            Vector.empty
          )
        )
      }
      "with unexpected event after addional name element" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("error"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of error element after additional names in InfoItem with name: test.")
      }
      "with unexpected event after description" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("error"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of error element after descriptions in InfoItem with name: test.")
      }
      "with unexpected event after MetaData" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            StartElement("error"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of error element after MetaData in InfoItem with name: test.")
      }
      "with unexpected event after value" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            StartElement("error"),
            EndElement("InfoItem")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of error element after values in InfoItem with name: test.")
      }
      "with unexpected event after complete InfoItem" >> {
        buildFromEvents(
          new InfoItemEventBuilder(objPath,None,testTime),
          Vector(
            StartElement("InfoItem", List(Attribute("name","test"),Attribute("type","test"))),
            StartElement("name"),
            Characters("testing1"),
            EndElement("name"),
            StartElement("description",List(Attribute("lang", "ENG"))),
            Characters("testing"),
            EndElement("description"),
            StartElement("value", List(Attribute("type","xs:double"))),
            Characters("1.2"),
            EndElement("value"),
            EndElement("InfoItem"),
            StartElement("error")
          )
        ) must beFailedTry.withThrowable[ODFParserError]("O-DF Parser error: Unexpect start of error element after complete InfoItem with name: test.")
      }
    }
    "parse correctly MetaData element" >> {
      val iiPath = Path("Objects/TestObj/test")
      val mdPath = iiPath / "MetaData"
      "from correct events" >> {
        buildFromEvents(
          new MetaDataEventBuilder(iiPath,None,testTime),
          Vector(
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData")
          )
        ) must beSuccessfulTry.withValue(
          MetaData(
            Vector(
              InfoItem(
                "test2",
                mdPath/"test2"
              ),
              InfoItem(
                "test1",
                mdPath/"test1"
              )
            )
          )
        )
      }
      "with unexpected event after complete MetaData" >> {
        buildFromEvents(
          new MetaDataEventBuilder(iiPath,None,testTime),
          Vector(
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData"),
            EndElement("error")
          )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect end of error element after complete MetaData in InfoItem ${iiPath.toString}.")
      }
      "with unexpected event in MetaData" >> {
        buildFromEvents(
          new MetaDataEventBuilder(iiPath,None,testTime),
          Vector(
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            EndElement("error"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData")
          )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect end of error element when expected InfoItem elements in MetaData in InfoItem ${iiPath.toString}.")
      }
      "with unexpected event before MetaData" >> {
        buildFromEvents(
          new MetaDataEventBuilder(iiPath,None,testTime),
          Vector(
            EndElement("error"),
            StartElement("MetaData"),
            StartElement("InfoItem", List(Attribute("name","test1"))),
            EndElement("InfoItem"),
            StartElement("InfoItem", List(Attribute("name","test2"))),
            EndElement("InfoItem"),
            EndElement("MetaData")
          )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect end of error element before expected MetaData element in InfoItem ${iiPath.toString}.")
      }
    }
    "handle correctly Object element" >> {
      "with correct events with all possible sub elements" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("description"),
            Characters("testing"),
            EndElement("description"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("SubObject"),
            EndElement("id"),
            StartElement("InfoItem", List(Attribute("name","II2"))),
            EndElement("InfoItem"),
            EndElement("Object"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test"),
            Set(Description("testing"))
          )
        )
      }
      "with object with only id" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test")
          )
        )
      }
      "with description" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("description"),
            Characters("testing"),
            EndElement("description"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test"),
            Set(Description("testing"))
          )
        )
      }
      "with description and InfoItem" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("description"),
            Characters("testing"),
            EndElement("description"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test"),
            Set(Description("testing"))
          )
        )
      }
      "with description and sub Object" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("description"),
            Characters("testing"),
            EndElement("description"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test"),
            Set(Description("testing"))
          )
        )
      }
      "with description, InfoItem and sub Object" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("description"),
            Characters("testing"),
            EndElement("description"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test"),
            Set(Description("testing"))
          )
        )
      }
      "with InfoItem" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test")
          )
        )
      }
      "with InfoItem and sub Object" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test")
          )
        )
      }
      "with sub Object" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object"),
            EndElement("Object")
            )
        ) must beSuccessfulTry.withValue(
          Object(
            Vector(QlmID("testing")),
            objPath,
            Some("test")
          )
        )
      }
      "without id" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            EndElement("Object")
            )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect end of Object element before at least one Id element in Object inside parent ${parentPath.toString}.")
      }
      "with unexpect event after id" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            Characters("error"),
            EndElement("Object")
            )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect text content after id in Object with id ${objPath.last.toString}.")
      }
      "with unexpect event after description" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("description"),
            Characters("testing"),
            EndElement("description"),
            Characters("error"),
            EndElement("Object")
            )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect text content after description inside Object with id ${objPath.last.toString}.")
      }
      "with unexpect event after InfoItem" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            Characters("error"),
            EndElement("Object")
            )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect text content after InfoItem inside Object with id ${objPath.last.toString}.")
      }
      "with unexpect event after sub Object" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object"),
            Characters("error"),
            EndElement("Object")
            )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect text content after Object inside Object with id ${objPath.last.toString}.")
      }
      "with unexpect event after complete Object" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ObjectEventBuilder(parentPath,None,testTime),
          Vector(
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("Object", List(Attribute("type","test"))),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            EndElement("Object"),
            EndElement("Object"),
            Characters("error")
            )
        ) must beFailedTry.withThrowable[ODFParserError](s"O-DF Parser error: Unexpect text content after complete Object with id ${objPath.last.toString}.")
      }
    }
    "handle correctly whole O-DF structure" >> {
      "with simple structure with only InfoItems and Object s" >> {
        val parentPath = Path("Objects/TestObj")
        val objPath = parentPath / "testing"
        buildFromEvents(
          new ODFEventBuilder(None,testTime),
          Vector(
            StartElement("Objects", List(Attribute("version","2.0")), None, Some("http://www.opengroup.org/xsd/odf/2.0/")),
            StartElement("Object"),
            StartElement("id"),
            Characters("testing"),
            EndElement("id"),
            StartElement("InfoItem", List(Attribute("name","II1"))),
            EndElement("InfoItem"),
            StartElement("Object"),
            StartElement("id"),
            Characters("SubObject"),
            EndElement("id"),
            StartElement("InfoItem", List(Attribute("name","II2"))),
            EndElement("InfoItem"),
            EndElement("Object"),
            EndElement("Object"),
            EndElement("Objects")
          )
        ) must beSuccessfulTry.withValue(
          ImmutableODF(
            Vector(
              Objects(Some("2.0")),
              InfoItem(Path("Objects/testing/II1"),Vector.empty),
              InfoItem(Path("Objects/testing/SubObject/II2"),Vector.empty)
              )
          )
        )
      }
    }

  }
  def runBuilderWithEvents( builder: EventBuilder[_], events: Iterable[ParseEvent] ): Try[EventBuilder[_]] = {
    Try{
      var runingBuilder: EventBuilder[_] = builder
      events.foreach{
        event =>
          runingBuilder = runingBuilder.parse(event)
      }
      runingBuilder
    }
  }
  def buildFromEvents( builder: EventBuilder[_], events: Iterable[ParseEvent] ): Try[_] = {
    Try{
      var runingBuilder: EventBuilder[_] = builder
      events.foreach{
        event =>
          runingBuilder = runingBuilder.parse(event)
      }
      runingBuilder.build
    }
  }
}
