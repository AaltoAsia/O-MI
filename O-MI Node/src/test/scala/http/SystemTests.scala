package http

import parsing.Path

import org.specs2.mutable.Specification
import spray.testkit.Specs2RouteTest
import spray.http._
import StatusCodes._
import MediaTypes._
import StatusCodes._
import xml._


class OmiServiceSpec extends Specification with Specs2RouteTest with OmiService {
    def actorRefFactory = system
    lazy val log = akka.event.Logging.getLogger(actorRefFactory, this)

    Starter.init()
      
    "OmiService (Data discovery)" should {

      "respond succesfully to GET to /Objects" in {
        Get("/Objects") ~> myRoute ~> check {
          mediaType === `text/xml`
          // status === Success // != 200 OK ????
          responseAs[String] must contain("<Objects>")
          responseAs[String] must contain("</Objects>")
        }
      }
      "respond succesfully to GET to /Objects/" in {
        Get("/Objects/") ~> myRoute ~> check {
          mediaType === `text/xml`
          //status === Success
          responseAs[String] must contain("<Objects>")
          responseAs[String] must contain("</Objects>")
        }
      }
      "respond with error to non existing path" in {
        Get("/Objects/nonexsistent7864057") ~> myRoute ~> check {
          mediaType === `text/xml`
          status === NotFound
          responseAs[String] must contain("<error>")
          responseAs[String] must contain("</error>")
        }
      }

      "reply its settings as odf from path `settingsOdfPath`" in {
        val path = Path(Starter.settings.settingsOdfPath).toString
        path === "Objects/OMI-Service/Settings"
        Get(path) ~> myRoute ~> check {
          mediaType === `text/xml`
          responseAs[NodeSeq] must contain(
            <Object><id>Settings</id></Object>
            )
        }
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

}

