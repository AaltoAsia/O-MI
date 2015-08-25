package http

import responses.RequestHandler
import parsing._
import types._
import types.Path._

import xml._

import org.specs2.mutable.Specification
import org.specs2.matcher.{ XmlMatchers, MatchResult }

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
import http.Authorization._

import scala.collection.JavaConverters._
import java.net.InetAddress

import testHelpers.BeforeAfterAll

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OmiServiceTest extends Specification

  with XmlMatchers
  with Specs2RouteTest
  with OmiService
  with BeforeAfterAll {
  def actorRefFactory = system
  lazy val log = akka.event.Logging.getLogger(actorRefFactory, this)

  implicit val dbConnection = new TestDB("system-test") // new DatabaseConnection
  implicit val dbobject = dbConnection
  val subscriptionHandler = TestActorRef(Props(new SubscriptionHandler()(dbConnection)))
  val requestHandler = new RequestHandler(subscriptionHandler)(dbConnection)
  val printer = new scala.xml.PrettyPrinter(80, 2)

  "System tests for features of OMI Node service".title

  def beforeAll() = {
    Boot.init(dbConnection)
    Thread.sleep(1000)
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

  "request, POST: OmiService" should {
    sequential
    "respond correctly to read request with invalid omi" in {
      val request: NodeSeq =
        // NOTE: The type needed for compiler to recognize the right Marhshaller later
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
          <omi:read msgformat="odf">
            <omi:msgsssssssssssssssssssssssssss xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>SmartFridge22334411</id>
                  <InfoItem name="PowerConsumption"/>
                </Object>
              </Objects>
            </omi:msgsssssssssssssssssssssssssss>
          </omi:read>
        </omi:omiEnvelope>

      Post("/", request).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
        mediaType === `text/xml`
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))
        //                    println(printer.format(resp))
        //          println("\n\n\n\n\n______________________________________")
        //          status === BadRequest // TODO this test needs to be updated when error handling is correctly implemented

        response must \("response") \ ("result") \ ("return", "returnCode" -> "400")
        val description = resp.\("response").\("result").\("return").\@("description")
        description must startWith("OmiParser: Invalid XML, schema failure:")
      }
    }

    "respond correctly to read request with invalid odf" in {
      val request: NodeSeq =
        // NOTE: The type needed for compiler to recognize the right Marhshaller later
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
          <omi:read msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Objects>
                  <id>SmartFridge22334411</id>
                  <InfoItem name="PowerConsumption"/>
                </Objects>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>

      Post("/", request).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
        mediaType === `text/xml`
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

        response must \("response") \ ("result") \ ("return", "returnCode" -> "400")
        val description = resp.\("response").\("result").\("return").\@("description")
        description must startWith("OdfParser: Invalid XML, schema failure:")
      }
    }

    "respond correctly to read request with non-existing path" in {
      val request: NodeSeq =
        // NOTE: The type needed for compiler to recognize the right Marhshaller later
        <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
          <omi:read msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects>
                <Object>
                  <id>non-existing</id>
                  <InfoItem name="PowerConsumption"/>
                </Object>
              </Objects>
            </omi:msg>
          </omi:read>
        </omi:omiEnvelope>

      Post("/", request).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
        mediaType === `text/xml`
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//        println(printer.format(resp))

        response must \("response") \ ("result") \ ("return", "returnCode" -> "404")
        val description = resp.\("response").\("result").\("return").\@("description")
        description === "Such item/s not found."
      }
    }

    "respond correctly to subscription poll with non existing requestId" in {
      val request: NodeSeq =
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10000">
          <omi:read msgformat="odf">
            <omi:requestID>9999</omi:requestID>
          </omi:read>
        </omi:omiEnvelope>

      Post("/", request).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
        mediaType === `text/xml`
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

        //        println(printer.format(resp))

        response must \("response") \ ("result") \ ("return", "returnCode" -> "404")
        val description = resp.\("response").\("result").\("return").\@("description")
        description === "A subscription with this id has expired or doesn't exist"
      }
    }

    "respond to permissive requests" in {
      val request: String = """
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="0">
          <omi:write msgformat="odf">
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="odf.xsd" xs:schemaLocation="odf.xsd odf.xsd">
                <Object>
                  <id>testObject</id>
                  <InfoItem name="testSensor">
                    <value unixTime="0" type="xs:integer">0</value>
                  </InfoItem>
                </Object>
              </Objects>
            </omi:msg>
          </omi:write>
        </omi:omiEnvelope>"""

      "respond correctly to write request with whitelisted IPv4-addresses" in {
        Post("/", XML.loadString(request)).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }

        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor1"))).withHeaders(`Remote-Address`("127.255.255.255")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
      }

      "respond correctly to write request with non-whitelisted IPv4-addresses" in {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor2"))).withHeaders(`Remote-Address`("192.65.127.80")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }

        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor3"))).withHeaders(`Remote-Address`("128.0.0.1")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }

      }

      "respond correctly to write request with whitelisted IPv6-addresses" in {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor4"))).withHeaders(`Remote-Address`("0:0:0:0:0:0:0:1")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor5"))).withHeaders(`Remote-Address`("0:0:0:FFFF:FFFF:FFFF:FFFF:FFFF")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
      }
      "respond correctly to write request with non-whitelisted IPv6-addresses" in {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor4"))).withHeaders(`Remote-Address`("0:0:1:0:0:0:0:0")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }

        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor4"))).withHeaders(`Remote-Address`("2001:DB80:ABBA:BABB:A:0:FF:FF")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }
      }
      "respond correctly to normal read with non-whitelisted address and user" in {
        val request: String = """
          <omi:omiEnvelope xmlns:omi="omi.xsd" version="1.0" ttl="0">
            <omi:read msgformat="odf">
              <omi:msg xmlns="odf.xsd">
                <Objects xmlns="odf.xsd">
                </Objects>
              </omi:msg>
            </omi:read>
          </omi:omiEnvelope>"""
        Post("/", XML.loadString(request)).withHeaders(`Remote-Address`("192.65.127.80")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object")
        }
        Post("/", XML.loadString(request)).withHeaders(`Remote-Address`("187.42.74.1"), RawHeader("HTTP_EPPN", "someNonExistentUser@cheatOrganization.zw")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object")
        }
      }
      "respond correctly to write request with non-whitelisted user" in {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor7"))).withHeaders(`Remote-Address`("192.65.127.80"), RawHeader("HTTP_EPPN", "someNonExistentUser@cheatOrganization.zw")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }
      }
      "respond correctly to write request with whitelisted saml user" in {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor8"))).withHeaders(`Remote-Address`("192.65.127.80"), RawHeader("HTTP_EPPN", "myself@testshib.org")) ~> myRoute ~> check {
          mediaType === `text/xml`
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
      }
    }

    //    def postTest(request: NodeSeq, remote: String = "127.0.0.1", mType: MediaType = `text/xml`, tests: MatchResult[Node]*): MatchResult[Any] = {
    //      Post("/", request).withHeaders(`Remote-Address`(remote)) ~> myRoute ~> check {
    //        mType === MediaType
    //
    //        val resp = responseAs[NodeSeq].head
    //        val response = resp showAs (n =>
    //          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))
    //
    //        tests.foldLeft[MatchResult[Any]](ok)((a, b) => b)
    //
    //      }
    //    }

    //   "respond with internal server error when requesting erroneous data from server" in {
    //     val request =
    //       <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
    //////////          <omi:read msgformat="odf">
    ////////            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    //////              <Objects>
    ////                <Object>
    //                  <id>testObject</id>
    //                  <InfoItem name="PowerConsumption"/>
    //                </Object>
    ////              </Objects>
    //////            </omi:msg>
    ////////          </omi:read>
    //////////        </omi:omiEnvelope>
    //       
    ////      Post("/", request).withHeaders(`Remote-Address`("127.0.0.1")) ~> myRoute ~> check {
    //     Get("/Objects/testObject") ~> myRoute ~> check {
    //        mediaType === `text/xml`
    //        
    //        val resp = responseAs[NodeSeq].head
    //        val response = resp showAs (n =>
    //          "Response:\n" + printer.format(n))
    //
    //        println(printer.format(resp))
    //        response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
    //      }
    //   }
    //
    //  }

    ////////////  "Read requests: OmiService" sho
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
}

