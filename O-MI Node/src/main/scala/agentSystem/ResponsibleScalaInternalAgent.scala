/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2016 Aalto University.                                        +
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

import scala.reflect.ClassTag
import scala.util.{Success, Failure, Try}
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }
import akka.actor.{
  Actor,
  ActorLogging,
  Props,
  ActorInitializationException
}
import akka.actor.Actor.Receive
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.Config
import types.OdfTypes._
import types.OmiTypes._
import types.Path
case class ResponsibilityRequest( senderName: String, request: OmiRequest)
trait ResponsibleScalaInternalAgent extends ScalaInternalAgent with ResponsibleInternalAgent{
  import context.dispatcher
  protected def handleWrite( write: WriteRequest ) :Unit

  override def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart
    case Stop() => sender() ! stop
    case write: WriteRequest => handleWrite(write)
   }
  final protected def passWrite(write: WriteRequest) : Unit = {

    val senderRef = sender()
    val future = writeToNode(write) 
    future.onComplete{
      case Success( response: ResponseRequest ) => senderRef ! response
      case Failure( t ) => senderRef ! Responses.InternalError(t)
    }

  }
  final protected def incorrectWrite(write: WriteRequest) : Unit = {
    sender() ! Responses.InternalError(new Exception(s"Write incorrect. Tryed to write incorrect value."))
  }
  final protected def forbiddenWrite(write: WriteRequest) : Unit = {
    sender() ! Responses.InternalError(new Exception(s"Write forbidden. Tryed to write to path that is not mean to be writen."))
  }
}

