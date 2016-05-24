package responses

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.testkit.TestProbe
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import spray.can.Http
import testHelpers.{Actors, SystemTestCallbackServer}

import scala.concurrent.Future
import scala.concurrent.duration._

class CallbackHandlerTest(implicit ee: ExecutionEnv) extends Specification {
  "CallbackHandler" should {
    "Send callback to the correct address" in new Actors {
      val port = 20003
      val probe = initCallbackServer(port)
      val msg  = <msg>success1</msg>
      Thread.sleep(1000)
      CallbackHandlers.sendCallback(s"http://localhost:$port",msg,Duration(2, "seconds"))

      probe.expectMsg(2 seconds, Option(msg))
    }

    "Try to keep sending message until ttl is over" in new Actors {
      val port = 20004
      val msg  = <msg>success2</msg>
      Future{CallbackHandlers.sendCallback(s"http://localhost:$port", msg, Duration(10, "seconds"))}

      Future{
        Thread.sleep(4000)
        initCallbackServer(port)
      }


      //val probe = initCallbackServer(port)
      //probe.expectMsg(5 seconds, Option(msg))
    }
  }

  def initCallbackServer(port: Int)(implicit system: ActorSystem): TestProbe = {
      val probe = TestProbe()
      val testServer = system.actorOf(Props(classOf[SystemTestCallbackServer], probe.ref))
      IO(Http) ! Http.Bind(testServer, interface = "localhost", port = 20003)
      probe
  }
}
