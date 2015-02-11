package responses

import org.specs2.mutable._
import scala.io.Source
import responses._
import parsing._
import database._
import parsing.OdfParser._
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import scala.xml.Utility.trim
import scala.xml.XML

class ReadTest extends Specification with Before {
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
        Path("Objects/ReadTest/SmartCar/Fuel") -> "30"
    )

    val intervaltestdata = List(
                            "100",
                            "102",
                            "105",
                            "109",
                            "115",
                            "117")

    for ((path, value) <- testData){
        SQLite.remove(path)
        SQLite.set(new DBSensor(path, value, testtime))
    }

    var count = 0

    SQLite.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata){
        SQLite.set(new DBSensor(Path("Objects/ReadTest/SmartOven/Temperature"), value, new java.sql.Timestamp(date.getTime + count)))
        count = count + 1000
    }
  }
//  def after = {
//    SQLite.remove(Path("Objects/ReadTest"))
//  }

  "Read response" should {
    "Give correct XML when asked for multiple values" in {
        lazy val simpletestfile = Source.fromFile("src/test/scala/responses/SimpleXMLReadRequest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/scala/responses/correctXMLfirsttest.xml")
        val parserlist = OmiParser.parse(simpletestfile)
        
        trim(Read.OMIReadResponse(parserlist.head.asInstanceOf[OneTimeRead])) should be equalTo(trim(correctxmlreturn))    
    }

    "Give a history of values when begin and end is used" in {
        lazy val intervaltestfile = Source.fromFile("src/test/scala/responses/IntervalXMLTest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/scala/responses/CorrectIntervalXML.xml")
        val parserlist = OmiParser.parse(intervaltestfile)

        trim(Read.OMIReadResponse(parserlist.head.asInstanceOf[OneTimeRead])) should be equalTo(trim(correctxmlreturn))
    }

}

    "When given path ODFREST" should {

    "Give just the value when path ends with /value" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest/Refrigerator123/PowerConsumption/value"))

        RESTXML should be equalTo(Some(Left("0.123")))
    }

    "Give correct XML when asked with just the path and trailing /" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest/RoomSensors1/"))

        val rightXML = <Object><id>RoomSensors1</id><InfoItem name="CarbonDioxide"/><Object>
                  <id>Temperature</id>
                </Object></Object>

        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

    "Return right xml when asked for Objects" in {
        val RESTXML = Read.generateODFREST(Path("Objects/ReadTest"))

        val rightXML = <Object><id>ReadTest</id><Object><id>Refrigerator123</id></Object><Object><id>RoomSensors1</id></Object><Object><id>SmartCar</id></Object>
                        <Object><id>SmartOven</id></Object></Object>

        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

  }
}
