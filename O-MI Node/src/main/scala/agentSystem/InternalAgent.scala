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
import types.OdfTypes._
import types.OmiTypes._
import types.Path
import akka.pattern.ask
import akka.actor.{
  Actor,
  ActorLogging,
  Props,
  ActorInitializationException
}
import scala.concurrent.{ Future,ExecutionContext, TimeoutException }

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
  case class Write(infos:OdfInfoItem*)                    extends ResponsibilityCmd
  case class Read()                     extends ResponsibilityCmd

sealed trait ResponsibleAgentResponse
trait ResponsibleAgentSuccess                  extends ResponsibleAgentResponse 
  //Used when agent to not need to write to paths that aren't owned by agent processing write
  case class SuccessfulWrite(paths:Seq[Path]) extends ResponsibleAgentSuccess
  //Used when agent needs to write to paths that aren't owned by itself
  case class FutureResult(future: Future[ResponsibilityResponse] ) extends ResponsibleAgentSuccess
trait ResponsibleAgentFailure                  extends ResponsibleAgentResponse 
  case class FailedWrite(paths:Seq[Path]) extends ResponsibleAgentFailure

sealed trait AbstractInternalAgent extends Actor with ActorLogging with Receiving{
  protected def start   : InternalAgentResponse 
  protected def restart : InternalAgentResponse
  protected def stop    : InternalAgentResponse
  protected def quit    : InternalAgentResponse
  protected def configure(config: String)  : InternalAgentResponse
  receiver{
    case Start()                    => sender() ! start
    case Restart()                  => sender() ! restart
    case Stop()                     => sender() ! stop
    case Quit()                     => sender() ! quit
    case Configure(config: String)  => sender() ! configure(config)
  }
}

trait InternalAgent extends AbstractInternalAgent {
}

trait ResponsibleInternalAgent extends AbstractInternalAgent {
  protected def write(infos:OdfInfoItem*) : InternalAgentResponse 
  protected def read  : InternalAgentResponse
  receiver {
    case Write(infos)  =>  sender() ! write(infos)
    case Read()         =>  sender() ! read
  }
}

