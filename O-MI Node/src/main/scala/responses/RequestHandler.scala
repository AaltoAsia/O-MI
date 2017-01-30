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


package responses

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, TimeoutException}
import scala.xml.NodeSeq

import akka.actor.{ActorSystem, ActorRef}
import akka.event.{LogSource, Logging, LoggingAdapter}
import analytics.AnalyticsStore
import database._
import types.OmiTypes._
import http.{ActorSystemContext, Actors, OmiConfigExtension }
import http.ContextConversion._
import scala.language.implicitConversions
import CallbackHandler._

trait OmiRequestHandlerBase { 
  def handle: PartialFunction[OmiRequest,Future[ResponseRequest]] 
  protected def log : LoggingAdapter
  protected implicit def system : ActorSystem
  protected implicit def settings: OmiConfigExtension
}


trait OmiRequestHandlerCore extends OmiRequestHandlerBase{ 
  implicit val logSource: LogSource[OmiRequestHandlerCore]= new LogSource[OmiRequestHandlerCore] {
      def genString(requestHandler:  OmiRequestHandlerCore) = requestHandler.toString
    }

  protected val log: LoggingAdapter = Logging( system, this)

}

class RequestHandler(
  protected implicit val system :ActorSystem, 
  protected val agentSystem : ActorRef,
  protected val subscriptionManager : ActorRef,
  protected implicit val settings: OmiConfigExtension,
  protected implicit val dbConnection: DB,
  protected implicit val singleStores: SingleStores,
  protected val analyticsStore: Option[ActorRef]
)
extends  OmiRequestHandlerCore
with ReadHandler 
with WriteHandler
with ResponseHandler
with SubscriptionHandler
with PollHandler
with CancelHandler
{
  final def handle: PartialFunction[OmiRequest,Future[ResponseRequest]] = {
    case subscription: SubscriptionRequest => handleSubscription(subscription)
    case read: ReadRequest => handleRead(read)
    case write: WriteRequest => handleWrite(write)
    case cancel: CancelRequest => handleCancel(cancel)
    case poll: PollRequest => handlePoll(poll)
    case response: ResponseRequest => handleResponse(response)
  }
}
