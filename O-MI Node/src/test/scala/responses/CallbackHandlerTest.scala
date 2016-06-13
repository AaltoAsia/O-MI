package responses

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import akka.pattern.ask
import akka.io.IO
import akka.testkit.TestProbe
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import spray.can.Http
import testHelpers.{Actors, SystemTestCallbackServer}

class CallbackHandlerTest(implicit ee: ExecutionEnv) extends Specification {
  "CallbackHandler" should {
    "Send callback to the correct address" in new Actors {
      val port = 20003
      val probe = initCallbackServer(port)
      val msg  = <msg>success1</msg>

      CallbackHandlers.sendCallback(s"http://localhost:$port",msg,Duration(2, "seconds"))

      probe.expectMsg(2 seconds, Option(msg))
    }

    "Try to keep sending message until ttl is over" in new Actors {
      val port = 20004
      val msg  = <msg>success2</msg>
      CallbackHandlers.sendCallback(s"http://localhost:$port", msg, Duration(10, "seconds"))

      val testProbeFuture = Future{
        Thread.sleep(4000)
        initCallbackServer(port)
      }

      val probe = Await.result(testProbeFuture, 5 seconds)
      probe.expectMsg(5 seconds, Option(msg))

    }
  }

  def initCallbackServer(port: Int)(implicit system: ActorSystem): TestProbe = {
      val probe = TestProbe()
      val testServer = system.actorOf(Props(classOf[SystemTestCallbackServer], probe.ref))
      implicit val timeout = Timeout(1 seconds)
      val bindFuture = IO(Http) ? Http.Bind(testServer, interface = "localhost", port = port)
      Await.ready(bindFuture, 2 seconds) //wait for bind to complete
      probe
  }
}
