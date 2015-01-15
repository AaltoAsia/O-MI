package agentSystemInterface

//import org.specs2._
import org.specs2.mutable._
import org.specs2.time.NoTimeConversions
import org.specs2.specification.Scope
import akka.actor._
import akka.testkit._
import akka.testkit.TestProbe
import java.util.concurrent.TimeUnit

import sensorDataStructure.SensorMap

//abstract class AkkaTestkitSpecs2Support extends TestKit(ActorSystem())
//                                           with After
//                                           with ImplicitSender {
//def after = system.
//}

class AgentListenerTest extends Specification with NoTimeConversions {
    val testMap = new SensorMap("/Objects/SmartHouse/SmartFridge/PowerConsumption")
  class Actors extends TestKit(ActorSystem("test")) with Scope with After{
    val actor = system.actorOf(Props(classOf[AgentListener], testMap))
    val probe = TestProbe()
    
    def after = system.shutdown
    //    val testRef = TestActorRef(new AgentListener(testMap))
    //    testRef ! "asd"
  }
//    def after = system.shutdown
  "AgentListener" should {
    "return test message" in new Actors{
//      val asda = TestProbe()
      println("testtesttesttesttesttesttesttesttesttesttesttest")
      actor.tell("ping", probe.ref)
      probe.expectMsg("pong")
      
    }
  }
  
}