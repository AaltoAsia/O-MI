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
/**
 Commands that can be received from InternalAgentLoader.
 **/
sealed trait InternalAgentCmd
case class Start()                    extends InternalAgentCmd
case class Restart()                  extends InternalAgentCmd
case class Stop()                     extends InternalAgentCmd

trait InternalAgentResponse
trait InternalAgentSuccess     extends InternalAgentResponse 
case class CommandSuccessful() extends InternalAgentSuccess 

class InternalAgentFailure(msg : String, exp : Option[Throwable] )  extends  Exception(msg, exp.getOrElse(null)) with InternalAgentResponse
class CommandFailed(msg : String, exp : Option[Throwable] ) extends InternalAgentFailure(msg, exp) 
case class StopFailed(msg : String, exp : Option[Throwable] ) extends CommandFailed(msg, exp) 
case class StartFailed(msg : String, exp : Option[Throwable] ) extends CommandFailed(msg, exp) 

sealed trait ResponsibleAgentMsg
case class ResponsibleWrite( promise: Promise[ResponseRequest], write: WriteRequest)


trait ScalaInternalAgent extends InternalAgent with ActorLogging{
  def config : Config
  def agentSystem = context.parent
  final def name = self.path.name
  @deprecated("Use Actor's preRestart and postRestart methods instead.","o-mi-node-0.8.0") 
  def restart : InternalAgentResponse = {
    stop 
    start
  }
  //These need to be implemented 
  @deprecated("Use Actor's preStart method instead.","o-mi-node-0.8.0") 
  def start   : InternalAgentResponse 
  @deprecated("Use Actor's postStop method instead.","o-mi-node-0.8.0") 
  def stop    : InternalAgentResponse 
  def receive  = {
    case Start() => sender() ! start 
    case Restart() => sender() ! restart
    case Stop() => sender() ! stop
   }
  final def writeToNode(write: WriteRequest) : Future[ResponseRequest] = {
    // timeout for the write request, which means how long this agent waits for write results
    implicit val timeout : Timeout = Timeout(write.handleTTL)

    // Execute the request, execution is asynchronous (will not block)
    (agentSystem ? ResponsibilityRequest(name, write)).mapTo[ResponseRequest]
  }
  override def preStart = start
  override def postStop = stop
}
