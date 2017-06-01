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
import scala.collection.mutable.{Map => MutableMap}
import akka.actor.{ActorRef, Props, Terminated}
import scala.util.{Try, Success, Failure}
import akka.pattern.ask
import http.CLICmds._
import AgentResponsibilities._
import AgentEvents._


trait InternalAgentManager extends BaseAgentSystem with InternalAgentLoader{
  import context.dispatcher

  protected def dbHandler: ActorRef
  protected def requestHandler: ActorRef
  def notTerminatedYet( name : AgentName ) : String = s"Agent $name is not terminated yet. Can not be started"
  def successfulCmdMsg( name : AgentName, cmd: String ) : String = s"Agent $name $cmd succesfully."
  def successfulStartMsg( name : AgentName) : String = successfulCmdMsg( name, "started" )
  def successfulStopMsg( name : AgentName ) : String = successfulCmdMsg( name, "stopped" )
  def wasAlreadyCmdMsg( name : AgentName, cmd: String ) : String = s"Agent $name was already $cmd."
  def wasAlreadyStartedMsg( name : AgentName) : String = wasAlreadyCmdMsg( name, "started" )
  def wasAlreadyStoppedMsg( name : AgentName) : String = wasAlreadyCmdMsg( name, "stopped" )
  def commandForNonexistingMsg( name : AgentName ) : String = s"Command for nonexistent agent: $name."
  def couldNotFindMsg( name : AgentName ) : String = s"Could not find agent: $name."

  protected def connectCLI( ip: String, cliRef: ActorRef ) ={
    connectedCLIs += ip -> cliRef
    true
  }
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
    //log.info( sender().path.name )
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
      } else {
        log.info(s"Starting: " + agentInfo.name)
        val msg = successfulStartMsg(agentName)
        loadAndStart( agentInfo.toConfigEntry )
        log.info(msg)

        Future.successful(msg)
      }
    }
  }
  protected def handleStop( stop: StopAgentCmd ) = {
    val agentName = stop.agent
    handleAgentCmd(agentName){
      agentInfo: AgentInfo =>
      if (agentInfo.running || agentInfo.agent.nonEmpty) {
        stopAgent( agentName )
        val msg = successfulStopMsg(agentName)
        Future.successful(msg)
      } else {
        val msg = wasAlreadyStoppedMsg(agentName)
        log.info(msg)
        Future.successful(msg)
      }
    }
  }

  }
