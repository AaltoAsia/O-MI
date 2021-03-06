/*package responses

import java.lang.{Iterable => JavaIterable}
import java.util.{Calendar, Date, TimeZone}

import agentSystem.InputPusher
import akka.actor._
import akka.testkit.TestActorRef
import database._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.XmlMatchers._
import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll
import parsing._
import types.OdfTypes.OdfValue
import types.OmiTypes._
import types._

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.xml.{Elem, NodeSeq, XML}
//
class ReadTest(implicit ee: ExecutionEnv) extends Specification with BeforeAfterAll {
  sequential

  //  val ReadResponseGen = new ReadResponseGen
  implicit val system = ActorSystem("readtest")
  implicit val dbConnection = new TestDB("read-test")

  val subscriptionHandler = TestActorRef(Props(new SubscriptionHandler()(dbConnection)))
  val requestHandler = new RequestHandler(subscriptionHandler)(dbConnection)
  val printer = new scala.xml.PrettyPrinter(80, 2)

  def beforeAll = {
    val calendar = Calendar.getInstance()
    val timeZone = TimeZone.getTimeZone("UTC")

    calendar.setTimeZone(timeZone)
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
      InputPusher.handlePathValuePairs(Iterable((path, OdfValue(value, "", testtime))))
      //dbConnection.set(path, testtime, value)
    }

    var count = 0

    //for begin and end testing
    dbConnection.remove(Path("Objects/ReadTest/SmartOven/Temperature"))
    val addFutures = intervaltestdata.map{ value => //for (value <- intervaltestdata) {
      val addFuture = InputPusher.handlePathValuePairs(Iterable((Path("Objects/ReadTest/SmartOven/Temperature"), OdfValue(value, "", new java.sql.Timestamp(date.getTime + count)))))
      //dbConnection.set(Path("Objects/ReadTest/SmartOven/Temperature"), new java.sql.Timestamp(date.getTime + count), value)
      count = count + 1000
      addFuture
    }

    //for metadata testing (if i added metadata to existing infoitems the previous tests would fail..)
    //    dbConnection.remove(Path("Objects/Metatest/Temperature"))
    val metaFuture = InputPusher.handlePathValuePairs(Iterable((Path("Objects/Metatest/Temperature"), OdfValue("asd", "", testtime))))
    Await.ready(Future.sequence(addFutures :+ metaFuture), Duration.apply(5, "seconds"))
    //dbConnection.set(Path("Objects/Metatest/Temperature"), testtime, "asd")

    // FIXME
    // dbConnection(JavaIterable(Path("Objects/Metatest/Temperature"),
    //   """<MetaData xmlns="odf.xsd"><InfoItem name="TemperatureFormat"><value dateTime="1970-01-17T12:56:15.723">Celsius</value></InfoItem></MetaData>"""))

  }
  def removeDateTimeString( text: String) : String =text.replaceAll(
    """dateTime\s*=\s*"\S*?"""",
    ""
  )

  def removeDateTime(reqresp: NodeSeq): Elem = {
      XML.loadString(
          removeDateTimeString(reqresp.toString)
      )
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
      val simpletestfile =
        """<?xml version="1.0" encoding="UTF-8"?>
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10.0">
          <omi:read msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>ReadTest</id>
                  <Object>
                    <id>Refrigerator123</id>
                    <InfoItem name="PowerConsumption">
                    </InfoItem>
                  </Object>
                  <Object>
                    <id>RoomSensors1</id>
                    <Object>
                      <id>Temperature</id>
                      <InfoItem name="Inside">
                      </InfoItem>
                    </Object>
                  </Object>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>"""

      val correctxmlreturn =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns="odf.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ttl="1.0" version="1.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"></omi:return>
              <omi:msg xsi:schemaLocation="odf.xsd odf.xsd" xmlns="odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>Refrigerator123</id>
                      <InfoItem name="PowerConsumption">
                        <value unixTime="1428975" >0.123</value>
                      </InfoItem>
                    </Object>
                    <Object>
                      <id>RoomSensors1</id>
                      <Object>
                        <id>Temperature</id>
                        <InfoItem name="Inside">
                          <value unixTime="1428975">21.2</value>
                        </InfoItem>
                      </Object>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      val parserlist = OmiParser.parse(simpletestfile)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y })) //.asInstanceOf[ReadRequest]))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome
      val node = resultOption.get
      node must \("response").\("result", "msgformat" -> "odf").\("msg").\("Objects").\("Object").await//if this test fails, check the namespaces
      
      val odf = node.map(nod => removeDateTime(nod \\("Objects")))//removeDateTime(node \\ ("Objects"))
      odf must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")).await
   //   OmiParser.parse(removeDateTime(node)) must beRight.which(_.headOption must beSome.which(_ should beAnInstanceOf[ResponseRequest]))
    }

    "Give a history of values when begin and end is used" in {
      val intervaltestfile =
        """<?xml version="1.0" encoding="UTF-8"?>
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10.0">
          <omi:read msgformat="odf" begin="1970-01-17T10:00:10" end="1970-01-17T23:56:25">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>ReadTest</id>
                  <Object>
                    <id>SmartOven</id>
                    <InfoItem name="Temperature">
                    </InfoItem>
                  </Object>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>"""

      val correctxmlreturn =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ttl="0.0" version="1.0" xsi:schemaLocation="omi.xsd omi.xsd">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"/>
              <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>SmartOven</id>
                      <InfoItem name="Temperature">
                        <value unixTime="1428975">100</value>
                        <value unixTime="1428976">102</value>
                        <value unixTime="1428977">105</value>
                        <value unixTime="1428978">109</value>
                        <value unixTime="1428979">115</value>
                        <value unixTime="1428980">117</value>
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      val parserlist = OmiParser.parse(intervaltestfile)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome//.which(_._2 === 200)
      val node = resultOption.get

      node must \("response").\("result", "msgformat" -> "odf").\("msg").\("Objects").\("Object").await //if this test fails, check the namespaces
      //val timelessRes = removeDateTime(node)
      val odf = node.map(nod => removeDateTime(nod \\("Objects")))//timelessRes \\("Objects")
      odf must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")).await
      //OmiParser.parse(timelessRes) must beRight.which(_.headOption must beSome.which(_ should beAnInstanceOf[ResponseRequest]))
    }
    "Give object and its children when asked for" in {
      val plainxml =
        """<?xml version="1.0" encoding="UTF-8"?>
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10.0">
          <omi:read msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>ReadTest</id>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>"""

      val correctxmlreturn =
        <omi:omiEnvelope xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"/>
              <omi:msg xsi:schemaLocation="odf.xsd odf.xsd" xmlns="odf.xsd">
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>SmartOven</id>
                      <InfoItem name="Temperature">
                        <value unixTime="1428980">117</value>
                      </InfoItem>
                    </Object>
                    <Object>
                      <id>SmartCar</id>
                      <InfoItem name="Fuel">
                        <value unixTime="1428975">30</value>
                      </InfoItem>
                    </Object>
                    <Object>
                      <id>RoomSensors1</id>
                      <InfoItem name="CarbonDioxide">
                        <value unixTime="1428975">too much</value>
                      </InfoItem>
                      <Object>
                        <id>Temperature</id>
                        <InfoItem name="Inside">
                          <value unixTime="1428975">21.2</value>
                        </InfoItem>
                        <InfoItem name="Outside">
                          <value unixTime="1428975">12.2</value>
                        </InfoItem>
                      </Object>
                    </Object>
                    <Object>
                      <id>Refrigerator123</id>
                      <InfoItem name="RefrigeratorDoorOpenWarning">
                        <value unixTime="1428975">door closed</value>
                      </InfoItem>
                      <InfoItem name="PowerConsumption">
                        <value unixTime="1428975">0.123</value>
                      </InfoItem>
                      <InfoItem name="RefrigeratorProbeFault">
                        <value unixTime="1428975">Nothing wrong with probe</value>
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      val parserlist = OmiParser.parse(plainxml)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome//.which(_._2 === 200)
      //      println("test3:")
      //      println(printer.format(resultOption.get._1.head))
      //      println("correct:")
      //      println(printer.format(correctxmlreturn.head))

      val node = resultOption.get
      val odf = node.map(nod => removeDateTime(nod \\ ("Objects")))
      odf must beEqualToIgnoringSpace(correctxmlreturn \\ ("Objects")).await
      //OmiParser.parse(removeDateTime(node)) must beRight.which(_.headOption must beSome.which(_ should beAnInstanceOf[ResponseRequest]))
    }

    "Give errors when a user asks for a wrong kind of/nonexisting object" in {
      val erroneousxml =
        """<?xml version="1.0" encoding="UTF-8"?>
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
                    <id>Roomsensors1</id>
                    <InfoItem name="wrong"></InfoItem>
                    <InfoItem name="Temperature"></InfoItem>
                  </Object>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>"""

      lazy val correctxmlreturn =
        <omi:omiEnvelope ttl="1.0" version="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <omi:response>
            <omi:result>
              <omi:return description="Such item/s not found." returnCode="404"></omi:return>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      val parserlist = OmiParser.parse(erroneousxml)
      parserlist.isRight === true
      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome
      val node = resultOption.get
      node.map(nod => removeDateTime(nod)) must beEqualToIgnoringSpace(correctxmlreturn).await
    }

    "Give partial result when part of the request is wrong" in {
      // NOTE: Maybe a bit specific test about the error message
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
      val partialresult =
        <omi:omiEnvelope version="1.0" ttl="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"/>
              <omi:msg>
                <Objects>
                  <Object>
                    <id>ReadTest</id>
                    <Object>
                      <id>SmartOven</id>
                      <InfoItem name="Temperature">
                        <value unixTime="1428980">117</value>
                      </InfoItem>
                    </Object>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
            <omi:result>
              <omi:return returnCode="404" description={
                "Could not find the following elements from the database:" +
                  " Objects/ReadTest/NonexistingObject" +
                  " Objects/ReadTest/Roomsensors1/CarbonDioxide" +
                  " Objects/ReadTest/Roomsensors1/wrong" +
                  " Objects/ReadTest/Roomsensors1/Temperature"
              }/>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>

      val parserlist = OmiParser.parse(partialxml.toString())
      parserlist.isRight === true
      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      readRequestOption must beSome
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))
      //returnCode should not be 200
      //resultOption must beSome.which(_._2 !== 200)

      resultOption must beSome.which{
        case result => result.map(res => removeDateTime(res)) must beEqualToIgnoringSpace(partialresult).await
      }

    }

    "Return with correct metadata" in {
      val metarequestxml =
        """<?xml version="1.0" encoding="UTF-8"?>
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10.0">
          <omi:read msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>Metatest</id>
                  <InfoItem name="Temperature">
                    <MetaData/>
                  </InfoItem>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>"""

      val correctxmlreturn =
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns="odf.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" ttl="1.0" version="1.0">
          <omi:response>
            <omi:result msgformat="odf">
              <omi:return returnCode="200"/>
              <omi:msg>
                <Objects>
                  <Object>
                    <id>Metatest</id>
                    <InfoItem name="Temperature">
                      <MetaData>
                        <InfoItem name="TemperatureFormat">
                          <value type="xs:string">Celsius</value>
                        </InfoItem>
                      </MetaData>
                      <value unixTime="1428975">asd</value>
                    </InfoItem>
                  </Object>
                </Objects>
              </omi:msg>
            </omi:result>
          </omi:response>
        </omi:omiEnvelope>
      
      val parserlist = OmiParser.parse(metarequestxml)
      if (parserlist.isLeft) println(parserlist.left.get.toSeq)
      parserlist.isRight === true

      val readRequestOption = parserlist.right.toOption.flatMap(x => x.headOption.collect({ case y: ReadRequest => y }))
      val resultOption = readRequestOption.map(x => requestHandler.runGeneration(x))

      resultOption must beSome
      //      println("test5:")
      //      println(printer.format(resultOption.get._1.head))
      //      println("correct:")
      //      println(printer.format(correctxmlreturn.head))
      val node = resultOption.get
      node.map(nod =>removeDateTime(nod)) must beEqualToIgnoringSpace(correctxmlreturn).await
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
                       <value unixTime="1428975">too much</value>
                     </InfoItem>

                     RESTXML must beSome.which(_ must beRight.which(r => removeDateTime(r) must beEqualToIgnoringSpace(rightXML)))
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

*/