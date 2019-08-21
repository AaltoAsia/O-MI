package responses

import scala.concurrent.duration._
import scala.concurrent.Await
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import http.OmiConfig
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import testHelpers.{Actorstest, SystemTestCallbackServer, DummySingleStores}
import types.omi._
import testHelpers._
import scala.concurrent.duration._

class CallbackHandlerTest(implicit ee: ExecutionEnv) extends Specification {

  sequential

  "CallbackHandler" should {

    "Send callback to the correct address" in new Actorstest(Actorstest.createSilentAs()) {
      implicit val materializer = ActorMaterializer()
      val port = 20003
      val (server,probe) = initCallbackServer(port)
      val ttl = Duration(2, "seconds")
      val msg = Responses.Success(ttl = ttl)
      val msgStr = msg.asXMLSource.runWith(Sink.fold("")(_ + _))

      val settings = OmiConfig(system)

      val callbackHandler = new CallbackHandler(settings, new DummySingleStores())(system, materializer)
      callbackHandler.sendCallback(HTTPCallback(Uri(s"http://localhost:$port")), msg)

      probe.expectMsg(ttl, Option(Await.result(msgStr, 5.seconds)))

      server.unbind().onComplete(_ => system.terminate())
    }

    "Try to keep sending message until ttl is over" in new Actorstest(Actorstest.createSilentAs())  {
      implicit val materializer = ActorMaterializer()
      val port = 20004
      val ttl = Duration(10, "seconds")
      val msg = Responses.Success(ttl = ttl)
      val msgStr = msg.asXMLSource.runWith(Sink.fold("")(_ + _))

      val settings = OmiConfig(system)
      val callbackHandler = new CallbackHandler(settings, new DummySingleStores())(system, materializer)
      callbackHandler.sendCallback(HTTPCallback(Uri(s"http://localhost:$port")), msg)

      Thread.sleep(500)
      val (server,probe) = initCallbackServer(port)

      probe.expectMsg(ttl, Option(Await.result(msgStr, 5.seconds)))

      server.unbind().onComplete(_ => system.terminate())
    }
  }

  def initCallbackServer(port: Int)(implicit system: ActorSystem) = {
    //implicit val timeout = Timeout(5 seconds)
    val probe = TestProbe()
    val testServer = new SystemTestCallbackServer(probe.ref, "localhost", port)
    (testServer,probe)
  }
}
