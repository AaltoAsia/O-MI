package agentSystemInterface

import org.specs2.mutable._
import akka.testkit.{ TestProbe, TestKit, EventFilter }
import akka.actor._
import org.specs2.specification.Scope
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress
import akka.io.Tcp._
import scala.io.Source
import database._

class AgentListenerTest extends Specification with Before {

  def before = SQLite.init

  class Actors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with Scope

  val local = new InetSocketAddress("localhost", 1234)
  val remote = new InetSocketAddress("remote", 4321)
  lazy val testOdf = Source.fromFile("src/test/scala/testOdf.xml").getLines().mkString("")

  "AgentListener" should {

    "reply with Register message when it receives Connected message" in new Actors {
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()

      actor.tell(Connected(local, remote), probe.ref)
      probe.expectMsgType[Register]
    }

    "log Connected event with ActorLogging" in new Actors {
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()

      EventFilter.info(s"Agent connected from $local to $remote", occurrences = 1) intercept {
        actor.tell(Connected(local, remote), probe.ref)
      }
    }

    "be terminated when receive CommandFailed message" in new Actors {
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()
      val bind = new Bind(probe.ref, remote)

      probe watch actor

      actor.tell(CommandFailed(bind), probe.ref)
      probe.expectTerminated(actor)
    }

    "log CommandFailed message with ActorLogging" in new Actors {
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()
      val bind = new Bind(probe.ref, remote)

      EventFilter.warning(s"Agent connection failed: $bind", occurrences = 1) intercept {
        actor.tell(CommandFailed(bind), probe.ref)
      }
    }

    "reply with Register messages to multiple actors" in new Actors {
      val actor = system.actorOf(Props[AgentListener])
      val probe1 = TestProbe()
      val probe2 = TestProbe()
      val probe3 = TestProbe()
      val probe4 = TestProbe()
      val probe5 = TestProbe()

      actor.tell(Connected(local, remote), probe1.ref)
      actor.tell(Connected(local, remote), probe2.ref)
      actor.tell(Connected(local, remote), probe3.ref)
      actor.tell(Connected(local, remote), probe4.ref)
      actor.tell(Connected(local, remote), probe5.ref)

      probe1.expectMsgType[Register]
      probe2.expectMsgType[Register]
      probe3.expectMsgType[Register]
      probe4.expectMsgType[Register]
      probe5.expectMsgType[Register]
    }
  }

  "InputDataHandler" should {
    sequential
    "receive sended data" in new Actors {
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()
      EventFilter.debug(message = "Got data \n" + testOdf) intercept {
        actor.tell(Received(akka.util.ByteString(testOdf)), probe.ref)
      }
    }

    "save sent data into database" in new Actors {
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()
      SQLite.clearDB()
      actor.tell(Received(akka.util.ByteString(testOdf)), probe.ref)
      //SQLite.get("Objects/SmartHouse/Moisture") must not be equalTo(None)      
      awaitCond(SQLite.get("Objects/SmartHouse/Moisture") != None, scala.concurrent.duration.Duration.apply(2500, "ms"), scala.concurrent.duration.Duration.apply(500, "ms"))

      
    }

    "log warning when it encounters node with no information" in new Actors {
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()
      EventFilter.warning(start = "Throwing away node: ") intercept {
        actor.tell(Received(akka.util.ByteString(testOdf)), probe.ref)
      }
    }

    "log warning when sending malformed data" in new Actors {
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()
      EventFilter.warning(message = s"Malformed odf received from agent ${probe.ref}: Invalid XML", occurrences = 1) intercept {
        actor.tell(Received(akka.util.ByteString(testOdf.replaceAll("Objects", ""))), probe.ref)
      }
    }

    "write info to log when it receives PeerClosed message" in new Actors {
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()

      EventFilter.info(s"Agent disconnected from $local", occurrences = 1) intercept {
        actor.tell(PeerClosed, probe.ref)
      }

    }

    "be terminated when it receives PeerClosed message" in new Actors {
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()

      probe watch actor
      actor.tell(PeerClosed, probe.ref)
      probe.expectTerminated(actor)
    }

  }
}
