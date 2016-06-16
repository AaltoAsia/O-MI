package http

import java.net.InetAddress

import scala.concurrent.duration.DurationInt
import scala.xml._

import agentSystem.AgentSystem
import akka.actor._
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.headers.{RawHeader, `Remote-Address`}
import akka.http.scaladsl.testkit.{RouteTest, RouteTestTimeout}
import akka.testkit.TestActorRef
import database._
import org.slf4j.LoggerFactory
import org.specs2.matcher.XmlMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import responses.{RequestHandler, SubscriptionManager}
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport.defaultNodeSeqUnmarshaller
import testHelpers.Specs2Interface
import types._

class OmiServiceTest
  extends {
    override val log = LoggerFactory.getLogger("OmiServiceTest")
  }
  with Specification
  with Specs2Interface
  with XmlMatchers
  with RouteTest
  with OmiService
  with BeforeAfterAll
{

  def actorRefFactory = system
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.second)
  //lazy val log = akka.event.Logging.getLogger(actorRefFactory, this)
  implicit val dbConnection = new TestDB("system-test")
  val subscriptionHandler = TestActorRef(Props(new SubscriptionManager()(dbConnection)))

  val agentManager = system.actorOf(
      AgentSystem.props(dbConnection, subscriptionHandler),
      "agent-system"
  )
  val requestHandler = new RequestHandler(subscriptionHandler, agentManager)(dbConnection)
  val printer = new scala.xml.PrettyPrinter(80, 2)

  val localHost = RemoteAddress(InetAddress.getLocalHost)

  "System tests for features of OMI Node service".title


  def beforeAll() = {
    Boot.saveSettingsOdf(agentManager)//Boot.init(dbConnection)
    //Thread.sleep(300)
    // clear if some other tests have left data
    //    dbConnection.clearDB()

    // Initialize the OmiService
    //    Boot.main(Array())
  }

  def afterAll() = {
    // clear db
    dbConnection.destroy()
    system.terminate()
  }

  "Data discovery, GET: OmiService" >> {

    "respond with hello message for GET request to the root path" >> {
      Get() ~> myRoute ~> check {
        mediaType === `text/html`
        responseAs[String] must contain("Say hello to <i>O-MI Node service")

      }
    }

    "respond succesfully to GET to /Objects" >> {
      Get("/Objects") ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        responseAs[NodeSeq].headOption must beSome.which(_.label == "Objects") // => ??? }((_: Node).label == "Objects")//.head.label === "Objects"
      }
    }
    "respond succesfully to GET to /Objects/" >> {
      Get("/Objects/") ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        responseAs[NodeSeq].headOption must beSome.which(_.label == "Objects")
      }
    }
    "respond with error to non existing path" >> {
      Get("/Objects/nonexsistent7864057") ~> myRoute ~> check {
        mediaType === `text/xml`
        status === NotFound
        responseAs[NodeSeq].headOption must beSome.which(_.label == "error") //head.label === "error"
      }
    }
    val settingsPath = "/" + Path(Boot.settings.settingsOdfPath).toString
    "respond successfully to GET to some value" >> {
      Get(settingsPath + "/num-latest-values-stored/value") ~> myRoute ~> check {
        mediaType === `text/plain`
        status === OK
        responseAs[String] === "10"
      }

    }


    // Somewhat overcomplicated test; Serves as an example for other tests
    "reply its settings as odf frorm path `settingsOdfPath` (with \"Settings\" id)" >> {
      Get(settingsPath) ~> myRoute ~> check { // this didn't work without / at start
        status === OK
        mediaType === `text/xml`
        responseAs[NodeSeq] must \("id") \> "Settings"
      }
    }

    "reply its settings having num-latest-values-stored)" >> {
      Get(settingsPath) ~> myRoute ~> check { // this didn't work without / at start
        status === OK
        mediaType === `text/xml`
        responseAs[NodeSeq] must \("InfoItem", "name" -> "num-latest-values-stored")
      }
    }

  }

  "request, POST: OmiService" >> {
    sequential
    "respond correctly to read request with invalid omi" >> {
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

      Post("/", request).withHeaders(`Remote-Address`(localHost)) ~> myRoute ~> check {
        mediaType === `text/xml`
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))
        //                    println(printer.format(resp))
        //          println("\n\n\n\n\n______________________________________")
        status === OK

        response must \("response") \ ("result") \ ("return", "returnCode" -> "400")
        val description = resp.\("response").\("result").\("return").\@("description")
        description must startWith("OmiParser: Invalid XML, schema failure:")
      }
    }

    "respond correctly to read request with invalid odf" >> {
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

      Post("/", request).withHeaders(`Remote-Address`(localHost)) ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))


        response must \("response") \ ("result") \ ("return", "returnCode" -> "400")
        val description = resp.\("response").\("result").\("return").\@("description")
        description must startWith("OdfParser: Invalid XML, schema failure:")
      }
    }

    "respond correctly to read request with non-existing path" >> {
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

      Post("/", request).withHeaders(`Remote-Address`(localHost)) ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//        println(printer.format(resp))

        response must \("response") \ ("result") \ ("return", "returnCode" -> "404")
        val description = resp.\("response").\("result").\("return").\@("description")
        description === "Such item/s not found."
      }
    }

    "respond correctly to subscription poll with non existing requestId" >> {
      val request: NodeSeq =
        <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
          <omi:read msgformat="odf">
            <omi:requestID>9999</omi:requestID>
          </omi:read>
        </omi:omiEnvelope>

      Post("/", request).withHeaders(`Remote-Address`(localHost)) ~> myRoute ~> check {
        mediaType === `text/xml`
        status === OK
        val resp = responseAs[NodeSeq].head
        val response = resp showAs (n =>
          "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

        //        println(printer.format(resp))

        response must \("response") \ ("result") \ ("return", "returnCode" -> "404")
        val description = resp.\("response").\("result").\("return").\@("description")
        description === "A subscription with this id has expired or doesn't exist"
      }
    }

    "respond to permissive requests" >> {
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

      "respond correctly to write request with whitelisted IPv4-addresses" >> {
        Post("/", XML.loadString(request)).withHeaders(`Remote-Address`(localHost)) ~> myRoute ~> check {
          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }

        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor1")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("127.255.255.255")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
      }

      "respond correctly to write request with non-whitelisted IPv4-addresses" >> {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor2")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("192.65.127.80")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }

        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor3")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("128.0.0.1")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }

      }

      "respond correctly to write request with whitelisted IPv6-addresses" >> {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor4")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("0:0:0:0:0:0:0:1")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor5")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("0:0:0:FFFF:FFFF:FFFF:FFFF:FFFF")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
        }
      }
      "respond correctly to write request with non-whitelisted IPv6-addresses" >> {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor4")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("0:0:1:0:0:0:0:0")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

//          println(printer.format(resp))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }

        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor4")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("2001:DB80:ABBA:BABB:A:0:FF:FF")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }
      }
      "respond correctly to normal read with non-whitelisted address and user" >> {
        val request: String = """
          <omi:omiEnvelope xmlns:omi="omi.xsd" version="1.0" ttl="0">
            <omi:read msgformat="odf">
              <omi:msg xmlns="odf.xsd">
                <Objects xmlns="odf.xsd">
                </Objects>
              </omi:msg>
            </omi:read>
          </omi:omiEnvelope>"""
        Post("/", XML.loadString(request))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("192.65.127.80")))) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object")
        }
        Post("/", XML.loadString(request))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("187.42.74.1"))),
                       RawHeader("HTTP_EPPN", "someNonExistentUser@cheatOrganization.zw")) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "200")
          response must \("response") \ ("result") \ ("msg") \ ("Objects") \ ("Object")
        }
      }
      "respond correctly to write request with non-whitelisted user" >> {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor7")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("192.65.127.80"))),
                       RawHeader("HTTP_EPPN", "someNonExistentUser@cheatOrganization.zw")) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK
          val resp = responseAs[NodeSeq].head
          val response = resp showAs (n =>
            "Request:\n" + request + "\n\n" + "Response:\n" + printer.format(n))

          response must \("response") \ ("result") \ ("return", "returnCode" -> "401")
          val description = resp.\("response").\("result").\("return").\@("description")
          description startsWith("Unauthorized")
        }
      }
      "respond correctly to write request with whitelisted saml user" >> {
        Post("/", XML.loadString(request.replaceAll("testSensor", "testSensor8")))
          .withHeaders(`Remote-Address`(RemoteAddress(InetAddress.getByName("192.65.127.80"))),
                       RawHeader("HTTP_EPPN", "myself@testshib.org")) ~> myRoute ~> check {

          mediaType === `text/xml`
          status === OK

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

