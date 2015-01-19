package agentSystemInterface

import org.specs2.mutable._
import akka.testkit.{TestProbe, TestActorRef, TestKit, EventFilter}
import akka.actor._
import org.specs2.specification.Scope
import org.specs2.mock._
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress
//import sensorDataStructure.SensorMap
import akka.io.Tcp._
class AgentListenerTest extends Specification{

  class Actors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with Scope 
  
  
  
  val local = new InetSocketAddress("localhost", 1234)
  val remote = new InetSocketAddress("remote", 4321)
//  val bind = new akka.io.Tcp.Bind(local, remote)
  
  "AgentListener" should {
    
    "reply with Register message when it receives Connected message" in new Actors{
//      val testMap = new SensorMap("/Objects/SmartHouse/SmartFridge/PowerConsumption")
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()
      actor.tell(Connected(local, remote), probe.ref)
      probe.expectMsgType[Register]
    }
    
    "log Connected event with ActorLogging" in new Actors{
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()
      EventFilter.info(s"Agent connected from $local to $remote", occurrences = 1) intercept {
      actor.tell(Connected(local, remote), probe.ref)
      }
    }
    
    "be terminated when receive CommandFailed message" in new Actors{
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()
      probe watch actor
      val bind = new Bind(probe.ref, remote)
      actor.tell(CommandFailed(bind), probe.ref)
      probe.expectTerminated(actor)
    }
    
    "log CommandFailed message with ActorLogging" in new Actors{
      val actor = system.actorOf(Props[AgentListener])
      val probe = TestProbe()
      val bind = new Bind(probe.ref, remote)
      EventFilter.warning(s"Agent connection failed: $bind", occurrences = 1) intercept {
      actor.tell(CommandFailed(bind), probe.ref)
      }
    }
  }
  
  "InputDataHandler" should{
    
//    "do something" in new Actors{
//      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
//      val probe = TestProbe()
//      actor.tell(Received, probe.ref)
//    }
    
    "write info to log when it receives PeerClosed message" in new Actors{
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()
      EventFilter.info(s"Agent disconnected from $local", occurrences = 1) intercept{
       actor.tell(PeerClosed, probe.ref)
      }
      
    }
    
    "be terminated when it receives PeerClosed message" in new Actors{
      val actor = system.actorOf(Props(classOf[InputDataHandler], local))
      val probe = TestProbe()
      probe watch actor
      actor.tell(PeerClosed, probe.ref)
      probe.expectTerminated(actor)
    }
    
  }
}
