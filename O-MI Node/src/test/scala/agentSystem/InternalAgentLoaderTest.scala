package agentSystem

import org.specs2.mutable._
import org.specs2.specification.Scope
import akka.actor.{ Props, ActorRef, ActorSystem }

import testHelpers.{ AfterAll }
import database._
import java.net.InetSocketAddress
import responses.{ SubscriptionHandler, RequestHandler }
import parsing._
import http._
import scala.concurrent.duration._
import scala.concurrent.Future
import agentSystem.InternalAgentCLICmds._

import akka.testkit.{ TestKit, TestActorRef, TestProbe, ImplicitSender, EventFilter }
import com.typesafe.config.ConfigFactory

import akka.util.Timeout
import akka.io.{ IO, Tcp }
import akka.pattern.ask

import scala.xml
import scala.xml._

class Actorstest(_system: ActorSystem) extends TestKit(_system) with Scope with After with ImplicitSender {

  def after = TestKit.shutdownActorSystem(system)
}


import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
@RunWith(classOf[JUnitRunner])
class InternalAgentLoaderTest extends Specification { // with AfterAll {
//  sequential

  "InternalAgentLoaderActor" should {
    "contain external agents in the bootables" in new Actorstest(ActorSystem()) {

      val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props(), "agent-loader")
      val actor = actorRef.underlyingActor
      val agents = actor.getAgents
      agents must haveKey("agents.VTTAgent")
      agents must haveKey("agents.SmartHouseAgent")
      agents must haveKey("agents.JavaAgent")
    }

    "be able to start and stop agents with start and stop messages" in new Actorstest(
      ActorSystem("startstop",
        ConfigFactory.load(
          ConfigFactory.parseString(
            """
            akka.loggers = ["akka.testkit.TestEventListener"]
            """).withFallback(ConfigFactory.load())))) {
      val agentName = "agents.JavaAgent"
      val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props(), "agent-loader")
      val actor = actorRef.underlyingActor
      val agents = actor.getAgents
      agents must haveKey(agentName)
      val eActor = agents(agentName).agent
      eActor must beSome.which { agent => agent.isAlive must beTrue }
      EventFilter.warning(message = ("Stopping: " + agentName), occurrences = 1) intercept {
        actorRef.receive(StopCmd(agentName))
      }
      eActor must beSome.which { _.isAlive must beFalse }
      //agent still there but agent in AgentInfo is None
      actor.getAgents must haveKey(agentName)
      actor.getAgents(agentName).agent must beNone

      EventFilter.warning(message = ("Starting: " + agentName), occurrences = 1) intercept {
        actorRef.receive(StartCmd(agentName))
      }

      Future { actor.getAgents(agentName).agent must beSome.which { agent => agent.isAlive must beTrue } }.await(retries = 4, timeout = scala.concurrent.duration.Duration.apply(1000, "ms"))

    }

    "be able to restart agents with restart command" in new Actorstest(
      ActorSystem("restart",
        ConfigFactory.load(
          ConfigFactory.parseString(
            """
            akka.loggers = ["akka.testkit.TestEventListener"]
            """).withFallback(ConfigFactory.load())))) {
      val agentName = "agents.VTTAgent"
      val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props(), "agent-loader")
      val actor = actorRef.underlyingActor

      val eActor = actor.getAgents(agentName).agent
      actor.getAgents must haveKey(agentName)
      EventFilter.warning("Re-Starting: " + agentName, occurrences = 1) intercept {
        actorRef.receive(ReStartCmd(agentName))
      }

      eActor must beSome.which { _.isAlive must beFalse }

      Future { actor.getAgents(agentName).agent must beSome.which(_.isAlive must beTrue) }.await(retries = 4, timeout = scala.concurrent.duration.Duration.apply(1000, "ms"))
    }

    "handle exceptions if trying to load non-existing agents" in new Actorstest(
      ActorSystem("loadnonexisting",
        ConfigFactory.load(
          ConfigFactory.parseString(
            """
            akka.loggers = ["akka.testkit.TestEventListener"]
            """).withFallback(ConfigFactory.load())))) {
      val agentName = "agents.nonExisting"
      val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props(), "agent-loader")
      val actor = actorRef.underlyingActor
      EventFilter.warning(start = "Classloading failed. Could not load: " + agentName, occurrences = 1) intercept {
        actor.loadAndStart(agentName, s"configs/$agentName")
      }
    }
//Test below works but gives primary key error on database
    /*"be able to handle ThreadExceptions from agents" in new Actorstest(
      ActorSystem("loadnonexisting",
        ConfigFactory.load(
          ConfigFactory.parseString(
            """
            akka.loggers = ["akka.testkit.TestEventListener"]
            agent-system.timeout-on-threadexception = 0
            """).withFallback(ConfigFactory.load())))) {
      
      val agentName = "agents.SmartHouseAgent"
      val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props(), "agent-loader")
      val actor = actorRef.underlyingActor
      val agents = actor.getAgents
      agents must haveKey(agentName)
      val eActor = agents(agentName).agent
      eActor must beSome.which { agent => agent.isAlive must beTrue }
      EventFilter.warning(pattern=
          """InternalAgent caugth exception: java\.lang\.Exception: test|Trying to relaunch: agents\.SmartHouseAgent"""
          , occurrences = 2) intercept{
        actorRef.receive(ThreadException(eActor.get, new Exception("test")))
      }
      val agents2 = actor.getAgents
      agents2 must haveKey(agentName)
      agents2(agentName).agent must beSome.which(_.isAlive must beTrue) 
//      eActor(agentName) must beSome.which { agent => agent.isAlive must beTrue }
    }*/
    
    
    
    
    
////does not work
    
//    "be able to load new agents and handle expectations that they throw" in new Actorstest(
//      ActorSystem("loadnew",
//        ConfigFactory.load(
//          ConfigFactory.parseString(
//            """
//            akka.loggers = ["akka.testkit.TestEventListener"]
//            """).withFallback(ConfigFactory.load())))) {
//
//    
//    class TestAgent(cp: String) extends InternalAgent(cp) {
//      def init() = ()
//      def loopOnce() = Thread.sleep(2000)
//      def finish() = ()
//    }
//
//    val agentName = "TestAgent"
//    val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props(), "agent-loader")
//    val actor = actorRef.underlyingActor
//    actor.loadAndStart(s"agentsystem.$agentName" + "$","")
//     1===1
//  }
    
    
  }
}
  
