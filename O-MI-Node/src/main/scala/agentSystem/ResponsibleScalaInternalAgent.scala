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

import akka.actor.ActorRef
import types.OmiTypes._

import scala.concurrent.Future

abstract class ResponsibleScalaInternalAgentTemplate(
                                                      requestHandler: ActorRef,
                                                      dbHandler: ActorRef
                                                    ) extends ScalaInternalAgentTemplate(requestHandler, dbHandler) with
  ResponsibleScalaInternalAgent

trait ResponsibleScalaInternalAgent
  extends ScalaInternalAgent
    with ResponsibleInternalAgent {

  import context.dispatcher

  protected def handleWrite(write: WriteRequest): Future[ResponseRequest] = writeToDB(write)
  protected def handleDelete(delete: DeleteRequest): Future[ResponseRequest] = requestFromDB(delete)

  protected def handleRead( read: ReadRequest ) : Future[ResponseRequest] = readFromDB(read)
  protected def handleCall(call: CallRequest): Future[ResponseRequest] = {
    Future {
      Responses.NotImplemented()
    }
  }

  override def receive: PartialFunction[Any, Unit] = {
    case write: WriteRequest => respondFuture(handleWrite(write))
    case delete: DeleteRequest => respondFuture(handleDelete(delete))
    case read: ReadRequest => respondFuture(handleRead(read))
    case call: CallRequest => respondFuture(handleCall(call))
  }

}

