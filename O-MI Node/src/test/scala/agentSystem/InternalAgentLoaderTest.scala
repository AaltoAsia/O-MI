package agentSystem

import scala.collection.mutable.{Map => MutableMap }
import akka.actor.{Actor, ActorSystem, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import akka.pattern.ask
import akka.testkit._
import com.typesafe.config.{ConfigFactory, Config}
import http.CLICmds._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.specification.create.InterpolatedFragment
import org.specs2.mutable._
import testHelpers.Actorstest
import scala.concurrent.Future
import http.OmiConfigExtension

class InternalAgentLoaderTest(implicit ee: ExecutionEnv) extends Specification { 
  
  def logTestActorSystem =ActorSystem(
    "startstop",
    ConfigFactory.load(
      ConfigFactory.parseString(
        """
        akka.loggers = ["akka.testkit.TestEventListener"]
        """).withFallback(ConfigFactory.load()))
  )
  "InternalAgentLoader should " >> { 
    "log warnings when loading fails when " >> {
      "agent's class is not found" >> missingAgentTest
      "agent's companion object is not found" >> missingObjectTest
    }

    "log warnings when loaded classes are invalid when " >> {
      "agent's class does not implement trait InternalAgent" >> unimplementedIATest
      "agent's companion object does not implement trait PropsCreator" >> unimplementedPCTest
      "agent's companion object creates props for something else than agent">> wrongPropsTest
      "agent's companion object is actually something else" >> oddObjectTest 
    }
    "log warnings when loaded classes throw exceptions when " >> {
      "props are created " >> propsTest 
      "agent is started  " >> startTest
    }

    "store successfully started agents to agents " >> successfulAgents 
  }
 class TestLoader( testConfig : AgentSystemConfigExtension) extends BaseAgentSystem with InternalAgentLoader{
   protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = MutableMap.empty
   override protected[this] val settings = testConfig
   def receive : Actor.Receive = {
     case ListAgentsCmd() => sender() ! agents.values.toVector
   }
 }
 object TestLoader{
   def props( testConfig: AgentSystemConfigExtension) : Props = Props({
    val loader = new TestLoader(testConfig)
    loader.start()
    loader
   })
 }
 class AgentSystemSettings( val config : Config ) extends AgentSystemConfigExtension

 def missingAgentTest      = new Actorstest(logTestActorSystem){
   val classname = "unexisting"
   val exception = new java.lang.ClassNotFoundException(classname)
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
      "Missing" ={
        class = "$classname"
        config = {}
      }
    }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Classloading failed. Could not load: $classname. Received $exception"
   )
   logWarningTest(new AgentSystemSettings(config), warnings )
 }
 def missingObjectTest     = new Actorstest(logTestActorSystem){
   val classname = "agentSystem.CompanionlessAgent"
   val exception = new java.lang.ClassNotFoundException(classname+"$")
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "Missing" ={
         class = "$classname"
         config = {}
       }
     }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Classloading failed. Could not load: $classname. Received $exception"
   )
   logWarningTest(new AgentSystemSettings(config), warnings )
 }
 def unimplementedIATest   = new Actorstest(logTestActorSystem){
   val classname = "agentSystem.WrongInterfaceAgent"
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "UnimplementedIA" ={
         class = "$classname"
         config = {}
       }
     }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Class $classname does not implement InternalAgent trait."
   )
   logWarningTest(new AgentSystemSettings(config), warnings )
 }
 def unimplementedPCTest   = new Actorstest(logTestActorSystem){
   val classname = "agentSystem.NotPropsCreatorAgent"
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "UnimplementedPC" ={
         class = "$classname"
         config = {}
       }
     }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Object $classname does not implement PropsCreator trait."
   )
   logWarningTest(new AgentSystemSettings(config), warnings )
 }
 def wrongPropsTest       = new Actorstest(logTestActorSystem){
   val classname = "agentSystem.WrongPropsAgent"
   val created = "agentSystem.FFAgent"
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "WrongProps" ={
         class = "$classname"
         config = {}
       }
     }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Object $classname creates InternalAgentProps for class $created, but should create for class $classname."
   )
   logWarningTest(new AgentSystemSettings(config), warnings )
  after
 }

 def oddObjectTest        = new Actorstest(logTestActorSystem){
  after
 }
 def propsTest            = new Actorstest(logTestActorSystem){
   val exception : Throwable = CommandFailed("Test failure.") 
   val classname = "agentSystem.FailurePropsAgent"
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "FailureProps" ={
         class = "$classname"
         config = {}
       }
     }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Class $classname could not be loaded or created. Received $exception"
  )
   logWarningTest(new AgentSystemSettings(config), warnings )
 }
 
 def startTest            = new Actorstest(logTestActorSystem/*ActorSystem()*/){
   val exception : Throwable = CommandFailed("Test failure.") 
   val classname = "agentSystem.FFAgent"
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "FailureAgent" ={
         class = "$classname"
         config = {}
       }
     }
   }
   """
   val config = ConfigFactory.parseString(configStr)
   val warnings = Vector(
     s"Class $classname could not be started. Received $exception"
    )
   logWarningTest(new AgentSystemSettings(config), warnings )
 }
 def successfulAgents     = new Actorstest(ActorSystem()){
   val emptyConfig = ConfigFactory.empty()
   val classname = "agentSystem.SSAgent"
   val classname2 = "unexisting"
   val classname3 = "agentSystem.SFAgent"
   val matchAll = ActorRef.noSender
   val correctAgents = Vector(
     AgentInfo("A1", classname, emptyConfig, matchAll , true, Seq.empty),
     AgentInfo("A2", classname, emptyConfig, matchAll , true, Seq.empty),
     AgentInfo("A3", classname, emptyConfig, matchAll , true, Seq.empty),
     //4 and 6, should fail without causing problem
     AgentInfo("A5", classname3, emptyConfig, matchAll , true, Seq.empty),
     AgentInfo("A7", classname3, emptyConfig, matchAll , true, Seq.empty)
   )
   val configStr =
   s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents {
       "A1" ={
         class = "$classname"
         config = {}
       }
       "A2" ={
         class = "$classname"
         config = {}
       }
       "A3" ={
         class = "$classname"
         config = {}
       }
       "A4" ={
         class = "$classname2"
         config = {}
       }
       "A5" ={
         class = "$classname3"
         config = {}
       }
       "A6" ={
         class = "$classname2"
         config = {}
       }
       "A7" ={
         class = "$classname3"
         config = {}
       }
     }
   }
   """
   val config =new AgentSystemSettings( ConfigFactory.parseString(configStr) )
   val timeout = config.internalAgentsStartTimout
   val loader = system.actorOf(TestLoader.props(config), "agent-loader") 
   val res = (loader ? ListAgentsCmd())(timeout).mapTo[Vector[AgentInfo]].map{ 
     vec : Vector[AgentInfo] =>
      vec.map{
       agentInfo => agentInfo.copy( agent = matchAll )
     }
   }
   res must contain{
     t: AgentInfo =>
     correctAgents must contain(t) 
   }.await
 }

 def logWarningTest(
   config: AgentSystemConfigExtension,
   warnings : Vector[String]
 )(
  implicit _system: ActorSystem 
  ) ={
    val filters = warnings.map{ msg => EventFilter.warning(message = msg, occurrences = 1)}
    filterEvents(filters){
      val loader = _system.actorOf(TestLoader.props(config), "agent-loader") 
    }
 }
}
