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

class CancelTest extends Specification with Before {
  def before = {
    val calendar = Calendar.getInstance()
    calendar.setTime(new Date(1421775723))
    calendar.set(Calendar.HOUR_OF_DAY, 12)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    SQLite.clearDB()
    val testData = Map(
        Path("Objects/CancelTest/Refrigerator123/PowerConsumption") -> "0.123"
    )
    
    for ((path, value) <- testData){
        SQLite.remove(path)
        SQLite.set(new DBSensor(path, value, testtime))
    }

    var id = SQLite.saveSub(
        new DBSub(Array(Path("Objects/CancelTest/Refrigerator123/PowerConsumption")),0,1,None,Some(testtime)))
  }

  
  "Cancel response" should {
    "Give correct XML when cancel is requested" in {
        lazy val simpletestfile = Source.fromFile("src/test/resources/responses/SimpleXMLCancelRequest.xml").getLines.mkString("\n")
        lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/CorrectCancelReturn.xml")
        val parserlist = OmiParser.parse(simpletestfile)
        val resultXML = trim(OMICancel.OMICancelResponse(parserlist))
        
        resultXML should be equalTo(trim(correctxmlreturn))
        OmiParser.parse(resultXML.toString()).head should beAnInstanceOf[Result]
    }
  } 
}
