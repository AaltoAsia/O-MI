package agentSystem
import scala.util.Try
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }
import scala.collection.mutable.{Map => MutableMap}

import com.typesafe.config.Config
import akka.actor.{ActorRef, Actor, ActorSystem, Props , Terminated}
import akka.testkit.{TestActorRef}

import agentSystem._
import types.OmiTypes.{Responses, WriteRequest, ReadRequest, CallRequest, ResponseRequest}
import http.CLICmds._
import AgentEvents._

class FailurePropsAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
) extends ScalaInternalAgent{
  
  def config = ???
}
object FailurePropsAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
     throw new Exception("Test failure.")
  }
}

class FFAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
) extends ScalaInternalAgent {
  def config = ???
      throw StartFailed("Test failure.",None)
   final override def stop: InternalAgentResponse = { 
      throw StopFailed("Test failure.",None)
  }
}
class FSAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
) extends ScalaInternalAgent {
  def config = ???
      throw StartFailed("Test failure.",None)
}
class SFAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
) extends ScalaInternalAgent {
  def config = ???
   final override def stop: InternalAgentResponse = { 
      throw StopFailed("Test failure.",None)
  }
}
class SSAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
) extends ScalaInternalAgent {
  def config = ???
}
class WSAgent(_requestHandler: ActorRef, _dbHandler: ActorRef) extends SSAgent(_requestHandler, _dbHandler) with ResponsibleScalaInternalAgent{
   override def handleWrite( write: WriteRequest ) :Future[ResponseRequest]= {
     
     Future.successful{
      Responses.InternalError( new Exception("Test failure.") )
     }
  }
    override def handleCall(call: CallRequest ) :Future[ResponseRequest] = {
    Future.successful{
      Responses.InternalError( new Exception("Test failure.") )
    }
  }

}
object SSAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new SSAgent(requestHandler,dbHandler) )
  }
}
object FFAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new FFAgent(requestHandler,dbHandler) )
  }
}
object SFAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new SFAgent(requestHandler,dbHandler) )
  }
}
object FSAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new FSAgent(requestHandler,dbHandler) )
  }
}

class CompanionlessAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
) extends ScalaInternalAgent {
  def config = ???
}

object ClasslessCompanion extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new FFAgent(requestHandler,dbHandler) )
  }
}

class WrongInterfaceAgent {
  def config = ???
}
object WrongInterfaceAgent {

}

class NotPropsCreatorAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
)  extends ScalaInternalAgent {
  def config = ???
}
object NotPropsCreatorAgent {

}

class WrongPropsAgent(
  protected val requestHandler: ActorRef, 
  protected val dbHandler: ActorRef
)  extends ScalaInternalAgent {
  def config = ???
}
object WrongPropsAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new FFAgent(requestHandler,dbHandler) )
  }
}
class TestManager( testAgents: scala.collection.mutable.Map[AgentName, AgentInfo],
  protected val dbHandler: ActorRef,
  protected val requestHandler: ActorRef
  )(implicit system: ActorSystem) extends BaseAgentSystem with InternalAgentManager{
  protected val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = testAgents
  agents.values.foreach{
    case ai: AgentInfo => 
      ai.agent.foreach{
        ref =>
         if( ref != ActorRef.noSender ) context.watch( ref)
      }
  
  }
  protected def settings : AgentSystemConfigExtension = ???
  def receive : Actor.Receive = {
    case nC: NewCLI => sender() ! connectCLI(nC.ip,nC.cliRef )
    case  start: StartAgentCmd  => handleStart( start)
    case  stop: StopAgentCmd  => handleStop( stop)
    case ListAgentsCmd() => sender() ! agents.values.toVector
    case Terminated(agentRef: ActorRef) => 
      agentStopped(agentRef)
  }
  def getAgents = agents
}

 class AgentSystemSettings( val config : Config ) extends AgentSystemConfigExtension

 class TestDummyRequestHandler() extends Actor {
    def receive: Actor.Receive = {
      case AgentStopped(name) => 
      case na: NewAgent =>
    }
 }
 class TestDummyDBHandler() extends Actor {
    def receive: Actor.Receive = {
      case AgentStopped(name) => 
      case na: NewAgent =>
    }
 }
 class TestLoader( testConfig : AgentSystemConfigExtension,
  protected val dbHandler: ActorRef,
  protected val requestHandler: ActorRef
   ) extends BaseAgentSystem with InternalAgentLoader{
   protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = MutableMap.empty
   override protected[this] val settings = testConfig
   def receive : Actor.Receive = {
     case ListAgentsCmd() => sender() ! agents.values.toVector
     case Terminated(agentRef) => agentStopped(agentRef)
   }
 }
 object TestLoader{
   def props(
     testConfig: AgentSystemConfigExtension,
     dbHandler: ActorRef,
     requestHandler: ActorRef
   ): Props = Props({
    val loader = new TestLoader(testConfig,dbHandler,requestHandler)
    loader.start()
    loader
   })
 }
