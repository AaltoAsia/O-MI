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
import agentSystem.AgentTypes._
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
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }

import com.typesafe.config.Config
import scala.util.{Try}
import scala.concurrent.duration._
  /**
    Commands that can be received from InternalAgentLoader.
  **/
sealed trait InternalAgentCmd
  case class Start()                    extends InternalAgentCmd
  case class Restart()                  extends InternalAgentCmd
  case class Stop()                     extends InternalAgentCmd
  case class Configure(config: String)  extends InternalAgentCmd
sealed trait InternalAgentResponse{
  def msg : String
}
trait InternalAgentSuccess                  extends InternalAgentResponse 
  case class CommandSuccessful(msg : String ) extends InternalAgentSuccess 
trait InternalAgentFailure                  extends InternalAgentResponse
  case class CommandFailed(msg : String ) extends InternalAgentFailure 
sealed trait ResponsibleAgentMsg
  case class ResponsibleWrite( promise: Promise[ResponsibleAgentResponse], write: WriteRequest)
sealed trait ResponsibleAgentResponse
  case class SuccessfulWrite( paths: Iterable[Path] ) extends ResponsibleAgentResponse 
object AgentTypes {
  trait InternalAgent extends Actor with ActorLogging {
    protected def parent = context.parent
    protected def name = self.path.name
    protected def start   : InternalAgentResponse 
    protected def restart : InternalAgentResponse
    protected def stop    : InternalAgentResponse
    protected def configure(config: String)  : InternalAgentResponse
    private[AgentTypes] def forcer : Actor.Receive = {
      case Start()                    => sender() ! start
      case Restart()                  => sender() ! restart
      case Stop()                     => sender() ! stop
      case Configure(config: String)  => sender() ! configure(config)
    }
    protected def receiver : Actor.Receive 
    final def receive : Actor.Receive= forcer orElse receiver
  }

  trait ResponsibleInternalAgent extends InternalAgent {
    protected def handleWrite(promise: Promise[ResponsibleAgentResponse], write: WriteRequest ) :Unit
    override private[AgentTypes] def forcer: Actor.Receive = super.forcer orElse {
      case ResponsibleWrite( promise: Promise[ResponsibleAgentResponse], write: WriteRequest)  =>  handleWrite(promise, write)
    }

    final protected def passWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
      val result = PromiseResult()
      parent ! PromiseWrite( result, write)
      promise.completeWith( result.isSuccessful ) 
    }
    final protected def incorrectWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
      promise.failure(new Exception(s"Write incorrect. Tryed to write incorrect value."))
    }
    final protected def forbiddenWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
      promise.failure(new Exception(s"Write forbidden. Tryed to write to path that is not mean to be writen."))
    }
  }
  trait PropsCreator{
    def props( config: Config ) : Props
  }
}
