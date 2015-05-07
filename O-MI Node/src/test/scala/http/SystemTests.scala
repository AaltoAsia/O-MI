package http

import parsing._
import parsing.Types._
import parsing.Types.Path._

import xml._

import org.specs2.mutable.Specification
import org.specs2.matcher.XmlMatchers

import spray.testkit.Specs2RouteTest
import spray.httpx.marshalling.BasicMarshallers._
import spray.http._
import HttpMethods._
import StatusCodes._
import MediaTypes._
import StatusCodes._

import database._

class OmiServiceSpec extends Specification
                        with XmlMatchers
                        with Specs2RouteTest
                        with OmiService {
    def actorRefFactory = system
    lazy val log = akka.event.Logging.getLogger(actorRefFactory, this)

    implicit val dbConnection = new SQLiteConnection // TestDB("system-test")
    implicit val dbobject = dbConnection
    
    val subscriptionHandler = akka.actor.ActorRef.noSender
    
    "System tests for features of OMI Node service".title

    step {
      // clear if some other tests have left data
      dbConnection.clearDB()

      // Initialize the OmiService
      Starter.init()
    }
      
    "Data discovery, GET: OmiService" should {
      
      "respond with hello message for GET request to the root path" in {
        Get() ~> myRoute ~> check{
          mediaType === `text/html`
          responseAs[String] must contain ("Say hello to <i>O-MI Node service")
        }
      }
      
      "respond succesfully to GET to /Objects" in {
        Get("/Objects") ~> myRoute ~> check {
          mediaType === `text/xml`
          status === OK
          responseAs[NodeSeq].head.label === "Objects"
        }
      }
      "respond succesfully to GET to /Objects/" in {
        Get("/Objects/") ~> myRoute ~> check {
          mediaType === `text/xml`
          status === OK
          responseAs[NodeSeq].head.label === "Objects"
        }
      }
      "respond with error to non existing path" in {
        Get("/Objects/nonexsistent7864057") ~> myRoute ~> check {
          mediaType === `text/xml`
          status === NotFound
          responseAs[NodeSeq].head.label === "error"
        }
      }
      "respond successfully to GET to some value" in {
        dbConnection.set(new DBSensor(Path("Objects/SystemTests/TestValue"), "123", new java.sql.Timestamp(1000)))
        
        Get("/Objects/SystemTests/TestValue/value") ~> myRoute ~> check {
          mediaType === `text/plain`
          status === OK
          responseAs[String] === "123"
        }
        
      }

      val settingsPath = "/" +Path(Starter.settings.settingsOdfPath).toString

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
      val fridgeData = DBSensor(Path("Objects/SmartFridge22334411/PowerConsumption"),
        powerConsumptionValue,
        dataTime
      )
      log.debug("set data")
      dbConnection.set(fridgeData)


      val readTestRequestFridge: NodeSeq =
        // NOTE: The type needed for compiler to recognize the right Marhshaller later
            <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
              <omi:read msgformat="omi.xsd">
                <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
                  <Objects>
                    <Object>
                      <id>SmartFridge22334411</id>
                      <InfoItem name="PowerConsumption" />
                    </Object>
                  </Objects>
                </omi:msg>
              </omi:read>
            </omi:omiEnvelope>
        
      val invalidReadTestRequestFridge: NodeSeq =
        // NOTE: The type needed for compiler to recognize the right Marhshaller later
            <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
              <omi:read msgformat="omi.xsd">
                <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
                  <sss>
                    <Object>
                      <id>SmartFridge22334411</id>
                      <InfoItem name="PowerConsumption" />
                    </Object>
                  </sss>
                </omi:msg>
              </omi:read>
            </omi:omiEnvelope>
    

      "handle a single read request and the response" should {
        "return error with invalid request" in {
          Post("/", invalidReadTestRequestFridge) ~> myRoute~> check {
            status === BadRequest // TODO this test needs to be updated when error handling is correctly implemented
          }
        }

        Post("/", readTestRequestFridge) ~> myRoute ~> check {

          // XXX: This test is hacky as it is a nested "should"
          val response = responseAs[NodeSeq].head
          val mtype = mediaType
          val rstatus = status

          "be xml that has a success return code (200)" in {
            mtype === `text/xml`
            rstatus === OK
            response must \("response") \("result", "msgformat" -> "odf")
            response must \("response") \("result") \("return", "returnCode" -> "200")
          }


          val msg = response \ "response" \ "result" \ "msg"
          val infoitem = msg \ "Objects" \ "Object" \ "InfoItem"

          "have the right InfoItem" in {

            response must \("response") \("result") \("msg")

            msg must \("Objects") \("Object") \("InfoItem", "name" -> "PowerConsumption")
          }

          "infoitem has the right value" in {
            infoitem must have length(1)
            infoitem must \("value") \> powerConsumptionValue // "180"
          }
        }
      }
    }

    step {
      // clear db
      dbConnection.clearDB()
    }

      /** EXAMPLES:

      "return a greeting for GET requests to the root path" in {
        Get() ~> myRoute ~> check {
                  responseAs[String] must contain("Say hello")
        }
      }

      "leave GET requests to other paths unhandled" in {
        Get("/kermit") ~> myRoute ~> check {
                  handled must beFalse
        }
      }

      "return a MethodNotAllowed error for PUT requests to the root path" in {
        Put() ~> sealRoute(myRoute) ~> check {
                  status === MethodNotAllowed
                          responseAs[String] === "HTTP method not allowed, supported methods: GET"
        }
      }
      */
      

}

