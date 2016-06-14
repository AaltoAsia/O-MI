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
import scala.concurrent.{Await, ExecutionContext, Future, TimeoutException}
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.{NodeSeq, XML}
//import akka.http.StatusCode

import java.net.UnknownHostException
import java.util.Date

import akka.actor.ActorRef
import akka.event.{LogSource, Logging, LoggingAdapter}
import database._
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import responses.CallbackHandlers._
import responses.OmiGenerator._
import types.OdfTypes._
import types.OmiTypes._
import types._

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
  def handleRequest(request: OmiRequest)(implicit ec: ExecutionContext): Future[NodeSeq] = {
    request.callback match {
      case Some(address) => {
        val callbackCheck = CallbackHandlers.checkCallback(address)
        callbackCheck.flatMap { uri =>
          request match {
            case sub: SubscriptionRequest => runGeneration(sub)
            case _ => {
              // TODO: Can't cancel this callback
              runGeneration(request)  map { xml =>
                  sendCallback(
                    address,
                    xml,
                    request.ttl
                  )
                 xmlFromResults(
                  1.0,
                  Results.simple("200", Some("OK, callback job started")))
              }
           }
          }
        } recover {
          case e: ProtocolNotSupported => invalidCallback(e.getMessage)
          case e: ForbiddenLocalhostPort => invalidCallback(e.getMessage)
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
   * @param request request is O-MI request to be handled
   */
  def actionOnInternalError: Throwable => Unit = { error =>
    //println("[ERROR] Internal Server error:")
    //error.printStackTrace()
    log.error(error, "Internal server error: ")
  }
}
class RequestHandler(
  val subscriptionManager: ActorRef,
  val agentSystem: ActorRef
)(implicit val dbConnection: DB) extends  OmiRequestHandlerCore
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
    case subscription: SubscriptionRequest => handleSubscription(subscription)
    case read: ReadRequest => handleRead(read)
    case write: WriteRequest => handleWrite(write)
    case cancel: CancelRequest => handleCancel(cancel)
    case poll: PollRequest => handlePoll(poll)
    case response: ResponseRequest => handleResponse(response)
  }

}
