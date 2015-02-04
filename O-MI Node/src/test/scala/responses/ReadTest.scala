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
    val date = new Date(1421775723); //static date for testing
    val testtime = new java.sql.Timestamp(date.getTime)
    val calendar = Calendar.getInstance()
    calendar.setTimeInMillis(testtime.getTime())
    val testData = Map(
        "Objects/Refrigerator123/PowerConsumption" -> "0.123",
        "Objects/Refrigerator123/RefrigeratorDoorOpenWarning" -> "door closed",
        "Objects/Refrigerator123/RefrigeratorProbeFault" -> "Nothing wrong with probe",
        "Objects/RoomSensors1/Temperature/Inside" -> "21.2",
        "Objects/RoomSensors1/CarbonDioxide" -> "too much",
        "Objects/RoomSensors1/Temperature/Outside" -> "12.2"
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

    SQLite.remove("Objects/SmartOven/Temperature")
    for (value <- intervaltestdata){
        SQLite.set(new DBSensor("Objects/SmartOven/Temperature", value, new java.sql.Timestamp(date.getTime + count)))
        count = count + 1000
    }
  }

  "Read response" should {
    "Give correct XML when asked for multiple values" in {
      lazy val simpletestfile = Source.fromFile("src/test/scala/responses/SimpleXMLReadRequest.xml").getLines.mkString("\n")
      lazy val correctxmlreturn = XML.loadFile("src/test/scala/responses/correctXMLfirsttest.xml")
      val parserlist = OmiParser.parse(simpletestfile)

      trim(correctxmlreturn) should be equalTo(trim(Read.OMIReadResponse(parserlist.head.asInstanceOf[OneTimeRead])))
    }

    "Give a history of values when begin and end is used" in {
        lazy val intervaltestfile = Source.fromFile("src/test/scala/responses/IntervalXMLTest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/scala/responses/CorrectIntervalXML.xml")
        val parserlist = OmiParser.parse(intervaltestfile)

        trim(correctxmlreturn) should be equalTo(trim(Read.OMIReadResponse(parserlist.head.asInstanceOf[OneTimeRead])))
    }
  }
}

/*
class ReadTest extends Specification {
    lazy val simpletestfile = Source.fromFile("src/test/scala/responses/SimpleXMLReadRequest.xml").getLines.mkString("\n")

	// Create our in-memory sensor database
   
    
    val date = new Date(1421775723); //static date for testing
    val testtime = new java.sql.Timestamp(date.getTime)
    val calendar = Calendar.getInstance()
    calendar.setTimeInMillis(testtime.getTime())
    val testData = Map(
        "Objects/Refrigerator123/PowerConsumption" -> "0.123",
        "Objects/Refrigerator123/RefrigeratorDoorOpenWarning" -> "door closed",
        "Objects/Refrigerator123/RefrigeratorProbeFault" -> "Nothing wrong with probe",
        "Objects/RoomSensors1/Temperature/Inside" -> "21.2",
        "Objects/RoomSensors1/CarbonDioxide" -> "too much",
        "Objects/RoomSensors1/Temperature/Outside" -> "12.2"
    )

    for ((path, value) <- testData){
        SQLite.remove(path)
        SQLite.set(new DBSensor(path, value, testtime))
    }

	def is = s2"""
  	Testing for the read response.

  	Read.OMIReadResponse should return correct XML when given a list of values.

      Correct XML with one value                $e1
      Correct XML with multiple values          $e2
      Correct answer from real request          $e3
      REST works with /value                    $e4
      REST works with trailing /                $e5
      
    """

    def e1 = {


        val testliste1 = List(
        ODFNode("/Objects/Refrigerator123/PowerConsumption", InfoItem, Some("0.123"), Some(testtime.toString), None))
    	val testliste1forread = List(OneTimeRead("10", None, None, testliste1))

        val testliste1check = List(
        ODFNode("/Objects/Refrigerator123/PowerConsumption", InfoItem, Some("0.123"), Some("dateTime=" + "\"" + testtime.toString + "\""), None))

        OmiParser.parse(Read.OMIReadResponse(2, testliste1forread, None, None)) == List(
            Result("", Some(testliste1check)))

    }

    def e2 = {


        val testliste2 = List(
        ODFNode("/Objects/Refrigerator123/PowerConsumption", InfoItem, Some("0.123"), Some(testtime.toString), None),
        ODFNode("/Objects/Refrigerator123/RefrigeratorDoorOpenWarning", InfoItem, Some("door closed"), Some(testtime.toString), None),
        ODFNode("/Objects/Refrigerator123/RefrigeratorProbeFault", InfoItem, Some("Nothing wrong with probe"), Some(testtime.toString), None),
        ODFNode("/Objects/RoomSensors1/Temperature/Inside", InfoItem, Some("21.2"), Some(testtime.toString), None),
        ODFNode("/Objects/RoomSensors1/CarbonDioxide", InfoItem, Some("too much"), Some(testtime.toString), None))

        val testliste2check = List(
        ODFNode("/Objects/Refrigerator123/PowerConsumption", InfoItem, Some("0.123"), Some("dateTime=" + "\"" + testtime.toString + "\""), None),
        ODFNode("/Objects/Refrigerator123/RefrigeratorDoorOpenWarning", InfoItem, Some("door closed"), Some("dateTime=" + "\"" + testtime.toString + "\""), None),
        ODFNode("/Objects/Refrigerator123/RefrigeratorProbeFault", InfoItem, Some("Nothing wrong with probe"), Some("dateTime=" + "\"" + testtime.toString + "\""), None),
        ODFNode("/Objects/RoomSensors1/Temperature/Inside", InfoItem, Some("21.2"), Some("dateTime=" + "\"" + testtime.toString + "\""), None),
        ODFNode("/Objects/RoomSensors1/CarbonDioxide", InfoItem, Some("too much"), Some("dateTime=" + "\"" + testtime.toString + "\""), None))

        val testliste2forread = List(OneTimeRead("10", None, None, testliste2))

        OmiParser.parse(Read.OMIReadResponse(2, testliste2forread, None, None)) == List(
            Result("", Some(testliste2check)))

    }

    def e3 = {
        val odfnodes = OmiParser.parse(simpletestfile)
        var listofnodes = odfnodes.collect {
            case OneTimeRead(_,_,_,c) => c
        }

        val nodelist = listofnodes.head

        OmiParser.parse(Read.OMIReadResponse(2, odfnodes.toList, None, None)) should be equalTo( List(  //nodelist should already be a list but for some reason its Seq
              Result("",Some(List(ODFNode("/Objects/Refrigerator123/PowerConsumption", InfoItem, Some("0.123"), Some("dateTime=" + "\"" + testtime.toString + "\""), None))))))
            //Result("", Some(nodelist.toList))))

    }

    def e4 = {
        val RESTXML = Read.generateODFREST("Objects/Refrigerator123/PowerConsumption/value")

        RESTXML should be equalTo(Some(Left("0.123")))
    }

    def e5 = {
        val RESTXML = Read.generateODFREST("Objects/RoomSensors1/")

        val rightXML = <Object><id>RoomSensors1</id><InfoItem name="CarbonDioxide"/><Object>
                  <id>Temperature</id>
                </Object></Object>

        RESTXML should be equalTo(Some(Right(rightXML)))
    }
}

*/

