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
import scala.concurrent.Promise
import scala.reflect.ClassTag
import scala.util.Try

import akka.actor.{Actor, ActorLogging, Props}
import com.typesafe.config.Config
import types.OmiTypes._
import types.Path
  /**
    Commands that can be received from InternalAgentLoader.
  **/
sealed trait InternalAgentCmd
  case class Start()                    extends InternalAgentCmd
  case class Restart()                  extends InternalAgentCmd
  case class Stop()                     extends InternalAgentCmd
sealed trait InternalAgentResponse
trait InternalAgentSuccess     extends InternalAgentResponse 
  case class CommandSuccessful() extends InternalAgentSuccess 
abstract class InternalAgentFailure(msg : String )                 extends  Exception(msg) with InternalAgentResponse
  case class CommandFailed(msg : String ) extends InternalAgentFailure(msg) 
sealed trait ResponsibleAgentMsg
  case class ResponsibleWrite( promise: Promise[ResponsibleAgentResponse], write: WriteRequest)
sealed trait ResponsibleAgentResponse
  case class SuccessfulWrite( paths: Iterable[Path] ) extends ResponsibleAgentResponse 
object AgentTypes {
  trait InternalAgent extends Actor with ActorLogging {
    protected def config : Config
    protected def parent = context.parent
    protected def name = self.path.name
    protected def start   : Try[InternalAgentSuccess ]
    protected def restart : Try[InternalAgentSuccess ] = stop flatMap{
      case success  : InternalAgentSuccess => start
    }
    protected def stop    : Try[InternalAgentSuccess ]
    private[AgentTypes] def forcer : Actor.Receive = {
      case Start()                    => sender() ! start
      case Restart()                  => sender() ! restart
      case Stop()                     => sender() ! stop
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
    def props( config: Config ) : InternalAgentProps
  }
  final case class InternalAgentProps private[InternalAgentProps](props: Props)
  object InternalAgentProps{
    final def apply[T <: InternalAgent: ClassTag](creator: =>T): InternalAgentProps ={
      new InternalAgentProps( Props( creator ))
    }
    final def apply[T <: InternalAgent]()(implicit arg0: ClassTag[T]): InternalAgentProps ={
      new InternalAgentProps( Props()(arg0))
    }
    final def apply[T <: InternalAgent](clazz: Class[T], args: Any*): InternalAgentProps ={
      new InternalAgentProps( Props( clazz, args) )
    } 
  }
}
