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
import com.typesafe.config.Config
import database.DB
import http.CLICmds._
import http.OmiNodeContext
import types.OmiTypes.WriteRequest
import types.Path
import http.{ActorSystemContext, Actors, Settings, Storages, OmiNodeContext, Callbacking}

object AgentSystem {
  def props()(
  implicit nc: ActorSystemContext with Actors with Callbacking with Settings with Storages
      ): Props = Props(
  {val as = new AgentSystem()
  as.start()
  as})
}
class AgentSystem(
  implicit val nc: ActorSystemContext with Actors with Callbacking with Settings with Storages
  ) extends BaseAgentSystem 
  with InternalAgentLoader
  with InternalAgentManager
  with ResponsibleAgentManager
  with DBPusher{
  import nc._
  protected[this] def settings: AgentSystemConfigExtension  = nc.settings
  protected[this] val agents: scala.collection.mutable.Map[AgentName, AgentInfo] = Map.empty
  //protected[this] val settings = http.Boot.settings
  def receive : Actor.Receive = {
    case  start: StartAgentCmd  => handleStart( start)
    case  stop: StopAgentCmd  => handleStop( stop)
    case  restart: ReStartAgentCmd  => handleRestart( restart )
    case ListAgentsCmd() => sender() ! agents.values.toVector
    case PromiseWrite(result: PromiseResult, write: WriteRequest) => handleWrite(result,write)  
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

abstract class BaseAgentSystem extends Actor with ActorLogging{
  /** Container for internal agents */
  protected[this] def agents: scala.collection.mutable.Map[AgentName, AgentInfo]
  protected[this] def settings: AgentSystemConfigExtension 
  implicit val timeout = Timeout(5 seconds) 
  implicit val nc: ActorSystemContext with Actors with Callbacking with Settings with Storages

  import nc._
}
