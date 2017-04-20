package agentSystem
import scala.util.Try
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }

import com.typesafe.config.Config
import akka.actor.{ActorRef, Actor, ActorSystem, Props }

import agentSystem._
import types.OmiTypes.{Responses, WriteRequest, ReadRequest, CallRequest, ResponseRequest}
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
class FailurePropsAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef) extends ScalaInternalAgent with StartFailure with StopFailure{
  
  def config = ???
}
object FailurePropsAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
     throw new Exception("Test failure.")
  }
}

class FFAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef) extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
class FSAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef) extends ScalaInternalAgent with StartFailure with StopSuccess{
  def config = ???
}
class SFAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef) extends ScalaInternalAgent with StartSuccess with StopFailure{
  def config = ???
}
class SSAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef) extends ScalaInternalAgent with StartSuccess with StopSuccess{
  def config = ???
}
class WSAgent(_requestHandler: ActorRef, _dbHandler: ActorRef) extends SSAgent(_requestHandler, _dbHandler) with ResponsibleScalaInternalAgent{
   override def handleWrite( write: WriteRequest ) :Future[ResponseRequest]= {
     
     Future.successful{
      Responses.InternalError( new Exception("Test failure.") )
     }
  }
    override def handleRead(read: ReadRequest ) :Future[ResponseRequest] = {
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

class CompanionlessAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef) extends ScalaInternalAgent with StartFailure with StopFailure{
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

class NotPropsCreatorAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef)  extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
object NotPropsCreatorAgent {

}

class WrongPropsAgent(protected val requestHandler: ActorRef, protected val dbHandler: ActorRef)  extends ScalaInternalAgent with StartFailure with StopFailure{
  def config = ???
}
object WrongPropsAgent extends PropsCreator{
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef) : Props = { 
    Props( new FFAgent(requestHandler,dbHandler) )
  }
}
class TestManager( testAgents: scala.collection.mutable.Map[AgentName, AgentInfo])
extends BaseAgentSystem with InternalAgentManager{
  protected val dbHandler: ActorRef = ActorRef.noSender
  protected val requestHandler: ActorRef = ActorRef.noSender
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
