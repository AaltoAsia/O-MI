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
  ActorRef,
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
abstract class ResponsibleScalaInternalAgentTemplate(
  requestHandler: ActorRef,
  dbHandler: ActorRef
) extends ScalaInternalAgentTemplate( requestHandler, dbHandler ) with ResponsibleScalaInternalAgent

trait ResponsibleScalaInternalAgent
 extends ScalaInternalAgent
  with ResponsibleInternalAgent{
  import context.dispatcher
  protected def handleWrite( write: WriteRequest ) : Future[ResponseRequest] = writeToDB(write)
  //protected def handleRead( read: ReadRequest ) : Future[ResponseRequest] = readFromDB(read)
  protected def handleCall( call: CallRequest ) : Future[ResponseRequest] = {
    Future{
      Responses.NotImplemented()
    }
  }

  override def receive  = {
    case write: WriteRequest => respondFuture(handleWrite(write))
    //case read: ReadRequest => respondFuture(handleRead(read))
    case call: CallRequest => respondFuture(handleCall(call))
  }

}

