/**
  Copyright (c) 2016 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  **/
package agentSystem

import agentSystem._
import http.CLICmds._
import http._
import types.Path
import akka.actor.SupervisorStrategy._
import akka.pattern.ask
import akka.util.Timeout
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
import http.CLICmds._


trait InternalAgentManager extends BaseAgentSystem {
  import context.dispatcher

  /** Helper method for checking is agent even stored. If was handle will be processed.
    *
    */
  private def handleAgentCmd(agentName: String)(handle: AgentInfo => String): String = {
    agents.get(agentName) match {
      case None =>
      log.warning("Command for not stored agent!: " + agentName)
      "Could not find agent: " + agentName
      case Some(agentInfo) =>
      handle(agentInfo)
    }
  }

  /**
    * Method for handling received messages.
    * Should handle:
    *   -- ConfigUpdate with updating running AgentActors.
    *   -- Terminated with trying to restart AgentActor.
    */
  receiver {
    case  start: StartAgentCmd  => handleStart( start)
    case  stop: StopAgentCmd  => handleStop( stop)
    case  restart: ReStartAgentCmd  => handleRestart( restart )
    case ListAgentsCmd() => sender() ! agents.values.toSeq
  } 
  def handleStart( start: StartAgentCmd ) = {
     val agentName = start.agent
    sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
    agentInfo.running match{
      case false=>
      log.info(s"Starting: " + agentInfo.name)
      val result = agentInfo.agent ? Start()
      val msg = s"Agent $agentName started succesfully."
      log.info(msg)
      agents += agentInfo.name -> AgentInfo(agentInfo.name,agentInfo.classname, agentInfo.config, agentInfo.agent, true)
      msg
      case true =>
      val msg = s"Agent $agentName was already Running. 're-start' should be used to restart running Agents"
      log.info(msg)
      msg
    }
  }
}
def handleStop( stop: StopAgentCmd ) = {
  val agentName = stop.agent
  sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
  agentInfo.running match{
    case true =>
    log.warning(s"Stopping: " + agentInfo.name)
    val result = agentInfo.agent ? Stop()
    agents += agentInfo.name -> AgentInfo(agentInfo.name,agentInfo.classname, agentInfo.config, agentInfo.agent, false)
    val msg = s"Agent $agentName stopped succesfully."
    log.info(msg)
    msg
    case true =>
    val msg = s"Agent $agentName was already stopped."
    log.info(msg)
    msg
  }
}
}

def handleRestart( restart: ReStartAgentCmd ) = {
  val agentName = restart.agent
  sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
  agentInfo.running match{
    case true=>
    log.info(s"Restarting: " + agentInfo.name)
    val result = agentInfo.agent ? Restart()
    val msg = s"Agent $agentName restarted succesfully."
    log.info(msg)
    msg
    case false =>
    val msg = s"Agent $agentName was not running. 'start' should be used to start stopped Agents."
    log.info(msg)
    msg
  }
}
    }

  }
