package agentSystem
import scala.util.Try
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }

import com.typesafe.config.Config
import akka.actor.{ActorRef, Actor, ActorSystem, Props }

import agentSystem._
import types.OmiTypes.WriteRequest
import http.CLICmds._


trait StartFailure{
   final def start: InternalAgentSuccess  = {  
    throw  CommandFailed("Test failure.")
  }
}
trait StopFailure{
   final def stop: InternalAgentSuccess = { 
    throw  CommandFailed("Test failure.")
  }
}
trait StartSuccess{
   final def start: InternalAgentSuccess ={  
    CommandSuccessful()
  }
}
trait StopSuccess{
   final def stop: InternalAgentSuccess  = { 
    CommandSuccessful()
  }
}
class FailurePropsAgent extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
object FailurePropsAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    throw  CommandFailed("Test failure.")
  }
}

class FFAgent extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
class FSAgent extends ScalaInternalAgent with StartFailure with StopSuccess{
  def config = throw  CommandFailed("Test failure.")
}
class SFAgent extends ScalaInternalAgent with StartSuccess with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
class SSAgent extends ScalaInternalAgent with StartSuccess with StopSuccess{
  def config = throw  CommandFailed("Test failure.")
}
class WSAgent extends SSAgent with ResponsibleInternalAgent{
   def handleWrite(write: WriteRequest ) :Unit = {
    passWrite( write)
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
  def config = throw  CommandFailed("Test failure.")
}

object ClasslessCompanion extends PropsCreator {
  final def props(config: Config) : Props = { 
    Props( new FFAgent )
  }
}

class WrongInterfaceAgent {
  def config = throw  CommandFailed("Test failure.")
}
object WrongInterfaceAgent {

}

class NotPropsCreatorAgent  extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
object NotPropsCreatorAgent {

}

class WrongPropsAgent  extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
object WrongPropsAgent extends PropsCreator{
  final def props(config: Config) : Props = { 
    Props( new FFAgent )
  }
}
class TestManager( testAgents: scala.collection.mutable.Map[AgentName, AgentInfo])
extends BaseAgentSystem with InternalAgentManager{
  protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = testAgents
  protected[this] val settings : AgentSystemConfigExtension = http.Boot.settings
  def receive : Actor.Receive = {
    case  start: StartAgentCmd  => handleStart( start)
    case  stop: StopAgentCmd  => handleStop( stop)
    case  restart: ReStartAgentCmd  => handleRestart( restart )
    case ListAgentsCmd() => sender() ! agents.values.toVector
  }
  def getAgents = agents
}

