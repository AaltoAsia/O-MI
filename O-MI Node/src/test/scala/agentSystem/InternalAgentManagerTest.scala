package agentSystem

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable.Map
import akka.actor.{Actor, ActorSystem, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.create.InterpolatedFragment
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.matcher.FutureMatchers._
import com.typesafe.config.{ConfigFactory, Config}
import agentSystem._
import http.CLICmds._
import testHelpers.Actorstest



//class InternalAgentManagerTest(implicit ee: ExecutionEnv) extends Specification {

   /*
 "InternalAgentManager should" >>{
   "return message when trying to" >> {
     "start allready running agent" >> startingRunningTest
     "stop allready stopped agent" >> stopingStoppedTest
     "command nonexistent agent" >> cmdToNonExistent
   }
   "successfully " >> {
     "stop running agent" >> stopingRunningTest
     "start stopped agent" >> startingStoppedTest
   }
   "return error message if agent fails to" >> {
     "stop" >> agentStopFailTest
     "start" >> agentStartFailTest
   }
 }  
 */
 /**
  * TODO: Refactor test. Agents needs proper implementations because 
  * switched off from using custom Start() and Stop() messages.
 def AS = ActorSystem() 
 def emptyConfig = ConfigFactory.empty()
 def timeoutDuration= 10.seconds
 implicit val timeout = Timeout( timeoutDuration )
 def cmdToNonExistent = new Actorstest(AS){
   import system.dispatcher
   val name = "Nonexisting"
   val testAgents : Map[AgentName, AgentInfo] = Map.empty
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StartAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = managerActor.couldNotFindMsg(name)
   resF should beEqualTo(correct).await( 0, timeoutDuration)
 }

 def startingRunningTest = new Actorstest(AS){
   import system.dispatcher
   val name = "Running"
   val ref = ActorRef.noSender
   val clazz = "agentSystem.SSAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, None, running = true, Nil, Scala())
   val testAgents = Map( name -> agentInfo)
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StartAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = managerActor.wasAlreadyStartedMsg(name)
   resF should beEqualTo(correct).await( 0, timeoutDuration)
 }
 def stopingStoppedTest = new Actorstest(AS){
   import system.dispatcher
   val name = "Stopped"
   val ref = ActorRef.noSender
   val clazz = "agentSystem.SSAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, None, running = false, Nil, Scala())
   val testAgents = Map( name -> agentInfo)
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StopAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = managerActor.wasAlreadyStoppedMsg(name)
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }


 def startingStoppedTest = new Actorstest(AS){
   import system.dispatcher
   val name = "StartSuccess"
   val ref = system.actorOf( SSAgent.props( emptyConfig, requestHandler, dbHandler), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, running = false, Nil)
   val testAgents = Map( name -> agentInfo)
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StartAgentCmd(name)
   val resF: Future[String ] = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct: String = managerActor.successfulStartMsg(name)
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }

 def stopingRunningTest = new Actorstest(AS){
   import system.dispatcher
   val name = "StopSuccess"
   val ref = system.actorOf( SSAgent.props( emptyConfig, requestHandler, dbHandler), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, running = true, Nil)
   val testAgents = Map( name -> agentInfo)
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StopAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = managerActor.successfulStopMsg(name)
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }
 
 def agentStopFailTest = new Actorstest(AS){
   import system.dispatcher
   val name = "Stopfail"
   val ref = system.actorOf( FFAgent.props( emptyConfig, requestHandler, dbHandler), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, running = true, Nil, Scala())
   val testAgents = Map( name -> agentInfo)
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StopAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = s"agentSystem.StopFailed: Test failure."
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }

 def agentStartFailTest = new Actorstest(AS){
   import system.dispatcher
   val name = "Startfail"
   val ref = system.actorOf( FFAgent.props( emptyConfig, requestHandler, dbHandler), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, running = false, Nil, Scala())
   val testAgents = Map( name -> agentInfo)
   val requestHandler = TestActorRef( new TestDummyRequestHandler() )
   val dbHandler =  TestActorRef( new TestDummyDBHandler() )
   val managerRef = TestActorRef( new TestManager(testAgents,dbHandler,requestHandler)) 
   val managerActor = managerRef.underlyingActor
   val msg = StartAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = s"agentSystem.StartFailed: Test failure."
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }*/

//}

