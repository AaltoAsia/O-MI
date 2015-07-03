package http

import responses.RequestHandler
import parsing._
import types._
import types.Path._

import xml._

import org.specs2.mutable.Specification
import org.specs2.matcher.XmlMatchers

import spray.testkit.Specs2RouteTest
import spray.httpx.marshalling.BasicMarshallers._
import spray.http._
import spray.http.HttpHeaders._
import HttpMethods._
import StatusCodes._
import MediaTypes._

import akka.testkit.TestActorRef
import akka.actor._
import responses.SubscriptionHandler

import database._
import http.PermissionCheck._

import scala.collection.JavaConverters._
import java.net.InetAddress

import testHelpers.BeforeAfterAll
class OmiServiceTest extends Specification

  with XmlMatchers
  with Specs2RouteTest
  with OmiService
  with BeforeAfterAll {
  def actorRefFactory = system
  lazy val log = akka.event.Logging.getLogger(actorRefFactory, this)

  implicit val dbConnection = new TestDB("system-test") // new SQLiteConnection
  implicit val dbobject = dbConnection
  val subscriptionHandler = TestActorRef(Props(new SubscriptionHandler()(dbConnection)))
  val requestHandler = new RequestHandler(subscriptionHandler)(dbConnection)
  val printer = new scala.xml.PrettyPrinter(80, 2)

  "System tests for features of OMI Node service".title

  def beforeAll() = {
    Boot.init(dbConnection)
    // clear if some other tests have left data
    //    dbConnection.clearDB()

    // Initialize the OmiService
    //    Boot.main(Array())
  }

  def afterAll() = {
    // clear db
    dbConnection.destroy()
    system.shutdown()
  }

  "Data discovery, GET: OmiService" should {

    "respond with hello message for GET request to the root path" in {
      Get() ~> myRoute ~> check {
        mediaType === `text/html`
        responseAs[String] must contain("Say hello to <i>O-MI Node service")

      }
    }

    "respond succesfully to GET to /Objects" in {
      Get("/Objects") ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        responseAs[NodeSeq].headOption must beSome.which(_.label == "Objects") // => ??? }((_: Node).label == "Objects")//.head.label === "Objects"
      }
    }
    "respond succesfully to GET to /Objects/" in {
      Get("/Objects/") ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        responseAs[NodeSeq].headOption must beSome.which(_.label == "Objects")
      }
    }
    "respond with error to non existing path" in {
      Get("/Objects/nonexsistent7864057") ~> myRoute ~> check {
        mediaType === `text/xml`
        status === NotFound
        responseAs[NodeSeq].headOption must beSome.which(_.label == "error") //head.label === "error"
      }
    }
    "respond successfully to GET to some value" in {
      dbConnection.set(Path("Objects/SystemTests/TestValue"), new java.sql.Timestamp(1000), "123")

      Get("/Objects/SystemTests/TestValue/value") ~> myRoute ~> check {
        mediaType === `text/plain`
        status === OK
        responseAs[String] === "123"
      }

    }

    val settingsPath = "/" + Path(Boot.settings.settingsOdfPath).toString

    // Somewhat overcomplicated test; Serves as an example for other tests
    "reply its settings as odf from path `settingsOdfPath` (with \"Settings\" id)" in {
      Get(settingsPath) ~> myRoute ~> check { // this didn't work without / at start
        status === OK
        mediaType === `text/xml`
        responseAs[NodeSeq] must \("id") \> "Settings"
      }
    }

    "reply its settings having num-latest-values-stored)" in {
      Get(settingsPath) ~> myRoute ~> check { // this didn't work without / at start
        status === OK
        mediaType === `text/xml`
        responseAs[NodeSeq] must \("InfoItem", "name" -> "num-latest-values-stored")
      }
    }

  }

  "Read requests: OmiService" should {
    sequential
    val powerConsumptionValue = "180"
    val dataTime = new java.sql.Timestamp(1000)
    val fridgeData = (Path("Objects/SmartFridge22334411/PowerConsumption"),
      powerConsumptionValue,
      dataTime)
    log.debug("set data")
    dbConnection.set(fridgeData._1, fridgeData._3, fridgeData._2)

    val readTestRequestFridge: NodeSeq =
      // NOTE: The type needed for compiler to recognize the right Marhshaller later
      <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects>
              <Object>
                <id>SmartFridge22334411</id>
                <InfoItem name="PowerConsumption"/>
              </Object>
            </Objects>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope>

    val invalidReadTestRequestFridge: NodeSeq =
      // NOTE: The type needed for compiler to recognize the right Marhshaller later
      <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
        <omi:read msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <WWWWWWWWWWWWWWWWWWWWWWWWWW>
              <Object>
                <id>SmartFridge22334411</id>
                <InfoItem name="PowerConsumption"/>
              </Object>
            </WWWWWWWWWWWWWWWWWWWWWWWWWW>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope>

    "handle a single read request with following messages:" should {
      s"invalid request:\n $invalidReadTestRequestFridge" in {
        Post("/", invalidReadTestRequestFridge).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
          val response = responseAs[NodeSeq].head
          //          println(response)
          //          println("\n\n\n\n\n______________________________________")
          //          status === BadRequest // TODO this test needs to be updated when error handling is correctly implemented
          s"response:\n${printer.format(response)}" in{
          response must \("response") \ ("result") \ ("return", "returnCode" -> "400")
        }

        }
      }
      
      s"correct request:\n $readTestRequestFridge" in {
      Post("/", readTestRequestFridge).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {

        // XXX: This test is hacky as it is a nested "should"
        val response = responseAs[NodeSeq].head
        val mtype = mediaType
        val rstatus = status

        s"response:\n${printer.format(response)}" in {
          mtype === `text/xml`
          rstatus === OK
          response must \("response") \ ("result", "msgformat" -> "odf")
          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")


        val msg = response \ "response" \ "result" \ "msg"
        val infoitem = msg \ "Objects" \ "Object" \ "InfoItem"



          response must \("response") \ ("result") \ ("msg")

          msg must \("Objects") \ ("Object") \ ("InfoItem", "name" -> "PowerConsumption")

          infoitem must have length (1)
          infoitem must \("value") \> powerConsumptionValue // "180"
        }
      }
      }
    }
    val subscriptionTestCorrect: NodeSeq =
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="2">
        <omi:read msgformat="odf" interval="1">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects>
              <Object>
                <id>SmartFridge22334411</id>
                <InfoItem name="PowerConsumption"/>
              </Object>
            </Objects>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope>

    "handle a subscription requests with following messages" should {
      sequential
      var requestID1: Option[Int] = None
      s"correct request: \n $subscriptionTestCorrect " in {
        Post("/", subscriptionTestCorrect).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status
          s"response:\n${printer.format(response)}" in{ 
          mtype === `text/xml`
          rstatus === OK

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
          response must \("response") \ ("result") \ ("requestID")

          requestID1 = Some((response \\ "requestID").text.toInt)
          //          println(requestID.get)
          //          println("\n\n\n\n\n")
          requestID1 must beSome

          }

        }

      }
      def pollmessage: NodeSeq =
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
          <omi:read>
            <omi:requestID>{ requestID1.get }</omi:requestID>
          </omi:read>
        </omi:omiEnvelope>

      "return correct message when polled with the correct requestID" in {
        requestID1 must beSome
        Post("/", pollmessage).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {

          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          mtype === `text/xml`
          rstatus === OK
          response must \("response") \ ("result") \ ("requestID") \> requestID1.get.toString()
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object") \ ("id") \> "SmartFridge22334411"
          //response must not \\("value") 
          // currently we will return value straight away
          response must \\("value") length 1
          response must \\("value") \> "180"

        }
      }
      
      "return correct message when polled with the correct requestID" in {
        Thread.sleep(1000)
        requestID1 must beSome
        Post("/", pollmessage).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {

          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          mtype === `text/xml`
          rstatus === OK
          response must \("response") \ ("result") \ ("requestID") \> requestID1.get.toString()
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object") \ ("id") \> "SmartFridge22334411"
          response must \\("value") length 1
          response must \\("value") \> "180"

        }
      }
      "return correct message when subscription ttl has ended" in {
        requestID1 must beSome
        Thread.sleep(1100)
        Post("/", pollmessage).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          mtype === `text/xml`
          rstatus === NotFound

          response must \("response") \ ("result") \ ("return", "returnCode" -> "404", "description" -> "A subscription with this id has expired or doesn't exist")
          response must \("response") \ ("result") \ ("requestID") \> requestID1.get.toString()
        }
      }

      val subscriptionTestCorrectEvent: NodeSeq =
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="-1">
          <omi:read msgformat="odf" interval="-1">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>SmartFridge22334411</id>
                  <InfoItem name="PowerConsumption"/>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>

      s"event subscription request:\n $subscriptionTestCorrectEvent" in {
        Post("/", subscriptionTestCorrectEvent).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status
          s"response:\n${printer.format(response)}" in {
          mtype === `text/xml`
          rstatus === OK

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
          response must \("response") \ ("result") \ ("requestID")

          requestID1 = Some((response \\ "requestID").text.toInt)
          //          println(requestID.get)
          //          println("\n\n\n\n\n")
          requestID1 must beSome
          }
        }

      }
      //lazy val needed here to get the correct requestID
      //////      lazy val pollmessage2: NodeSeq =
      //////        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
      ////          <omi:read>
      //            <omi:requestID>{ requestID1.get }</omi:requestID>
      //          </omi:read>
      ////        </omi:omiEnvelope>
      "return response with no new values when there have been no updates" in {
        requestID1 must beSome
        Post("/", pollmessage).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {

          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          mtype === `text/xml`
          rstatus === OK
          response must \("response") \ ("result") \ ("requestID") \> requestID1.get.toString()
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object") \ ("id") \> "SmartFridge22334411"
          response must not \\ ("value")
        }
      }
      "return response with new values after db update" in {
        //simulate an value update in the database
        dbConnection.set(fridgeData._1, new java.sql.Timestamp(new java.util.Date().getTime()), "200")

        Post("/", pollmessage).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {

          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          mtype === `text/xml`
          rstatus === OK
          response must \("response") \ ("result") \ ("requestID") \> requestID1.get.toString()
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object") \ ("id") \> "SmartFridge22334411"
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object") \ ("InfoItem") \ ("value") \> "200"
        }
      }
      "return empty message when new values have been already polled" in {
        Post("/", pollmessage).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {

          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          mtype === `text/xml`
          rstatus === OK
          response must \("response") \ ("result") \ ("requestID") \> requestID1.get.toString()
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object") \ ("id") \> "SmartFridge22334411"
          response must not \\ ("value")
        }
      }

    }
  }
  "Write request: OmiService" should {
    val writeRequest =
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
        <omi:write msgformat="odf">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects>
              <Object>
                <id>SmartFridge22334411</id>
                <InfoItem name="PowerConsumption"/>
              </Object>
            </Objects>
          </omi:msg>
        </omi:write>
      </omi:omiEnvelope>

    "handle single write request and the response" should {
      "return correct message when adding new InfoItem" in {
        1===1
      }
    }
  }

  /**
   * EXAMPLES:
   *
   * "return a greeting for GET requests to the root path" in {
   * Get() ~> myRoute ~> check {
   * responseAs[String] must contain("Say hello")
   * }
   * }
   *
   * "leave GET requests to other paths unhandled" in {
   * Get("/kermit") ~> myRoute ~> check {
   * handled must beFalse
   * }
   * }
   *
   * "return a MethodNotAllowed error for PUT requests to the root path" in {
   * Put() ~> sealRoute(myRoute) ~> check {
   * status === MethodNotAllowed
   * responseAs[String] === "HTTP method not allowed, supported methods: GET"
   * }
   * }
   */

}


