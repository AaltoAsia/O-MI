package responses

import org.specs2.mutable._
import scala.io.Source
import responses._
import parsing._
import parsing.Types._
import parsing.Types.Path._
import database._
import parsing.OdfParser._
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import scala.xml.Utility.trim
import scala.xml.XML
import testHelpers.BeforeAll

class SubscriptionTest extends Specification with BeforeAll {

  def beforeAll = {
    val calendar = Calendar.getInstance()
    calendar.setTime(new Date(1421775723))
    calendar.set(Calendar.HOUR_OF_DAY, 12)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    SQLite.clearDB()
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
      SQLite.remove(path)
      SQLite.set(new DBSensor(path, value, testtime))
    }

    var count = 0

    SQLite.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata) {
      SQLite.set(new DBSensor(Path("Objects/ReadTest/SmartOven/Temperature"), value, new java.sql.Timestamp(date.getTime + count)))
      count = count + 1000
    }

    lazy val simpletestfile = Source.fromFile("src/test/resources/responses/SubscriptionRequest.xml").getLines.mkString("\n")
    val parserlist = OmiParser.parse(simpletestfile)

    val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[Subscription])

    lazy val simpletestfilecallback = Source.fromFile("src/test/resources/responses/SubscriptionRequestWithCallback.xml").getLines.mkString("\n")
    val parserlistcallback = OmiParser.parse(simpletestfilecallback)

    val (requestIDcallback, xmlreturncallback) = OMISubscription.setSubscription(parserlistcallback.head.asInstanceOf[Subscription])
  }

  "Subscription response" should {
    sequential
    "Return with just a requestId when subscribed" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)

      val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[Subscription])

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>{ requestID }</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      trim(xmlreturn.head).toString == trim(correctxml).toString

    }

    "Return with no values when interval is larger than time elapsed and no callback given" in {

      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/SubRetrieve.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)

      val subxml = Read.OMIReadResponse(parserlist.head.asInstanceOf[OneTimeRead]).head

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>0</omi:requestId>
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

      trim(subxml.head).toString === trim(correctxml).toString

    }

    "Return with right values and requestId in subscription generation" in {

      val subxml = OMISubscription.OMISubscriptionResponse(1)

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
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
        </omi:omiEnvelope>

      trim(subxml.head).toString == trim(correctxml).toString

    }

    "Return error code when asked for nonexisting infoitem" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/BuggyRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)

      val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[Subscription])

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="400" description="No InfoItems found in the paths"></omi:return>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      (requestID, trim(xmlreturn.head).toString) == (-1, trim(correctxml).toString)
    }

    "Return with error when subscription doesn't exist" in {
      val xmlreturn = OMISubscription.OMISubscriptionResponse(1234)

      val correctxml =
        <omi:omiEnvelope xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0" xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <omi:response>
            <omi:result>
              <omi:return returnCode="400" description="A subscription with this id has expired or doesn't exist">
              </omi:return>
              <omi:requestId>1234</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      trim(xmlreturn.head).toString == trim(correctxml).toString
    }
    "Return polled data only once" in {
      val testTime = new Date().getTime - 10000
      val testSub = SQLite.saveSub(new database.DBSub(Array(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")), 60.0, 1, None, Some(new java.sql.Timestamp(testTime))))
      //      SQLite.startBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))

      SQLite.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))
      SQLite.get(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")) === None

      (0 to 10).foreach(n =>
        SQLite.set(new DBSensor(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime + n * 1000))))
      val test = OMISubscription.odfGeneration(testSub)
      val dataLength = test.\\("value").length
      println("\n\n\n\n")
      println( test.\\("value"))
      println("\n\n\n\n")
      dataLength must be_>=(10)
      val test2 = OMISubscription.odfGeneration(testSub)
      val newDataLength = test2.\\("value").length
      newDataLength must be_<=(3)

      SQLite.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))

      //      SQLite.stopBuffering(Path("Objects/SubscriptionTest/SmartOven/pollingtest"))
      SQLite.removeSub(testSub)
    }
    "TTL should decrease by some multiple of interval" in {
      val testTime = new Date().getTime - 10000
      val testSub = SQLite.saveSub(new database.DBSub(Array(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest")), 60.0, 3, None, Some(new java.sql.Timestamp(testTime))))
      val ttlFirst = SQLite.getSub(testSub).get.ttl
      ttlFirst === 60.0
      (0 to 10).foreach(n =>
        SQLite.set(new DBSensor(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime + n * 1000))))
      val test = OMISubscription.odfGeneration(testSub)
      val test2 = OMISubscription.odfGeneration(testSub)
      val ttlEnd = SQLite.getSub(testSub).get.ttl
      (ttlFirst - ttlEnd) % 3 === 0

      SQLite.remove(Path("Objects/SubscriptionTest/intervalTest/SmartOven/pollingtest"))
      SQLite.removeSub(testSub)
    }
    "Event based subscription without callback should return all the new values when polled" in {
      val testTime = new Date().getTime - 10000
      val testSub = SQLite.saveSub(new database.DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).foreach(n =>
        SQLite.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime - 5000 + n * 1000))))
      val test = OMISubscription.odfGeneration(testSub)
      test.\\("value").length === 6
      SQLite.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      SQLite.removeSub(testSub)

    }
    "Event based subscription withou callback should not return already polled data" in {
      val testTime = new Date().getTime - 10000
      val testSub = SQLite.saveSub(new database.DBSub(Array(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest")), 60.0, -1, None, Some(new java.sql.Timestamp(testTime))))
      (0 to 10).foreach(n =>
        SQLite.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime - 5000 + n * 1000))))
      val test = OMISubscription.odfGeneration(testSub)
      test.\\("value").length === 6
      val test2 = OMISubscription.odfGeneration(testSub)
      test2.\\("value").length === 0
      SQLite.set(new DBSensor(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"), "testvalue", new java.sql.Timestamp(new Date().getTime)))
      val test3 = OMISubscription.odfGeneration(testSub)
      test3.\\("value").length === 1
      
      SQLite.remove(Path("Objects/SubscriptionTest/eventTest/SmartOven/pollingtest"))
      SQLite.removeSub(testSub)
    }
    

  }

}




