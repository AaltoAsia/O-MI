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

import scala.concurrent.{Future}
import scala.util.{Try, Success, Failure}
import akka.pattern.ask
import http.CLICmds._


trait InternalAgentManager extends BaseAgentSystem {
  import context.dispatcher

  /** Helper method for checking is agent even stored. If was handle will be processed.
    *
    */
  private def handleAgentCmd(agentName: String)(handle: AgentInfo => Future[String]): Future[String] = {
    val msg : Future[String] = agents.get(agentName) match {
      case None =>
      log.warning("Command for not stored agent!: " + agentName)
        Future.successful(s"Could not find agent: $agentName")
      case Some(agentInfo) =>
        handle(agentInfo)
    }
    sender() ! msg
    msg
  }

  protected def handleStart( start: StartAgentCmd ) = {
    val agentName = start.agent
    handleAgentCmd(agentName) { 
      agentInfo: AgentInfo =>
      if(agentInfo.running ){
        val msg = s"Agent $agentName was already Running. 're-start' should be used to restart running Agents."
        log.info(msg)
        Future.successful(msg)
      }else{
        log.info(s"Starting: " + agentInfo.name)
        val result = agentInfo.agent ? Start()
        result.map{
          case Success(CommandSuccessful()) =>
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
          case Failure( t: Throwable ) =>
            t.toString
        }.recover{
          case t : Throwable => 
          t.toString
        }

      }
    }
  }
  protected def handleStop( stop: StopAgentCmd ) = {
    val agentName = stop.agent
    handleAgentCmd(agentName){
      agentInfo: AgentInfo =>
      if (agentInfo.running) {
        log.warning(s"Stopping: " + agentInfo.name)
        val result = agentInfo.agent ? Stop()
        result.map{
          case Success(CommandSuccessful()) =>
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
          case Failure( t: Throwable ) =>
            t.toString
        }.recover{
          case t : Throwable => 
          t.toString
        }
      } else {
        val msg = s"Agent $agentName was already stopped."
        log.info(msg)
        Future.successful(msg)
      }
    }
  }

  protected def handleRestart( restart: ReStartAgentCmd ) = {
    val agentName = restart.agent
    handleAgentCmd(agentName) { 
      agentInfo: AgentInfo =>
    if( agentInfo.running) {
      log.info(s"Restarting: " + agentInfo.name)
      val result = agentInfo.agent ? Restart()
      result.map{
        case Success(CommandSuccessful()) =>
          val msg = s"Agent $agentName restarted succesfully."
          log.info(msg)
          msg
          case Failure( t: Throwable ) =>
            t.toString
      }.recover{
          case t : Throwable => 
          t.toString
        }

    }else {
      val msg = s"Agent $agentName was not running. 'start' should be used to start stopped Agents."
      log.info(msg)
        Future.successful(msg)
    }
  }
      }

  }
