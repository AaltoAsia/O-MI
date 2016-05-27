
package agentSystem

import database.DB
import agentSystem.AgentTypes._
import agentSystem._
import http.CLICmds._
import http._
import types.Path
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
  SupervisorStrategy
}
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
import com.typesafe.config.Config
import scala.language.postfixOps

object AgentSystem {
  def props(dbobject: DB,subHandler: ActorRef): Props = Props(
  {val as = new AgentSystem(dbobject,subHandler)
  as.start()
  as})
}
class AgentSystem(val dbobject: DB, val subHandler: ActorRef) extends CoreAgentSystem 
                  with InternalAgentLoader
                  with InternalAgentManager
                  with ResponsibleAgentManager
object `package` {
  type AgentName = String
}

  sealed trait AgentInfoBase{
    def name:       AgentName
    def classname:  String
    def config:     Config
    def ownedPaths: Seq[Path]
  }
  case class AgentConfigEntry(
    name:       AgentName,
    classname:  String,
    config:     Config,
    ownedPaths: Seq[Path]
  ) extends AgentInfoBase
  case class AgentInfo(
    name:       AgentName,
    classname:  String,
    config:     Config,
    agent:      ActorRef,
    running:    Boolean,
    ownedPaths: Seq[Path]
  ) extends AgentInfoBase 

trait Receiving { 
  var receivers: Actor.Receive = Actor.emptyBehavior 
  def receiver(next: Actor.Receive) : Unit = { receivers = receivers orElse next }
  final def receive : Actor.Receive = receivers
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
  protected[this] val settings = http.Boot.settings
}
