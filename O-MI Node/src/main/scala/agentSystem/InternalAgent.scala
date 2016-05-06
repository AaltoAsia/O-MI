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
import akka.actor.{
  Actor,
  ActorLogging,
  Props,
  ActorInitializationException
}

  /**
    Commands that can be received from InternalAgentLoader.
  **/
sealed trait InternalAgentCmd
  case class Start()                    extends InternalAgentCmd
  case class Restart()                  extends InternalAgentCmd
  case class Stop()                     extends InternalAgentCmd
  case class Quit()                     extends InternalAgentCmd
  case class Configure(config: String)  extends InternalAgentCmd
sealed trait InternalAgentResponse{
  def msg : String
}
  trait InternalAgentSuccess                  extends InternalAgentResponse 
  case class CommandSuccessful(msg : String ) extends InternalAgentSuccess 
  trait InternalAgentFailure                  extends InternalAgentResponse
  case class CommandFailed(msg : String ) extends InternalAgentFailure 
sealed trait ResponsibilityCmd
  case class Write()                    extends ResponsibilityCmd
  case class Read()                     extends ResponsibilityCmd
  case class RegisterPath()             extends ResponsibilityCmd

sealed trait AbstractInternalAgent extends Actor with ActorLogging{
  protected def start   : InternalAgentResponse 
  protected def restart : InternalAgentResponse
  protected def stop    : InternalAgentResponse
  protected def quit    : InternalAgentResponse
  protected def configure(config: String)  : InternalAgentResponse
  final protected def baseReceive : Receive = {
    case Start()                    => sender() ! start
    case Restart()                  => sender() ! restart
    case Stop()                     => sender() ! stop
    case Quit()                     => sender() ! quit
    case Configure(config: String)  => sender() ! configure(config)
  }
  protected def responsibleReceive : Receive 
  protected def extendedReceive : Receive 
  override final def receive = extendedReceive orElse responsibleReceive orElse baseReceive
}

trait InternalAgent extends AbstractInternalAgent {
  protected override final def responsibleReceive : Receive  = Actor.emptyBehavior
}

trait ResponsibleInternalAgent extends AbstractInternalAgent {
  protected def write : InternalAgentResponse 
  protected def read  : InternalAgentResponse
  protected def registerPath  : InternalAgentResponse
  protected override final def responsibleReceive : Receive = {
    case Write()        =>  sender() ! write 
    case Read()         =>  sender() ! read
    case RegisterPath() =>  sender() ! registerPath    
  }
}

