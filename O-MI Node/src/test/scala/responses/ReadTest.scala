package responses

import org.specs2.mutable._
import org.specs2.matcher.XmlMatchers._
import scala.io.Source
import responses._
import parsing._
import types._
import types.Path._
import types.OmiTypes._
import database._
import parsing.OdfParser._
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat
import scala.xml.Utility.trim
import scala.xml.XML
import testHelpers.{ BeforeAfterAll, SubscriptionHandlerTestActor }
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable
import akka.actor._
import scala.util.Try
import shapeless.headOption
import akka.testkit.TestActorRef

class ReadTest extends Specification with BeforeAfterAll {
  sequential

  //  val ReadResponseGen = new ReadResponseGen
  implicit val system = ActorSystem("readtest")
  implicit val dbConnection = new TestDB("read-test")

  val subscriptionHandler = TestActorRef(Props(new SubscriptionHandler()(dbConnection)))
  val requestHandler = new RequestHandler(subscriptionHandler)(dbConnection)
  val printer = new scala.xml.PrettyPrinter(80, 2)

  def beforeAll = {
    val calendar = Calendar.getInstance()
    calendar.setTime(new Date(1421775723))
    calendar.set(Calendar.HOUR_OF_DAY, 12)
    val date = calendar.getTime
    val testtime = new java.sql.Timestamp(date.getTime)
    //    dbConnection.clearDB()
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
      dbConnection.set(path, testtime, value)
    }

    var count = 0

    //for begin and end testing
    dbConnection.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    for (value <- intervaltestdata) {
      dbConnection.set(Path("Objects/ReadTest/SmartOven/Temperature"), new java.sql.Timestamp(date.getTime + count), value)
      count = count + 1000
    }

    //for metadata testing (if i added metadata to existing infoitems the previous tests would fail..)
//    dbConnection.remove(Path("Objects/Metatest/Temperature"))
    dbConnection.set(Path("Objects/Metatest/Temperature"), testtime, "asd")
    dbConnection.setMetaData(Path("Objects/Metatest/Temperature"),
      """<MetaData xmlns="odf.xsd"><InfoItem name="TemperatureFormat"><value dateTime="1970-01-17T12:56:15.723">Celsius</value></InfoItem></MetaData>""")

  }
  def afterAll = {
    dbConnection.destroy()
  }
  /*
 * Removed the Option get calls and head calls for sequences.
 * Tests have duplication but that is to allow easier debugging incase tests fail.
 */
  "Read response" should {
    sequential

    "Give correct XML when asked for multiple values" in {
      lazy val simpletestfile = Source.fromFile("src/test/resources/responses/read/SimpleXMLReadRequest.xml").getLines.mkString("\n")
      lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/correctXMLfirsttest.xml")
      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y })) //.asInstanceOf[ReadRequest]))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome.which(_._2 === 200)
      val node = resultOption.get._1
      node must \ ("response") \ ("result", "msgformat" -> "odf") \ ("msg") \ ("Objects") \ ("Object") //if this test fails, check the namespaces
      
      resultOption must beSome.which(n=> (n._1 \\ ("Objects")) must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")))
      resultOption must beSome.which(
        result => OmiParser.parse(result._1.toString()) must beRight.which(_.headOption must beSome.which(_ should beAnInstanceOf[ResponseRequest])))
    }

    "Give a history of values when begin and end is used" in {
      lazy val intervaltestfile = Source.fromFile("src/test/resources/responses/read/IntervalXMLTest.xml").getLines.mkString("\n")
      lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/CorrectIntervalXML.xml")
      val parserlist = OmiParser.parse(intervaltestfile)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome.which(_._2 === 200)
      resultOption must beSome.which(n=> (n._1 \\ ("Objects")) must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")))
      val node = resultOption.get._1

      node must \ ("response") \ ("result", "msgformat" -> "odf") \ ("msg") \ ("Objects") \ ("Object") //if this test fails, check the namespaces
 
      resultOption must beSome.which(
        result => OmiParser.parse(result._1.toString()) must beRight.which(_.headOption must beSome.which(_ should beAnInstanceOf[ResponseRequest])))
    }
    "Give object and its children when asked for" in {
      lazy val plainxml = Source.fromFile("src/test/resources/responses/read/PlainRequest.xml").getLines.mkString("\n")
      lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/PlainRightRequest.xml")
      val parserlist = OmiParser.parse(plainxml)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome.which(_._2 === 200)
//      println("test3:")
//      println(printer.format(resultOption.get._1.head))
//      println("correct:")
//      println(printer.format(correctxmlreturn.head))
      resultOption must beSome.which(n=> (n._1 \\ ("Objects")) must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")))

      resultOption must beSome.which(
        result => OmiParser.parse(result._1.toString()) must beRight.which(_.headOption must beSome.which(_ should beAnInstanceOf[ResponseRequest])))
    }

    "Give errors when a user asks for a wrong kind of/nonexisting object" in {
      lazy val erroneousxml = Source.fromFile("src/test/resources/responses/read/ErroneousXMLReadRequest.xml").getLines.mkString("\n")
      lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/WrongRequestReturn.xml")
      val parserlist = OmiParser.parse(erroneousxml)
      parserlist.isRight === true
      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))
      //returnCode should not be 200
      resultOption must beSome.which(_._2 !== 200)
//      println("test4:")
//      println(printer.format(resultOption.get._1.head))
//      println("correct:")
//      println(printer.format(correctxmlreturn.head))
      resultOption must beSome.which(n=> (n._1 \\ ("Objects")) must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")))

      //OmiParser.parse(resultXML.toString()).head should beAnInstanceOf[Result]
    }
/*    "Give partial result when part of the request is wrong" in {
      val partialxml =
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10.0">
          <omi:read msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>ReadTest</id>
                  <Object>
                    <id>NonexistingObject</id>
                  </Object>
                  <Object>
                    <id>SmartOven</id>
                  </Object>
                  <Object>
                    <id>Roomsensors1</id>
                    <InfoItem name="CarbonDioxide"></InfoItem>
                    <InfoItem name="wrong"></InfoItem>
                    <InfoItem name="Temperature"></InfoItem>
                  </Object>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>
        
        val parserlist= OmiParser.parse(partialxml.toString())
        parserlist.isRight === true
      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))
      //returnCode should not be 200
//      resultOption must beSome.which(_._2 !== 200)
      println("test6:")
      println(printer.format(resultOption.get._1.head))
//      println("correct:")
//      println(printer.format(correctxmlreturn.head))
            resultOption must beSome.which(n=> (n._1 \\ ("Objects")) must beEqualToIgnoringSpace(partialxml \\ ("Objects")))

      resultOption must beSome.which(_._1 must beEqualToIgnoringSpace(partialxml))
        

    }*/ //TODO: test with partially correct data should be in separate results

    "Return with correct metadata" in {
      lazy val metarequestxml = Source.fromFile("src/test/resources/responses/read/MetadataRequest.xml").getLines.mkString("\n")
      lazy val correctxmlreturn = XML.loadFile("src/test/resources/responses/read/MetadataCorrectReturn.xml")
      val parserlist = OmiParser.parse(metarequestxml)
      if(parserlist.isLeft) println(parserlist.left.get.toSeq)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome.which(_._2 === 200)
//      println("test5:")
//      println(printer.format(resultOption.get._1.head))
//      println("correct:")
//      println(printer.format(correctxmlreturn.head))
      resultOption must beSome.which(_._1 must beEqualToIgnoringSpace(correctxmlreturn))

    }

  }

  "When given path ODFREST" should {

    "Give just the value when path ends with /value" in {
      val RESTXML = requestHandler.generateODFREST(Path("Objects/ReadTest/Refrigerator123/PowerConsumption/value"))

      RESTXML must beSome.which(_ must beLeft("0.123"))
    }

    "Give correct XML when asked with an object path and trailing /" in {
      val RESTXML = requestHandler.generateODFREST(Path("Objects/ReadTest/RoomSensors1/"))

      val rightXML = <Object><id>RoomSensors1</id><InfoItem name="CarbonDioxide"/><Object>
                                                                                    <id>Temperature</id>
                                                                                  </Object></Object>

      RESTXML must beSome.which(_ must beRight.which(_ must beEqualToIgnoringSpace(rightXML)))
      //        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

    "Give correct XML when asked with an InfoItem path and trailing /" in {
      val RESTXML = requestHandler.generateODFREST(Path("Objects/ReadTest/RoomSensors1/CarbonDioxide"))

      val rightXML = <InfoItem name="CarbonDioxide" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                       <value unixTime="1421775">too much</value>
                     </InfoItem>

      RESTXML must beSome.which(_ must beRight.which(_ must beEqualToIgnoringSpace(rightXML)))
      //        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

    "Return None when asked for nonexisting object" in {
      val RESTXML = requestHandler.generateODFREST(Path("Objects/ReadTest/RoomSensors1/Wrong"))

      RESTXML should beNone
    }

    "Return right xml when asked for" in {
      val RESTXML = requestHandler.generateODFREST(Path("Objects/ReadTest"))

      val rightXML = <Object>
                       <id>ReadTest</id><Object><id>Refrigerator123</id></Object><Object><id>RoomSensors1</id></Object><Object><id>SmartCar</id></Object>
                       <Object><id>SmartOven</id></Object>
                     </Object>

      RESTXML must beSome.which(_ must beRight.which(_ must beEqualToIgnoringSpace(rightXML)))
      //        trim(RESTXML.get.right.get) should be equalTo(trim(rightXML))
    }

  }
}
