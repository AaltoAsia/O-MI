package agentSystem

import org.specs2.mutable._
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestKit,TestActorRef, TestProbe}
import testHelpers.Actors
 

class AgentLoaderTest extends TestKit(ActorSystem()) with SpecificationLike with After {
  def after = system.shutdown()

                                  /*
  "AgentLoaderActor" should {
    "do something" in new Actors{
      val actorRef = TestActorRef(AgentLoader.props())
      val actor = actorRef.underlyingActor

      1 ===1
    }
  }
  */
  val actorRef = TestActorRef[AgentLoader]
  val actor = actorRef.underlyingActor
  val probe = TestProbe()
  actorRef ! ConfigUpdated  

  "AgentLoaderActor" should {
    sequential

    "contain agents.SmartHouseBoot in bootables" in {
      actor.getBootables.contains("agents.SmartHouseBoot") === true
    }
    
    "contain agents.SensorBoot in bootables" in {
      actor.getBootables.contains("agents.SensorBoot") === true
    }
    
    "return class name and matching config path when getClassnamesWithConfigPath method is called" in {
      actor.getClassnamesWithConfigPath.contains(("agents.SmartHouseBoot", "configs/SmartHouseConfig")) === true
      actor.getClassnamesWithConfigPath.contains(("agents.SensorBoot", "configs/SensorConfig")) === true
    }
    
    "contain the same bootables when configs are not updated" in {
      val test1 = actor.getBootables.get("agents.SmartHouseBoot")
      val test2 = actor.getBootables.get("agents.SensorBoot")
      
      test1 must beSome
      test2 must beSome
      
      actorRef.tell(ConfigUpdated, probe.ref)
      
      Thread.sleep(1000)

      actor.getBootables.get("agents.SmartHouseBoot").get === test1.get
      actor.getBootables.get("agents.SensorBoot").get === test2.get
    }

  }
}
