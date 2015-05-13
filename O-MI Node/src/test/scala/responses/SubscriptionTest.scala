package responses

import org.specs2.mutable._
import org.specs2.matcher.XmlMatchers._
import scala.io.Source
import responses._
import responses.Common._
import parsing._
import parsing.Types._
import parsing.Types.Path._
import parsing.Types.OmiTypes._
import database._
import parsing.OdfParser._
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone
import java.text.SimpleDateFormat;
import scala.xml.Utility.trim
import scala.xml.XML
import testHelpers.BeforeAfterAll
import scala.concurrent.{ Await, Future }

class SubscriptionTest extends Specification with BeforeAfterAll {
  sequential

  implicit val dbConnection = new TestDB("subscription-response-test")
  val subsResponseGen = new OMISubscription.SubscriptionResponseGen
  val pollResponseGen = new OMISubscription.PollResponseGen()

  def beforeAll = {
    val calendar = Calendar.getInstance()
    val timeZone = TimeZone.getTimeZone("Etc/GMT+2")
    calendar.setTimeZone(timeZone)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    dbConnection.clearDB()
    val testData = Map(
      Path("Objects/ReadTest/Refrigerator123/PowerConsumption") -> "0.123",
      Path("Objects/ReadTest/Refrigerator123/RefrigeratorDoorOpenWarning") -> "door closed",
      Path("Objects/ReadTest/Refrigerator123/RefrigeratorProbeFault") -> "Nothing wrong with probe",
      Path("Objects/ReadTest/RoomSensors1/Temperature/Inside") -> "21.2",
      Path("Objects/ReadTest/RoomSensors1/CarbonDioxide") -> "too much",
      Path("Objects/ReadTest/RoomSensors1/Temperature/Outside") -> "12.2",
      Path("Objects/ReadTest/SmartCar/Fuel") -> "30")

    val intervaltestdata = List(
      "100",
      "102",
      "105",
      "109",
      "115",
      "117")

    for ((path, value) <- testData) {
      dbConnection.remove(path)
      dbConnection.set(new DBSensor(path, value, testtime))
    }

    var count = 1000000

    dbConnection.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata) {
      dbConnection.set(new DBSensor(Path("Objects/ReadTest/SmartOven/Temperature"), value, new java.sql.Timestamp(date.getTime + count)))
      count = count + 1000
    }

    //  lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
    //  val parserlist = OmiParser.parse(simpletestfile)

    //  val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[SubscriptionRequest])

    //  lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequestWithCallback.xml").getLines.mkString("\n")
    //  val parserlistcallback = OmiParser.parse(simpletestfilecallback)

    //  val (requestIDcallback, xmlreturncallback) = OMISubscription.setSubscription(parserlistcallback.head.asInstanceOf[SubscriptionRequest])
  }
  def afterAll ={
    dbConnection.destroy()
  }

  "Subscription response" should {
    "Return with just a requestId when subscribed" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val correctxml = {
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>{ requestID }</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>
      }

      //      trim(xmlreturn.head).toString() === trim(correctxml).toString()
      xmlreturn must beEqualToIgnoringSpace(correctxml)
    }

    "Return with no values when interval is larger than time elapsed and no callback given" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val subxml = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(requestID))))

      val correctxml = {
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>{ requestID }</omi:requestId>
              <omi:msg xsi:schemaLocation="odf.xsd odf.xsd" xmlns="odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>Refrigerator123</id>
                      <InfoItem name="PowerConsumption">
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>
      }

      //      trim(subxml.head) === trim(correctxml)
      subxml.head must beEqualToIgnoringSpace(correctxml)

    }

    "Return with right values and requestId in subscription generation" in {
      lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequestWithCallback.xml").getLines.mkString("\n")
      val parserlistcallback = OmiParser.parse(simpletestfilecallback)
      parserlistcallback.isRight === true
      val (requestIDcallback, xmlreturncallback) = OMISubscription.setSubscription(parserlistcallback.right.get.head.asInstanceOf[SubscriptionRequest])

      val subxml = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(requestIDcallback))))

      //      val correctxml =
      /*
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>1</omi:requestId>
              <omi:msg xsi:schemaLocation="odf.xsd odf.xsd" xmlns="odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>Refrigerator123</id>
                      <InfoItem name="PowerConsumption">
                        <value dateTime="1970-01-17T12:56:15.723">0.123</value>
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope> */

      //      trim(subxml.head) === trim(correctxml)

      subxml.\\("value").head.text === "0.123"
    }

    "Return error code when asked for nonexisting infoitem" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/BuggyRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true
      val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="400" description="No InfoItems found in the paths"></omi:return>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      requestID === -1
      xmlreturn.head must beEqualToIgnoringSpace(correctxml)
      //      (requestID, trim(xmlreturn.head)) === (-1, trim(correctxml))
    }

    "Return with error when subscription doesn't exist" in {
      val xmlreturn = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(1234))))

      val correctxml =
        <omi:omiEnvelope xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd">
          <omi:response>
            <omi:result>
              <omi:return returnCode="404" description="A subscription with this id has expired or doesn't exist">
              </omi:return><omi:requestId>1234</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      //      trim(xmlreturn.head) === trim(correctxml)
      xmlreturn.head must beEqualToIgnoringSpace(correctxml)
    }
    "Return polled data only once" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")), 60.0, 1, None, Some(new java.sql.Timestamp(testTime))))
      //      dbConnection.startBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))

      dbConnection.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))
      dbConnection.get(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")) === None

      (0 to 10).foreach(n =>
        dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime + n * 1000))))
      val test = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      val dataLength = test.\\("value").length
      dataLength must be_>=(10)
      val test2 = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      val newDataLength = test2.\\("value").length
      newDataLength must be_<=(3)

      dbConnection.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))

      //      dbConnection.stopBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))
      dbConnection.removeSub(testSub)
    }
    "TTL should decrease by some multiple of interval" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")), 60.0, 3, None, Some(new java.sql.Timestamp(testTime))))
      val ttlFirst = dbConnection.getSub(testSub).get.ttl
      ttlFirst === 60.0
      (0 to 10).foreach(n =>
        dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime + n * 1000))))
      val test = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      val test2 = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      val ttlEnd = dbConnection.getSub(testSub).get.ttl
      (ttlFirst - ttlEnd) % 3 === 0

      dbConnection.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))
      dbConnection.removeSub(testSub)
    }
    "Event based subscription without callback should return all the new values when polled" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).foreach(n =>
        dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime - 5000 + n * 1000))))
      val test = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test.\\("value").length === 6
      dbConnection.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      dbConnection.removeSub(testSub)

    }
    "Event based subscription without callback should not return already polled data" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).foreach(n =>
        dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime - 5000 + n * 1000))))
      val test = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test.\\("value").length === 6
      val test2 = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test2.\\("value").length === 0
      dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), "testvalue", new java.sql.Timestamp(new Date().getTime)))
      val test3 = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test3.\\("value").length === 1

      dbConnection.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      dbConnection.removeSub(testSub)
    }
    "Event based subscription should return new values only when the value changes" in {
      val testTime = new Date().getTime - 10000
      val testSub = dbConnection.saveSub(new DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).zip(Array(1, 1, 1, 2, 3, 3, 4, 5, 5, 6, 7)).foreach(n =>
        dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), n._2.toString(), new java.sql.Timestamp(testTime + n._1 * 900))))
      val test = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test.\\("value").length === 7
      val test2 = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test2.\\("value").length === 0
      dbConnection.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), "testvalue", new java.sql.Timestamp(new Date().getTime)))
      val test3 = omiResponse(pollResponseGen.genResult(PollRequest(10, None, Seq(testSub))))
      test3.\\("value").length === 1

      dbConnection.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      dbConnection.removeSub(testSub)
    }
    "Subscriptions should be removed from database when their ttl expires" in {
      val simpletestfile = Source.fromFile("src/test/resources/responses/subscription/SubscriptionRequest.xml").getLines.mkString("\n").replaceAll("""ttl="10.0"""", """ttl="1.0"""")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === false
      val testSub = OMISubscription.setSubscription(parserlist.right.get.head.asInstanceOf[SubscriptionRequest])._1
      val temp = dbConnection.getSub(testSub).get
      dbConnection.getSub(testSub) must beSome
      Thread.sleep(3000)
      dbConnection.getSub(testSub) must beNone
    }

  }

}




