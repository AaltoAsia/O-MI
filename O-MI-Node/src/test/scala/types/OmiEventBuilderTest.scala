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
package OmiTypes
package parser

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.Instant
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap
import scala.concurrent.duration._
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
import OmiTypes._
import types.OmiTypes.parser.{ResultEventBuilder,OdfRequestEventBuilder}
import utils._


import org.specs2.matcher.XmlMatchers._
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.specification.{Scope,AfterAll}
import scala.util.Try


class OMIEventBuilderTest extends Specification {
  val testTimeStr ="2017-05-11T15:44:55.001Z"
  val testTime: Timestamp = Timestamp.from(OffsetDateTime.parse(testTimeStr).toInstant())
  val testTTL = 30.seconds
  val testCallbackStr = "http://localhost/" 
  sequential
  "EventBuilders" >> {
    "handle correctly requestID element" >> {

      "with correct events" >>{
        buildFromEvents( 
          new RequestIdEventBuilder(None,testTime),
          Vector(
            StartElement("requestID"),
            Characters("51557117"),
            EndElement("requestID")
          )
        ) must beSuccessfulTry.withValue(51557117L)
      }
      "with invalid id, type of string" >>{
        buildFromEvents( 
          new RequestIdEventBuilder(None,testTime),
          Vector(
            StartElement("requestID"),
            Characters("51ouou"),
            EndElement("requestID")
          )
        ) must beFailedTry.withThrowable[OMIParserError](s"O-MI Parser error: RequestId 51ouou was not type Long.")
      }
      "with unexpected event after content" >>{
        buildFromEvents( 
          new RequestIdEventBuilder(None,testTime),
          Vector(
            StartElement("requestID"),
            Characters("51557117"),
            EndElement("error"),
            EndElement("requestID")
          )
        ) must beFailedTry.withThrowable[OMIParserError](s"O-MI Parser error: Unexpect end of error element before expected closing of requestID.")
      }
      "with unexpected event before content" >>{
        buildFromEvents( 
          new RequestIdEventBuilder(None,testTime),
          Vector(
            StartElement("requestID"),
            EndElement("error"),
            Characters("51557117"),
            EndElement("requestID")
          )
        ) must beFailedTry.withThrowable[OMIParserError](s"O-MI Parser error: Unexpect end of error element when expected text content.")
      }
      "with unexpected event before opening element" >>{
        buildFromEvents( 
          new RequestIdEventBuilder(None,testTime),
          Vector(
            EndElement("error"),
            StartElement("requestID"),
            Characters("51557117"),
            EndElement("requestID")
          )
        ) must beFailedTry.withThrowable[OMIParserError](s"O-MI Parser error: Unexpect end of error element before expected requestID element.")
      }
      "with unexpected event after complete requestID" >>{
        buildFromEvents( 
          new RequestIdEventBuilder(None,testTime),
          Vector(
            StartElement("requestID"),
            Characters("51557117"),
            EndElement("requestID"),
            EndElement("error")
          )
        ) must beFailedTry.withThrowable[OMIParserError](s"O-MI Parser error: Unexpect end of error element after complete requestID element.")
      }
    }

    "handle correctly write request" >>{
      "with correct events" >>{
        buildFromEvents( 
          new WriteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("write", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("write")
          )
        ) must beSuccessfulTry.withValue(
          WriteRequest(
            testODF,
            None,
            testTTL
          )
        )
      }
      "with callback" >>{
        buildFromEvents( 
          new WriteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("write", List(Attribute("msgformat","odf"),Attribute("callback",testCallbackStr))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("write")
          )
        ) must beSuccessfulTry.withValue(
          WriteRequest(
            testODF,
            Some(RawCallback(testCallbackStr)),
            testTTL
          )
        )
      }
      "with invalid msgformat" >>{
        buildFromEvents( 
          new WriteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("write", List(Attribute("msgformat","error"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("write")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unknown msgformat: error. Do not know how to parse content inside msg element.")
      }
      "with unexpected event before msg" >>{
        buildFromEvents( 
          new WriteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("write", List(Attribute("msgformat","odf"))),
            StartElement("error"),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("write")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element before msg element.")
      }
      "with unexpected event after msg" >>{
        buildFromEvents( 
          new WriteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("write", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            StartElement("error"),
            EndElement("write")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element before write element closing.")
      }
      "with unexpected event in msg" >>{
        buildFromEvents( 
          new WriteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("write", List(Attribute("msgformat","odf"))),
            StartElement("msg"),
          ) ++ testODFEvents ++ Vector(
            StartElement("error"),
            EndElement("msg"),
            EndElement("write")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element in msg element.")
      }
    }
    
    "handle correctly call element" >>{
      "with correct events" >>{
        buildFromEvents( 
          new CallEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("call", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("call")
          )
        ) must beSuccessfulTry.withValue(
          CallRequest(
            testODF,
            None,
            testTTL
          )
        )
      }
      "with callback" >>{
        buildFromEvents( 
          new CallEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("call", List(Attribute("msgformat","odf"),Attribute("callback",testCallbackStr))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("call")
          )
        ) must beSuccessfulTry.withValue(
          CallRequest(
            testODF,
            Some(RawCallback(testCallbackStr)),
            testTTL
          )
        )
      }
      "with invalid msgformat" >>{
        buildFromEvents( 
          new CallEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("call", List(Attribute("msgformat","error"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("call")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unknown msgformat: error. Do not know how to parse content inside msg element.")
      }
      "with unexpected event before msg" >>{
        buildFromEvents( 
          new CallEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("call", List(Attribute("msgformat","odf"))),
            StartElement("error"),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("call")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element before msg element.")
      }
      "with unexpected event after msg" >>{
        buildFromEvents( 
          new CallEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("call", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            StartElement("error"),
            EndElement("call")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element before call element closing.")
      }
      "with unexpected event in msg" >>{
        buildFromEvents( 
          new CallEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("call", List(Attribute("msgformat","odf"))),
            StartElement("msg"),
          ) ++ testODFEvents ++ Vector(
            StartElement("error"),
            EndElement("msg"),
            EndElement("call")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element in msg element.")
      }
    }
    "handle correctly delete element" >>{
      "with correct events" >>{
        buildFromEvents( 
          new DeleteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("delete", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("delete")
          )
        ) must beSuccessfulTry.withValue(
          DeleteRequest(
            testODF,
            None,
            testTTL
          )
        )
      }
      "with callback" >>{
        buildFromEvents( 
          new DeleteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("delete", List(Attribute("msgformat","odf"),Attribute("callback",testCallbackStr))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("delete")
          )
        ) must beSuccessfulTry.withValue(
          DeleteRequest(
            testODF,
            Some(RawCallback(testCallbackStr)),
            testTTL
          )
        )
      }
      "with invalid msgformat" >>{
        buildFromEvents( 
          new DeleteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("delete", List(Attribute("msgformat","error"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("delete")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unknown msgformat: error. Do not know how to parse content inside msg element.")
      }
      "with unexpected event before msg" >>{
        buildFromEvents( 
          new DeleteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("delete", List(Attribute("msgformat","odf"))),
            StartElement("error"),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("delete")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element before msg element.")
      }
      "with unexpected event after msg" >>{
        buildFromEvents( 
          new DeleteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("delete", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            StartElement("error"),
            EndElement("delete")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element before delete element closing.")
      }
      "with unexpected event in msg" >>{
        buildFromEvents( 
          new DeleteEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("delete", List(Attribute("msgformat","odf"))),
            StartElement("msg"),
          ) ++ testODFEvents ++ Vector(
            StartElement("error"),
            EndElement("msg"),
            EndElement("delete")
          )
        ) must beFailedTry.withThrowable[OMIParserError]("O-MI Parser error: Unexpect start of error element in msg element.")
      }
    }
    "handle correctly read element" >>{
      "without any additional attributes" >>{
        buildFromEvents( 
          new ReadEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("read", List(Attribute("msgformat","odf"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("read")
          )
        ) must beSuccessfulTry.withValue(
          ReadRequest(
            testODF,
            ttl = testTTL
          )
        )
      }
      "with interval" >>{
        buildFromEvents( 
          new ReadEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("read", List(Attribute("msgformat","odf"),Attribute("interval","1"))),
            StartElement("msg")
          ) ++ testODFEvents ++ Vector(
            EndElement("msg"),
            EndElement("read")
          )
        ) must beSuccessfulTry.withValue(
          SubscriptionRequest(
            1.seconds,
            testODF,
            ttl = testTTL
          )
        )
      }
      "with requestID" >>{
        buildFromEvents( 
          new ReadEventBuilder(testTTL, None,testTime),
          Vector(
            StartElement("read"),
            StartElement("requestID"),
            Characters("1313"),
            EndElement("requestID"),
            EndElement("read")
          )
        ) must beSuccessfulTry.withValue(
          PollRequest(
            None,
            Vector(1313),
            ttl = testTTL
          )
        )
      }
    }
    /*
    "handle correctly cancel element" >>{}
    */
  }
  def testODFEvents = Vector(
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
  def testODF = ImmutableODF(
            Vector(
              Objects(Some("2.0")),
              InfoItem(Path("Objects/testing/II1"),Vector.empty),
              InfoItem(Path("Objects/testing/SubObject/II2"),Vector.empty)
              )
          )
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
