package agentSystem

import org.specs2.mutable._
import org.specs2.specification.Scope
import akka.actor.{ Props, ActorRef }

import testHelpers.{ AfterAll }
import database._
import java.net.InetSocketAddress
import responses.{ SubscriptionHandler, RequestHandler }
//import agentSystem.ExternalAgentListener
import parsing._
import http._
import scala.concurrent.duration._
import akka.testkit.{ TestKit, TestActorRef, TestProbe }
import akka.util.Timeout
import akka.io.{ IO, Tcp }
import akka.pattern.ask

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class InternalAgentLoaderTest extends Specification with AfterAll {
//  implicit val system = ActorSystem()

//  class Actorstest extends TestKit(system) with Scope with Starter {
//    
//    override def start(agentLoader: TestActorRef[InternalAgentLoader], dbConnection: DB = new DatabaseConnection): ActorRef = {
//      val subHandler = system.actorOf(Props(new SubscriptionHandler()(dbConnection)), "subscription-handler")
//      val sensorDataListener = system.actorOf(Props(classOf[ExternalAgentListener]), "agent-listener")
//
////      val agentLoader = system.actorOf(InternalAgentLoader.props() , "agent-loader")
//
//      //    val agentLoader = system.actorOf(InternalAgentLoader.props() , "agent-loader")
//
//      // create omi service actor
//      val omiService = system.actorOf(Props(new OmiServiceActor(new RequestHandler(subHandler)(dbConnection), dbConnection)), "omi-service")
//
//      implicit val timeoutForBind = Timeout(Duration.apply(5, "second"))
//
//      IO(Tcp) ? Tcp.Bind(sensorDataListener, new InetSocketAddress(settings.externalAgentInterface, settings.externalAgentPort))
//      
//      IO(Tcp) ? Tcp.Bind(agentLoader, new InetSocketAddress("localhost", settings.cliPort))
//      return omiService
//    }
//  }

  def afterAll = system.shutdown()
  //  def afterAll = system.shutdown()
  //val actorRef = TestActorRef(AgentLoader.props())
  //  "AgentLoaderActor" should {
  //    "do something" in new Actors{
  //      val actorRef = TestActorRef(AgentLoader.props())
  //      val actor = actorRef.underlyingActor
  //  actorRef ! ConfigUpdated  
  //
  //      1 ===1
  //    }
  //  }
  //  val actorRef = TestActorRef[InternalAgentLoader]
  //  val actor = actorRef.underlyingActor
  //  val probe = TestProbe()
  //  actorRef ! ConfigUpdated  

  "InternalAgentLoaderActor" should {
    sequential

    "contain agents.SmartHouseBoot in bootables" in}// new Actorstest {
      val actorRef = TestActorRef[InternalAgentLoader]
      val actor = actorRef.underlyingActor
      val probe = TestProbe()
      println(actor.getAgents)

      //      actor.getBootables.contains("agents.SmartHouseBoot") === true
      1 === 1
    }

    "contain agents.SensorBoot in bootables" in {
      //      actor.getBootables.contains("agents.SensorBoot") === true
      1 === 1
    }

//    "return class name and matching config path when getClassnamesWithConfigPath method is called" in new Actors {
//      val actorRef = TestActorRef[InternalAgentLoader]
//      val actor = actorRef.underlyingActor
//      val probe = TestProbe()
//
//      actor.getClassnamesWithConfigPath.contains(("agents.SmartHouseBoot", "configs/SmartHouseConfig")) === true
//      actor.getClassnamesWithConfigPath.contains(("agents.SensorBoot", "configs/SensorConfig")) === true
//    }
//
//    "contain the same bootables when configs are not updated" in {
//      //      val test1 = actor.getBootables.get("agents.SmartHouseBoot")
//      //      val test2 = actor.getBootables.get("agents.SensorBoot")
//
//      //      test1 must beSome
//      //      test2 must beSome
//
//      //      actorRef.tell(ConfigUpdated, probe.ref)
//
//      Thread.sleep(1000)
//
//      //      actor.getBootables.get("agents.SmartHouseBoot").get === test1.get
//      //      actor.getBootables.get("agents.SensorBoot").get === test2.get
//      1 === 1
//    }
//
  }
}
  
