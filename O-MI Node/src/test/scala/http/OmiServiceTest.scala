package http

import org.specs2.mutable._
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import spray.http.StatusCodes._
import akka.testkit.TestActorRef

class OmiServiceTest extends Specification with Specs2RouteTest with HttpService {
  def actorRefFactory = system
  val omiServiceActorRef = TestActorRef[OmiServiceActor]
  val omiServiceActor = omiServiceActorRef.underlyingActor
  val testRoute = omiServiceActor.myRoute
  
  "OmiService" should {
    "return hello message for GET request to the root path" in {
      Get() ~> testRoute ~> check {
        responseAs[String] must contain("Say hello to <i>O-MI Node service")
      }
    }
    "testtesttest" in {
      Get("html") ~> testRoute ~> check {
        responseAs[String] must equalTo("test")
      }
    }
  }
}