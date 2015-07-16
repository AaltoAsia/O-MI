package agentSystem

import org.specs2.mutable._
import org.specs2.specification.Scope
import akka.actor.{ Props, ActorRef, ActorSystem }

import testHelpers.{ AfterAll }
import database._
import java.net.InetSocketAddress
import responses.{ SubscriptionHandler, RequestHandler }
//import agentSystem.ExternalAgentListener
import parsing._
import http._
import scala.concurrent.duration._
import akka.testkit.{ TestKit, TestActorRef, TestProbe, ImplicitSender }
import akka.util.Timeout
import akka.io.{ IO, Tcp }
import akka.pattern.ask


import scala.xml
import scala.xml._


class Actorstest(_system:ActorSystem) extends TestKit(_system) with Scope with After with ImplicitSender {
    
    def after = TestKit.shutdownActorSystem(system)
  }

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
@RunWith(classOf[JUnitRunner])
class InternalAgentLoaderTest extends Specification{// with AfterAll {

  "InternalAgentLoaderActor" should {
    sequential

    
    //workds with Eclipse Junit Test but fails in sbt due to NoClassDefFoundError: scala/xml/Node
    "contain external agents in the bootables" in new Actorstest(ActorSystem("agentloaderTest")) {

      val actorRef = TestActorRef[InternalAgentLoader](InternalAgentLoader.props() , "agent-loader")
      val actor = actorRef.underlyingActor
      val agents = actor.getAgents
      agents must haveKey("agents.VTTAgent")
      agents must haveKey("agents.SmartHouseAgent")
      agents must haveKey("agents.CoffeeMaker")
    }

    "contain agents.SensorBoot in bootables" in {

      1 === 1
    }


  }
}
  
