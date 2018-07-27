package agentSystem

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit._
import com.typesafe.config.ConfigFactory
import http.CLICmds._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import testHelpers.{Actorstest,NoisyActorstest}

import scala.concurrent.duration._

class InternalAgentLoaderTest(implicit ee: ExecutionEnv) extends Specification {

  "InternalAgentLoader should " >> {
    "log warnings when loading fails when " >> {
      "agent's class is not found" >> missingAgentTest
      "agent's companion object is not found" >> missingObjectTest
    }

    "log warnings when loaded classes are invalid when " >> {
      "agent's class does not implement trait InternalAgent" >> unimplementedIATest
      "agent's companion object does not implement trait PropsCreator" >> unimplementedPCTest
      "agent's companion object creates props for something else than agent" >> wrongPropsTest
    }
    "log warnings when loaded classes throw exceptions when " >> {
      "props are created " >> propsTest
      "agent is started  " >> startTest
    }

    "store successfully started agents to agents " >> successfulAgents
  }

  def missingAgentTest = new NoisyActorstest() {
    val classname = "unexisting"
    val exception = new java.lang.ClassNotFoundException(classname)
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents = [{
      name = "Missing" 
      language = "scala"
      class = "$classname"
     }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Classloading failed. Could not load: $classname. Received $exception"
    )
    logWarningTest(new AgentSystemSettings(config), warnings)
  }

  def missingObjectTest = new NoisyActorstest() {
    val classname = "agentSystem.CompanionlessAgent"
    val exception = new java.lang.ClassNotFoundException(classname + "$")
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents =[{
         name = "Missing" 
         language = "scala"
         class = "$classname"
       }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Classloading failed. Could not load: $classname. Received $exception"
    )
    val asce = new AgentSystemSettings(config)
    logWarningTest(asce, warnings)
  }

  def unimplementedIATest = new NoisyActorstest() {
    val classname = "agentSystem.WrongInterfaceAgent"
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents =[{
      name = "UnimplementedIA"
      language = "scala"
      class = "$classname"
     }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Class $classname does not implement InternalAgent trait."
    )
    val asce = new AgentSystemSettings(config)
    logWarningTest(asce, warnings)
  }

  def unimplementedPCTest = new NoisyActorstest() {
    val classname = "agentSystem.NotPropsCreatorAgent"
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     starting-timeout = 2 seconds
     internal-agents =[{
      name = "UnimplementedIA"
      language = "scala"
      class = "$classname"
     }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Object $classname does not implement PropsCreator trait."
    )
    logWarningTest(new AgentSystemSettings(config), warnings)
  }

  def wrongPropsTest = new NoisyActorstest() {
    val classname = "agentSystem.WrongPropsAgent"
    val created = "agentSystem.FFAgent"
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents =[{
       name= "WrongProps"
       language = "scala"
       class = "$classname"
     }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Object $classname creates InternalAgentProps for class $created, but should create for class $classname."
    )
    logWarningTest(new AgentSystemSettings(config), warnings)
    after
  }

  def oddObjectTest = new NoisyActorstest() {
    after
  }

  def propsTest = new NoisyActorstest() {
    val exception: Throwable = new Exception("Test failure.")
    val classname = "agentSystem.FailurePropsAgent"
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents =[{
       name= "FailureProps"
       language = "scala"
       class = "$classname"
     }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Class $classname could not be loaded or created. Received $exception"
    )
    logWarningTest(new AgentSystemSettings(config), warnings)
  }

  def startTest = new NoisyActorstest(/*ActorSystem()*/) {
    val exception: Throwable = StartFailed("Test failure.", None)
    val classname = "agentSystem.FFAgent"
    val configStr =
      s"""
   agent-system{
     starting-timeout = 2 seconds
     internal-agents =[{
       name= "FailureAgent"
       language = "scala"
       class = "$classname"
     }]
   }
   """
    val config = ConfigFactory.parseString(configStr)
    val warnings = Vector(
      s"Agent FailureAgent encountered exception during creation."
    )
    logWarningTest(new AgentSystemSettings(config), warnings)
  }

  def successfulAgents = new Actorstest() {
    val emptyConfig = ConfigFactory.empty()
    val classname = "agentSystem.SSAgent"
    val classname2 = "unexisting"
    val classname3 = "agentSystem.SFAgent"
    val matchAll = Some(ActorRef.noSender)
    val confs = Vector(
      s"""{
         name = "A1"
         class = "$classname"
         language = "scala"
       }""" ,
       s"""{
         name = "A2"
         class = "$classname"
         language = "scala"
       }""" ,
       s"""{
         name = "A3"
         class = "$classname"
         language = "scala"
       }""" ,
       s"""{
         name = "A4"
         class = "$classname2"
         language = "scala"
       }""" ,
       s"""{
         name = "A5"
         class = "$classname3"
         language = "scala"
       }""" ,
       s"""{
         name = "A6"
         class = "$classname2"
         language = "scala"
       }""" ,
       s"""{
         name = "A7" 
         class = "$classname3"
         language = "scala"
       }
       """
     )
    val correctAgents = Vector(
      AgentInfo("A1", classname, ConfigFactory.parseString(confs(0)), matchAll, running = true, Seq.empty, Scala()),
      AgentInfo("A2", classname, ConfigFactory.parseString(confs(2)), matchAll, running = true, Seq.empty, Scala()),
      AgentInfo("A3", classname, ConfigFactory.parseString(confs(3)), matchAll, running = true, Seq.empty, Scala()),
      //4 and 6, should fail without causing problem
      AgentInfo("A5", classname3, ConfigFactory.parseString(confs(5)), matchAll, running = true, Seq.empty, Scala()),
      AgentInfo("A7", classname3, ConfigFactory.parseString(confs(6)), matchAll, running = true, Seq.empty, Scala())
    )
    val configStr =
      s"""
   agent-system{
     starting-timeout = 5 seconds
     internal-agents =[
     ${confs.mkString(",\n")}
     ] 
   }
   """
    val config = new AgentSystemSettings(ConfigFactory.parseString(configStr))
    val timeout = config.internalAgentsStartTimeout
    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val loader = system.actorOf(TestLoader.props(config, requestHandler, dbHandler), "agent-loader")
    val res = (loader ? ListAgentsCmd()) (timeout).mapTo[Vector[AgentInfo]].map {
      vec: Vector[AgentInfo] =>
        vec.map {
          agentInfo => agentInfo.copy(agent = matchAll)
        }
    }
    res.failed.foreach { case er: Throwable => system.log.error(er, "ListAgentsCmd() future failed") }

    res must contain {
      t: AgentInfo =>
        correctAgents must contain(t)
    }.await(retries = 2, timeout = 5 seconds)
  }

  def logWarningTest(
                      config: AgentSystemConfigExtension,
                      warnings: Vector[String]
                    )(
                      implicit _system: ActorSystem
                    ) = {

    val requestHandler = TestActorRef(new TestDummyRequestHandler())
    val dbHandler = TestActorRef(new TestDummyDBHandler())
    val filters = warnings.map { msg => EventFilter.warning(message = msg, occurrences = 1) }
    filterEvents(filters) {
      _system.actorOf(TestLoader.props(config, dbHandler, requestHandler), "agent-loader")
    }
  }
}
