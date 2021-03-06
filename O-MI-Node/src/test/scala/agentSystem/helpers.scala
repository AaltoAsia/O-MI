package agentSystem

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent.Future
import akka.actor.{Actor, ActorRef, Props, Terminated}
import com.typesafe.config.Config

import agentSystem.AgentEvents._
import http.CLICmds._
import types.omi._

trait UnhandledReceive extends Actor{
  def receive = {
    case any: Any => unhandled(any)
  }
}
class FailurePropsAgent(
                         protected val requestHandler: ActorRef,
                         protected val dbHandler: ActorRef
                       ) extends ScalaInternalAgent with UnhandledReceive {

  def config = ???
}

object FailurePropsAgent extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    throw new Exception("Test failure.")
  }
}

class FFAgent(
               protected val requestHandler: ActorRef,
               protected val dbHandler: ActorRef
             ) extends ScalaInternalAgent with UnhandledReceive {
  def config = ???

  throw new Exception("Test failure.")

  override def postStop = {
    throw new Exception("Test failure.")
  }
}

class FSAgent(
               protected val requestHandler: ActorRef,
               protected val dbHandler: ActorRef
             ) extends ScalaInternalAgent  with UnhandledReceive {
  def config = ???

  throw new Exception("Test failure.")
}

class SFAgent(
               protected val requestHandler: ActorRef,
               protected val dbHandler: ActorRef
             ) extends ScalaInternalAgent  with UnhandledReceive {
  def config = ???

  override def postStop = {
    throw new Exception("Test failure.")
  }
}

class SSAgent(
               protected val requestHandler: ActorRef,
               protected val dbHandler: ActorRef
             ) extends ScalaInternalAgent  with UnhandledReceive {
  def config = ???
}

class WSAgent(_requestHandler: ActorRef, _dbHandler: ActorRef) extends SSAgent(_requestHandler, _dbHandler) with
  ResponsibleScalaInternalAgent {
  override def handleWrite(write: WriteRequest): Future[ResponseRequest] = {

    Future.successful {
      Responses.InternalError(new Exception("Test failure."))
    }
  }

  override def handleCall(call: CallRequest): Future[ResponseRequest] = {
    Future.successful {
      Responses.InternalError(new Exception("Test failure."))
    }
  }

}

object SSAgent extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    Props(new SSAgent(requestHandler, dbHandler))
  }
}

object FFAgent extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    Props(new FFAgent(requestHandler, dbHandler))
  }
}

object SFAgent extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    Props(new SFAgent(requestHandler, dbHandler))
  }
}

object FSAgent extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    Props(new FSAgent(requestHandler, dbHandler))
  }
}

class CompanionlessAgent(
                          protected val requestHandler: ActorRef,
                          protected val dbHandler: ActorRef
                        ) extends ScalaInternalAgent with UnhandledReceive {
  def config = ???
}

object ClasslessCompanion extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    Props(new FFAgent(requestHandler, dbHandler))
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
                          ) extends ScalaInternalAgent with UnhandledReceive {
  def config = ???
}

object NotPropsCreatorAgent {

}

class WrongPropsAgent(
                       protected val requestHandler: ActorRef,
                       protected val dbHandler: ActorRef
                     ) extends ScalaInternalAgent {
  def config = ???
  def receive = {
    case any: Any => unhandled(any)
  }
}

object WrongPropsAgent extends PropsCreator {
  final def props(config: Config, requestHandler: ActorRef, dbHandler: ActorRef): Props = {
    Props(new FFAgent(requestHandler, dbHandler))
  }
}

class TestManager(testAgents: scala.collection.mutable.Map[AgentName, AgentInfo],
                  protected val dbHandler: ActorRef,
                  protected val requestHandler: ActorRef
                 ) extends BaseAgentSystem with InternalAgentManager {
  protected val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = testAgents
  agents.values.foreach {
    ai: AgentInfo =>
      ai.agent.foreach {
        ref =>
          if (ref != ActorRef.noSender) context.watch(ref)
      }

  }

  protected def settings: AgentSystemConfigExtension = ???

  def receive: Actor.Receive = {
    case nC: NewCLI => sender() ! connectCLI(nC.ip, nC.cliRef)
    case start: StartAgentCmd => handleStart(start)
    case stop: StopAgentCmd => handleStop(stop)
    case ListAgentsCmd() => sender() ! agents.values.toVector
    case Terminated(agentRef: ActorRef) =>
      agentStopped(agentRef)
  }

  def getAgents: MutableMap[AgentName, AgentInfo] = agents
}

class AgentSystemSettings(val config: Config) extends AgentSystemConfigExtension

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

class TestLoader(
  testConfig: AgentSystemConfigExtension,
  protected val dbHandler: ActorRef,
  protected val requestHandler: ActorRef
) extends BaseAgentSystem with InternalAgentLoader {
  protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = MutableMap.empty
  override protected[this] val settings: AgentSystemConfigExtension = testConfig

  def receive: Actor.Receive = {
    case ListAgentsCmd() => sender() ! agents.values.toVector
    case Terminated(agentRef) => agentStopped(agentRef)
  }
}

object TestLoader {
  def props(
             testConfig: AgentSystemConfigExtension,
             dbHandler: ActorRef,
             requestHandler: ActorRef
           ): Props = Props({
    val loader = new TestLoader(testConfig, dbHandler, requestHandler)
    loader.start()
    loader
  })
}
