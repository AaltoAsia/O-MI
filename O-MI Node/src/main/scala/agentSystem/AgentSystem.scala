
package agentSystem

import agentSystem._
import http.CLICmds._
import http._
import types.Path
import akka.actor.SupervisorStrategy._
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.Actor.Receive
import akka.actor.{
  Actor, 
  ActorRef, 
  ActorInitializationException, 
  ActorKilledException, 
  ActorLogging, 
  OneForOneStrategy, 
  Props, 
  SupervisorStrategy}
import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable.Map
import java.io.File
import java.net.URLClassLoader
import java.sql.Timestamp
import java.util.Date
import java.util.jar.JarFile
import http.CLICmds._
import database.DB
import scala.language.postfixOps

object AgentSystem {
  def props(dbobject: DB,subHandler: ActorRef): Props = Props(new AgentSystem(dbobject,subHandler))
}
class AgentSystem(val dbobject: DB, val subHandler: ActorRef) extends CoreAgentSystem 
                  with InternalAgentLoader
                  with InternalAgentManager
                  with ResponsibleAgentManager
object `package` {
  type AgentName = String
}

  sealed trait AAgent{
    def name:       AgentName
    def classname:  String
    def config:     String
  }
  case class AgentConfigEntry(
    val name:       AgentName,
    val classname:  String,
    val config:     String
  ) extends AAgent
  case class AgentInfo(
    name:       AgentName,
    classname:  String,
    config:     String,
    agent:      ActorRef,
    running:    Boolean
  ) extends AAgent

trait Receiving { 
  var receivers: Actor.Receive = Actor.emptyBehavior 
  def receiver(next: Actor.Receive) { receivers = receivers orElse next }
  final def receive = receivers
}
abstract class BaseAgentSystem extends Actor with ActorLogging with Receiving{
  /** Container for internal agents */
  protected[this] def agents: scala.collection.mutable.Map[AgentName, AgentInfo]
  protected[this] def settings: OmiConfigExtension 
  implicit val timeout = Timeout(5 seconds) 
}
class CoreAgentSystem extends BaseAgentSystem {
  /** Container for internal agents */
  protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = Map.empty
  protected[this] val settings = Settings(context.system)
}
