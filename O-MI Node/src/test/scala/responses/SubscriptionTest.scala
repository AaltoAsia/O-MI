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

class SubscriptionTest extends Specification with Before {
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
  }

  "Subscription response" should {
    "Return with just a requestId when subscribed" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/SubscriptionRequest.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)

      val (requestID, xmlreturn) = OMISubscription.setSubscription(parserlist.head.asInstanceOf[Subscription])

      val correctxml =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
          <omi:response>
            <omi:result>
              <omi:return returnCode="200"></omi:return>
              <omi:requestId>{ requestID }</omi:requestId>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      trim(xmlreturn.head).toString == trim(correctxml).toString
    }

    "Return with historical data when no callback was provided" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/Subretrieve.xml").getLines.mkString("\n")
      val parserlist = OmiParser.parse(simpletestfile)
      println(parserlist)

      1 == 1
    }
  }

}




