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

import akka.pattern.ask
import http.CLICmds._


trait InternalAgentManager extends BaseAgentSystem {
  //import context.dispatcher

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

  protected def handleStart( start: StartAgentCmd ) = {
    val agentName = start.agent
    sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
    agentInfo.running match{
      case false=>
      log.info(s"Starting: " + agentInfo.name)
      val result = agentInfo.agent ? Start()
      val msg = s"Agent $agentName started succesfully."
      log.info(msg)
      agents += agentInfo.name -> AgentInfo(
        agentInfo.name,
        agentInfo.classname,
        agentInfo.config,
        agentInfo.agent,
        true,
        agentInfo.ownedPaths
      )
    msg
    case true =>
    val msg = s"Agent $agentName was already Running. 're-start' should be used to restart running Agents"
    log.info(msg)
    msg
  }
}
}
protected def handleStop( stop: StopAgentCmd ) = {
  val agentName = stop.agent
  sender() ! handleAgentCmd(agentName) { agentInfo: AgentInfo =>
  if (agentInfo.running) {
    log.warning(s"Stopping: " + agentInfo.name)
    val result = agentInfo.agent ? Stop()
    agents += agentInfo.name -> AgentInfo(
      agentInfo.name,
      agentInfo.classname,
      agentInfo.config,
      agentInfo.agent,
      false,
      agentInfo.ownedPaths
    )
  val msg = s"Agent $agentName stopped succesfully."
  log.info(msg)
  msg
} else {
  val msg = s"Agent $agentName was already stopped."
  log.info(msg)
  msg
}
  }
}

protected def handleRestart( restart: ReStartAgentCmd ) = {
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
