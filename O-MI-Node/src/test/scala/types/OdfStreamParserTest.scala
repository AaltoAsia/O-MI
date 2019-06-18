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
import java.time.OffsetDateTime
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
  val testTimeStr ="2017-05-11T15:44:55+03:00"
  val testTime: Timestamp = Timestamp.from(OffsetDateTime.parse(testTimeStr).toInstant())
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
    "parse correctly InfoItem element" >> {
      val objPath = Path("Objects/TestObj")
      val iiPath = objPath / "test"
      "from correct events" >> {
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
            Vector(DoubleValue(1.3,testTime),DoubleValue(1.2,testTime))
          )
        )
      }
    }
    /*
    "parse correctly MetaData element" >> {
    }
    "parse correctly Object element" >> {
    }
    "parse correctly Objects element" >> {
    }
    "parse correctly whole O-DF structure" >> {
    }*/
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
