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

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import akka.pattern.ask
import http.CLICmds._


trait InternalAgentManager extends BaseAgentSystem {
  import context.dispatcher

  def successfulCmdMsg( name : AgentName, cmd: String ) : String = s"Agent $name $cmd succesfully."
  def successfulStartMsg( name : AgentName) : String = successfulCmdMsg( name, "started" )
  def successfulStopMsg( name : AgentName ) : String = successfulCmdMsg( name, "stopped" )
  def wasAlreadyCmdMsg( name : AgentName, cmd: String ) : String = s"Agent $name was already $cmd."
  def wasAlreadyStartedMsg( name : AgentName) : String = wasAlreadyCmdMsg( name, "started" )
  def wasAlreadyStoppedMsg( name : AgentName) : String = wasAlreadyCmdMsg( name, "stopped" )
  def commandForNonexistingMsg( name : AgentName ) : String = s"Command for nonexistent agent: $name."
  def couldNotFindMsg( name : AgentName ) : String = s"Could not find agent: $name."

  /** Helper method for checking is agent even stored. If was handle will be processed.
    *
    */
  private def handleAgentCmd(agentName: String)(handle: AgentInfo => Future[String]): Future[String] = {
    val msg : Future[String] = agents.get(agentName) match {
      case None =>
      log.warning(commandForNonexistingMsg(agentName))
        Future.successful(couldNotFindMsg(agentName))
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
        val msg = wasAlreadyStartedMsg(agentName)
        log.info(msg)
        Future.successful(msg)
      }else{
        log.info(s"Starting: " + agentInfo.name)
        val result = agentInfo.agent ? Start()
        result.map{
          case CommandSuccessful() =>
            val msg = successfulStartMsg(agentName)
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
          case failure : InternalAgentFailure => 
            failure.toString
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
          case CommandSuccessful() =>
            agents += agentInfo.name -> AgentInfo(
              agentInfo.name,
              agentInfo.classname,
              agentInfo.config,
              agentInfo.agent,
              false,
              agentInfo.ownedPaths
            )
            val msg = successfulStopMsg(agentName)
            log.info(msg)
            msg
          case failure : InternalAgentFailure => 
            failure.toString
        }.recover{
          case t : Throwable => 
          t.toString
        }
      } else {
        val msg = wasAlreadyStoppedMsg(agentName)
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
          val msg = s"Agent $agentName restarted succesfullyi."
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
