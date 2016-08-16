/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package agentSystem

import scala.collection.mutable.Map
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy._
import com.typesafe.config.Config
import database.DB
import http.CLICmds._
import types.OmiTypes.WriteRequest
import types.Path

object AgentSystem {
  def props(dbobject: DB ,subHandler: ActorRef): Props = Props(
  {val as = new AgentSystem(dbobject,subHandler)
  as.start()
  as})
}
class AgentSystem(val dbobject: DB, val subHandler: ActorRef)
  extends BaseAgentSystem 
  with InternalAgentLoader
  with InternalAgentManager
  with ResponsibleAgentManager
  with DBPusher{
  protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = Map.empty
  protected[this] val settings = http.Boot.settings
  def receive : Actor.Receive = {
    case  start: StartAgentCmd  => handleStart( start)
    case  stop: StopAgentCmd  => handleStop( stop)
    case  restart: ReStartAgentCmd  => handleRestart( restart )
    case ListAgentsCmd() => sender() ! agents.values.toVector
    case ResponsibilityRequest(senderName, write: WriteRequest ) => handleWrite(senderName, write)  
  }  
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
    ownedPaths: Seq[Path],
    language:   Option[Language]
  ) extends AgentInfoBase
  case class AgentInfo(
    name:       AgentName,
    classname:  String,
    config:     Config,
    agent:      ActorRef,
    running:    Boolean,
    ownedPaths: Seq[Path]
  ) extends AgentInfoBase 


  sealed trait Language{}
  final case class Unknown(val lang : String ) extends Language
  final case class Scala() extends Language
  final case class Java() extends Language

object Language{
  def apply( str: String ) = str.toLowerCase() match {
    case "java" => Java()
    case "scala" => Scala()
    case str: String => Unknown(str)
  }
}

abstract class BaseAgentSystem extends Actor with ActorLogging{
  /** Container for internal agents */
  protected[this] def agents: scala.collection.mutable.Map[AgentName, AgentInfo]
  protected[this] def settings: AgentSystemConfigExtension 
  implicit val timeout = Timeout(5 seconds) 
  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case fail: StartFailed => 
        SupervisorStrategy.Stop
      case fail: InternalAgentFailure => 
        log.warning( "InternalAgent failure: " + sender().path.name )
        SupervisorStrategy.Restart
      case t =>
        log.warning( "Child: " + sender().path.name )
        super.supervisorStrategy.decider.applyOrElse(t, (_: Any) => Escalate)
    }
}
