package agentSystem
import scala.util.Try
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }

import com.typesafe.config.Config
import akka.actor.{ActorRef, Actor, ActorSystem, Props }

import agentSystem._
import types.OmiTypes.WriteRequest
import http.CLICmds._


trait StartFailure{
   final def start: InternalAgentResponse  = {  
      StartFailed("Test failure.",None)
  }
}
trait StopFailure{
   final def stop: InternalAgentResponse = { 
      StopFailed("Test failure.",None)
  }
}
trait StartSuccess{
   final def start: InternalAgentResponse ={  
    CommandSuccessful()
  }
}
trait StopSuccess{
   final def stop: InternalAgentResponse  = { 
    CommandSuccessful()
  }
}
class FailurePropsAgent extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
object FailurePropsAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
     throw new Exception("Test failure.")
  }
}

class FFAgent extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
class FSAgent extends ScalaInternalAgent with StartFailure with StopSuccess{
  def config = ???
}
class SFAgent extends ScalaInternalAgent with StartSuccess with StopFailure{
  def config = ???
}
class SSAgent extends ScalaInternalAgent with StartSuccess with StopSuccess{
  def config = ???
}
class WSAgent extends SSAgent with ResponsibleScalaInternalAgent{
   def handleWrite(write: WriteRequest ) :Unit = {
     sender() ! FailedWrite( Vector.empty, Vector( new Exception("Test failure.")))
  }

}
object SSAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    Props( new SSAgent )
  }
}
object FFAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    Props( new FFAgent )
  }
}
object SFAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    Props( new SFAgent )
  }
}
object FSAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    Props( new FSAgent )
  }
}

class CompanionlessAgent extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}

object ClasslessCompanion extends PropsCreator {
  final def props(config: Config) : Props = { 
    Props( new FFAgent )
  }
}

class WrongInterfaceAgent {
  def config = ???
}
object WrongInterfaceAgent {

}

class NotPropsCreatorAgent  extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
object NotPropsCreatorAgent {

}

class WrongPropsAgent  extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
object WrongPropsAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    Props( new FFAgent )
  }
}
class TestManager( testAgents: scala.collection.mutable.Map[AgentName, AgentInfo])
extends BaseAgentSystem with InternalAgentManager{
  protected val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = testAgents
  protected def settings : AgentSystemConfigExtension = ???
  def receive : Actor.Receive = {
    case  start: StartAgentCmd  => handleStart( start)
    case  stop: StopAgentCmd  => handleStop( stop)
    case  restart: ReStartAgentCmd  => handleRestart( restart )
    case ListAgentsCmd() => sender() ! agents.values.toVector
  }
  def getAgents = agents
}

 class AgentSystemSettings( val config : Config ) extends AgentSystemConfigExtension
