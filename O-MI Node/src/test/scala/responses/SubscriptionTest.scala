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
//import org.specs2.specification.{Step, Fragments}

//from http://stackoverflow.com/questions/16936811/execute-code-before-and-after-specification
//trait BeforeAllAfterAll extends Specification {
//  override def map(fragments: =>Fragments) = 
//    Step(beforeAll) ^ fragments ^ Step(afterAll)
//
//  protected def beforeAll()
//  protected def afterAll()
//}

class SubscriptionTest extends Specification with Before {
//  def afterAll:Unit = ()
  def before = {
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
              <omi:requestId>{requestID}</omi:requestId>
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
      val testTime =new Date().getTime - 10000
      val testSub = SQLite.saveSub(new database.DBSub(Array(Path("Objects/ReadTest/SmartOven/pollingtest")),60.0,1,None,Some(new java.sql.Timestamp(testTime))))
//      SQLite.startBuffering(Path("Objects/ReadTest/SmartOven/pollingtest"))
      
      SQLite.remove(Path("Objects/ReadTest/SmartOven/pollingtest"))
      SQLite.get(Path("Objects/ReadTest/SmartOven/pollingtest")) === None
      
      (0 to 11).foreach(n=>
        SQLite.set(new DBSensor(Path("Objects/ReadTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime+n*1000))))
      val test = OMISubscription.odfGeneration(testSub)
      val intervalsPassed = (new Date().getTime - testTime)/ 1000
      test.\\("value").length === intervalsPassed.min(12)
      val test2 = OMISubscription.odfGeneration(testSub)
      test2.\\("value").length === (SQLite.getSub(testSub).get.startTime.getTime - testTime)/1000 - intervalsPassed

      SQLite.remove(Path("Objects/ReadTest/SmartOven/pollingtest"))

//      SQLite.stopBuffering(Path("Objects/ReadTest/SmartOven/pollingtest"))
      SQLite.removeSub(testSub)
    }
    "TTL should decrease by some multiple of interval" in {
      val testTime =new Date().getTime - 10000
      val testSub = SQLite.saveSub(new database.DBSub(Array(Path("Objects/ReadTest/SmartOven/pollingtest")),60.0,3,None,Some(new java.sql.Timestamp(testTime))))
      val ttlFirst = SQLite.getSub(testSub).get.ttl
      ttlFirst === 60.0
      (0 to 10).foreach(n=>
        SQLite.set(new DBSensor(Path("Objects/ReadTest/SmartOven/pollingtest"), n.toString(), new java.sql.Timestamp(testTime+n*1000))))
      val test = OMISubscription.odfGeneration(testSub)
      val test2 = OMISubscription.odfGeneration(testSub)
      val ttlEnd = SQLite.getSub(testSub).get.ttl
      (ttlFirst-ttlEnd) % 3 === 0
      
      SQLite.remove(Path("Objects/ReadTest/SmartOven/pollingtest"))
      SQLite.removeSub(testSub)
    }

  }

}




