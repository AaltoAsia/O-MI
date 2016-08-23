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



class InternalAgentManagerTest(implicit ee: ExecutionEnv) extends Specification {

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
 def AS = ActorSystem() 
 def emptyConfig = ConfigFactory.empty()
 def timeoutDuration= 10.seconds
 implicit val timeout = Timeout( timeoutDuration )
 def cmdToNonExistent = new Actorstest(AS){
   import system.dispatcher
   val name = "Nonexisting"
   val testAgents : Map[AgentName, AgentInfo] = Map.empty
   val managerRef = TestActorRef( new TestManager(testAgents)) 
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
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, true, Nil)
   val testAgents = Map( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
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
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, false, Nil)
   val testAgents = Map( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
   val msg = StopAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = managerActor.wasAlreadyStoppedMsg(name)
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }


 def startingStoppedTest = new Actorstest(AS){
   import system.dispatcher
   val name = "StartSuccess"
   val ref = system.actorOf( Props( new SSAgent), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, false, Nil)
   val testAgents = Map( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
   val msg = StartAgentCmd(name)
   val resF: Future[String ] = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct: String = managerActor.successfulStartMsg(name)
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }

 def stopingRunningTest = new Actorstest(AS){
   import system.dispatcher
   val name = "StopSuccess"
   val ref = system.actorOf( Props( new SSAgent), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, true, Nil)
   val testAgents = Map( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
   val msg = StopAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = managerActor.successfulStopMsg(name)
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }
 
 def agentStopFailTest = new Actorstest(AS){
   import system.dispatcher
   val name = "Stopfail"
   val ref = system.actorOf( Props( new FFAgent), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, true, Nil)
   val testAgents = Map( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
   val msg = StopAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = s"agentSystem.StopFailed: Test failure."
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }

 def agentStartFailTest = new Actorstest(AS){
   import system.dispatcher
   val name = "Startfail"
   val ref = system.actorOf( Props( new FFAgent), name)
   val clazz = "agentSystem.FFAgent"
   val agentInfo = AgentInfo( name, clazz, emptyConfig, ref, false, Nil)
   val testAgents = Map( name -> agentInfo)
   val managerRef = TestActorRef( new TestManager(testAgents)) 
   val managerActor = managerRef.underlyingActor
   val msg = StartAgentCmd(name)
   val resF = (managerRef ? msg).mapTo[Future[String]].flatMap(f => f)
   val correct = s"agentSystem.StartFailed: Test failure."
   resF should beEqualTo( correct ).await( 0, timeoutDuration)
 }

}

