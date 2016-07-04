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

import java.net.UnknownHostException
import java.util.Date

import akka.actor.{ActorSystem, ActorRef}
import akka.event.{LogSource, Logging, LoggingAdapter}
import database._
import responses.CallbackHandlers._
import responses.OmiGenerator._
import types.OmiTypes._

trait OmiRequestHandlerBase { 
  protected final def handleTTL( ttl: Duration) : FiniteDuration = if( ttl.isFinite ) {
        if(ttl.toSeconds != 0)
          FiniteDuration(ttl.toSeconds, SECONDS)
        else
          FiniteDuration(2,MINUTES)
      } else {
        FiniteDuration(Int.MaxValue,MILLISECONDS)
      }
  implicit def  dbConnection: DB

  protected def log: LoggingAdapter

  protected[this] def date = new Date()
}


trait OmiRequestHandlerCore { 
  protected def handle: PartialFunction[OmiRequest,Future[NodeSeq]] 

  implicit val logSource: LogSource[OmiRequestHandlerCore]= new LogSource[OmiRequestHandlerCore] {
      def genString(requestHandler:  OmiRequestHandlerCore) = requestHandler.toString
    }
  protected def log = Logging( http.Boot.system, this)

  def handleRequest(request: OmiRequest)(implicit system: ActorSystem): Future[NodeSeq] = {
    import system.dispatcher // execution context for futures

    request.callback match {

      case Some(callback) => {

        val callbackCheck = CallbackHandlers.checkCallbackUri(callback.uri)

        callbackCheck.flatMap {_ =>
          request match {
            case sub: SubscriptionRequest => runGeneration(sub)
            case _ => {
              // TODO: Can't cancel this callback
              runGeneration(request)  map { response =>
                  request.callback.map(_ send response)
              }
              Future.successful{
                xmlFromResults(
                  1.0,
                  Results.simple("200", Some("OK, callback job started"))
                )
              }
            }
          }
        } recover {
          case e: ProtocolNotSupported           => invalidCallback(e.getMessage)
          case e: ForbiddenLocalhostPort         => invalidCallback(e.getMessage)
          case e: java.net.MalformedURLException => invalidCallback(e.getMessage)
          case e: UnknownHostException           => invalidCallback("Unknown host: " + e.getMessage)
          case e: SecurityException              => invalidCallback("Unauthorized " + e.getMessage)
          case e: java.net.ProtocolException     => invalidCallback(e.getMessage)
          case t: Throwable                      => throw t
        }
      }
      case None => {
        request match {
          case _ => runGeneration(request)
        }
      }
    }
  }
  /**
   * Method for running response generation. Handles tiemout etc. upper level failures.
   *
   * @param request request is O-MI request to be handled
   */
  def runGeneration(request: OmiRequest)(implicit ec: ExecutionContext): Future[NodeSeq] = {
    handle(request).recoverWith{
      case e: TimeoutException => Future.successful(OmiGenerator.timeOutError(e.getMessage))
      case e: IllegalArgumentException => Future.successful(OmiGenerator.invalidRequest(e.getMessage))
      case e: Throwable =>
        log.error(e, "Internal Server Error: ")
        Future.successful(OmiGenerator.internalError(e))
    }
  }
  /**
   * Method to be called for handling internal server error, logging and stacktrace.
   *
   */
  def actionOnInternalError: Throwable => Unit = { error =>
    log.error(error, "Internal server error: ")
  }
}

class RequestHandler(
  val subscriptionManager: ActorRef,
  val agentSystem: ActorRef
)(implicit val dbConnection: DB
  ) extends OmiRequestHandlerCore
    with ReadHandler 
    with WriteHandler
    with ResponseHandler
    with SubscriptionHandler
    with PollHandler
    with CancelHandler
    with RESTHandler
    with RemoveHandler
  {

  protected def handle: PartialFunction[OmiRequest,Future[NodeSeq]] = {
    case sub     : SubscriptionRequest => handleSubscription(sub)
    case read    : ReadRequest         => handleRead(read)
    case write   : WriteRequest        => handleWrite(write)
    case cancel  : CancelRequest       => handleCancel(cancel)
    case poll    : PollRequest         => handlePoll(poll)
    case response: ResponseRequest     => handleResponse(response)
  }

}
