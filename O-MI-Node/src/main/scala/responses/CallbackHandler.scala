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

import java.net.{InetAddress, URLEncoder}
import java.sql.Timestamp
import java.util.Date

import scala.collection.mutable.{Map => MutableMap}//, Queue => MutableQueue}
import scala.collection.immutable.{Map, HashMap, Queue}
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
//import scala.xml.NodeSeq

import akka.util.{ByteString, Timeout}
import akka.actor.{ActorSystem, Terminated, Actor, ActorLogging, ActorRef, Timers, Props, PoisonPill}
import akka.event.LogSource
import akka.pattern.{ask}
import org.slf4j.LoggerFactory
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream._
import akka.stream.scaladsl._

import responses.CallbackHandler._
import types.omi._
import database.SingleStores

import http.MetricsReporter.CallbackSent
import http.{WebSocketUtil, OmiConfigExtension}

object CallbackHandler {

  case class BufferedRequest(request: HttpRequest, deadline: Deadline, callback: HTTPCallback)

  case class CurrentConnection(identifier: Int, handler: SendHandler)

  val supportedProtocols = Vector("http", "https")
  type SendHandler = ResponseRequest => Future[Unit]

  // Base error
  sealed class CallbackFailure(msg: String) extends Exception(msg)
  sealed trait HttpCallbackFailure{ val call: BufferedRequest }

  // Errors
  case class HttpError(status: StatusCode, call: BufferedRequest) extends
    CallbackFailure(s"Received HTTP status $status from ${call.request.uri}.")
    with HttpCallbackFailure

  case class HttpConnectionError(error: Throwable, call: BufferedRequest) extends
    CallbackFailure(error.toString)
    with HttpCallbackFailure

  case class ProtocolNotSupported(protocol: String, callback: Callback) extends
    CallbackFailure(s"$protocol is not supported, use one of " + supportedProtocols.mkString(", "))

  case class ForbiddenLocalhostPort(callback: Callback) extends
    CallbackFailure(s"Callback address is forbidden.")

  case class MissingConnection(callback: WebSocketCallback) extends
    CallbackFailure(s"CurrentConnection not found for ${
      callback match {
        case CurrentConnectionCallback(identifier) => identifier
        case WSCallback(uri) => uri
      }
    }")

}

trait HttpSendLogic {
  this: Actor with ActorLogging =>

  protected val http: HttpExt
  implicit val system = context.system

  def send(next: BufferedRequest)(implicit ec: ExecutionContext) = {

    val start = System.currentTimeMillis()
    
    val failures: Try[HttpResponse] => Try[HttpResponse] = {
      case Failure(error: Throwable) =>
        log.info(s"Callback failed to ${next.request.uri}: $error")
        context.actorSelection("/user/metric-reporter") ! CallbackSent(false, System.currentTimeMillis()-start, next.callback.uri)
        Failure(HttpConnectionError(error, next))
      case s: Success[HttpResponse] =>
        context.actorSelection("/user/metric-reporter") ! CallbackSent(true, System.currentTimeMillis()-start, next.callback.uri)
        s
    }

    val response = http singleRequest next.request
    response transform failures flatMap check(next) onComplete {self ! _}
  }

  def check(next: BufferedRequest): HttpResponse => Future[BufferedRequest] = { response =>
    if (response.status.isSuccess) {
      //TODO: Handle content of response, possibly piggybacking
      log.debug(
        s"Successfully sent POST request to ${next.request.uri} with ${response.status}"
      )
      response.discardEntityBytes()
      Future successful next
    } else {
      response.discardEntityBytes()
      log.info(s"Callback http response status: ${response.status} at ${next.request.uri}")
      Future failed HttpError(response.status, next)
    }
  }

}


object HttpQueue {
  case object TrySend
  case class EmptyPleaseKillMe(uri: Uri)
  sealed trait TimerCommand
  case class StartInterval(interval: FiniteDuration) extends TimerCommand
  case object StopInterval extends TimerCommand
  private case object TimerKey
}

class HttpQueue(
  first: BufferedRequest,
  protected val http: HttpExt
) extends Actor with ActorLogging with Timers with HttpSendLogic {
  import HttpQueue._
  import context.dispatcher

  // TODO: size limit for the queue
  def receive = queued(Queue(first), 1)
  val uri = first.request.uri

  def caseTimerCommand: Receive = {
    case StartInterval(interval) =>
      log.debug(s"StartInterval: $uri")
      timers.startTimerAtFixedRate(TimerKey, TrySend, interval)
    case StopInterval => timers.cancel(TimerKey)
  }


  // To stop receiving TrySend commands
  def busy(successQueue: Queue[BufferedRequest], failureQueue: Queue[BufferedRequest], retries: Int): Receive = caseTimerCommand orElse {
    case newRequest: BufferedRequest =>
      context become busy(successQueue enqueue newRequest, failureQueue enqueue newRequest, retries)
    case Success(_) =>
      log.debug(s"TrySend: Success; $uri")
      context become queued(successQueue, 0)
      self ! TrySend

    case Failure(_) =>
      context become queued(failureQueue, retries+1)
    case TrySend => // noop
  }

  def queued(httpQueue: Queue[BufferedRequest], retries: Int): Receive = caseTimerCommand orElse {
    case newRequest: BufferedRequest =>
      context become queued(httpQueue enqueue newRequest, retries)

    case TrySend => 
      httpQueue.dequeueOption match {
        case Some((next, newQueue)) if next.deadline.isOverdue =>
          log.debug(s"TrySend: ttl overdue; $uri")
          context become queued(newQueue, retries)
          self ! TrySend

        case Some((next, newQueue)) =>
          log.debug(s"TrySend: trying next; $uri")
          context become busy(newQueue, httpQueue, retries)
          send(next)

        case None => 
          log.debug(s"TrySend: Empty; $uri")
          context become dying
          sender() ! EmptyPleaseKillMe(uri)
      }
  }
  def dying: Receive = {
    case r: BufferedRequest => sender() ! r  // someone else process this
    case TrySend => // noop
  }

}

class HttpBufferRouter(
  val settings: OmiConfigExtension,
  protected val http: HttpExt
) extends Actor with ActorLogging with HttpSendLogic {
  import HttpQueue._
  import context.dispatcher

  def receive = queued(HashMap.empty)
  def queued(httpQueues: Map[Uri, ActorRef]): Receive = {
    case newRequest: BufferedRequest => // does not contain
      httpQueues get newRequest.request.uri match {
        case Some(queue) => queue ! newRequest
        case None =>
          send(newRequest)
      }
      sender() ! (())
    case Success(_: BufferedRequest) => // Initial request succeeded: noop
    case Failure(e: HttpCallbackFailure) => // Initial request failed, queue
      val newRequest = e.call
      val child = context.actorOf(Props(classOf[HttpQueue], newRequest, http))

      context become queued( httpQueues + (newRequest.request.uri -> child))

      child ! StartInterval(settings.callbackDelay)

    case Failure(e) => log.warning(s"Unknown send error: $e")

    case EmptyPleaseKillMe(uri) =>
      context become queued(httpQueues - uri)
      sender() ! PoisonPill
  }
}


/**
  * Handles sending data to callback addresses
  */
class CallbackHandler(
                       protected val settings: OmiConfigExtension,
                       protected val singleStores: SingleStores,
                     )(
                       protected implicit val system: ActorSystem,
                     ) extends WebSocketUtil {

  import system.dispatcher

  protected val httpExtension: HttpExt = Http(system)
  val portsUsedByNode: Seq[Int] = settings.ports.values.toSeq
  val whenTerminated: Future[Terminated] = system.whenTerminated

  val httpRouter = system.actorOf(Props(classOf[HttpBufferRouter], settings, httpExtension))

  protected def currentTimestamp = new Timestamp(new Date().getTime)

  implicit val logSource: LogSource[CallbackHandler] = (requestHandler: CallbackHandler) => requestHandler.toString

  //protected val log: LoggingAdapter = Logging(system, this)
  protected val log = LoggerFactory.getLogger(classOf[CallbackHandler])

  val webSocketConnections: MutableMap[String, SendHandler] = MutableMap.empty
  val currentConnections: MutableMap[Int, CurrentConnection] = MutableMap.empty

  private[this] def sendHttp(callback: HTTPCallback,
                             request: OmiRequest,
                             ttl: Duration): Future[Unit] = {
    val tryUntil = (ttl match {
      case ttl: FiniteDuration => ttl
      case _ => settings.callbackTimeout
    }).fromNow


    val address = callback.uri
    val encodedSource = (request match {
        case response: ResponseRequest =>
          Source.futureSource(
            response.withRequestInfoFrom(singleStores).map{r =>
              r.asXMLSource
            })
        case r: OmiRequest => r.asXMLSource

      }).map{str: String => URLEncoder.encode(str,"UTF-8")}

    val formSource = Source.single("msg=").concat(encodedSource)
      .map{str: String => ByteString(str,"UTF-8")}
      .map(HttpEntity.ChunkStreamPart.apply)

    val httpEntity = HttpEntity.Chunked(ContentTypes.`application/x-www-form-urlencoded`,formSource)
    //val httpEntity = FormData(("msg", request.asXML.toString)).toEntity(HttpCharsets.`UTF-8`)

    val httpRequest = RequestBuilding.Post(address, httpEntity)

    log.debug(
      s"Trying to send POST request to $address, will keep trying for ${tryUntil.timeLeft.toSeconds} sec."
    )

    implicit val timeout = Timeout(tryUntil.timeLeft)

    httpRouter ? BufferedRequest(httpRequest, tryUntil, callback) map (_ => ())
    // TODO return future
  }



  /**
    * Send callback O-MI message to `address`
    *
    * @param callback    Callback defining were to send request.
    * @param omiResponse O-MI response to be send.
    * @return future for the result of the callback is returned without blocking the calling thread
    */
  def sendCallback(callback: DefinedCallback, omiResponse: ResponseRequest): Future[Unit] = callback match {
    case cb: HTTPCallback =>
      sendHttp(cb, omiResponse, omiResponse.ttl)
    case cb: CurrentConnectionCallback =>
      sendCurrentConnection(cb, omiResponse, omiResponse.ttl)
    case cb: WSCallback =>
      sendWS(cb, omiResponse, omiResponse.ttl)
    case _ => Future.failed(new CallbackFailure("Unknown callback type"))

  }

  private[this] def sendWS(callback: WSCallback, request: ResponseRequest, ttl: Duration): Future[Unit] = {
    webSocketConnections.get(callback.address).map {
      handler: SendHandler =>

        log.debug(
          s"Trying to send response to WebSocket connection ${callback.address}"
        )
        val f = handler(request)
        f.onComplete {
          case Success(_) =>
            log.debug(
              s"Response  send successfully to WebSocket connection ${callback.address}"
            )
          case Failure(t: Throwable) =>
            log.debug(
              s"Response send  to WebSocket connection ${callback.address} failed, ${
                t.getMessage
              }. Connection removed."
            )
            webSocketConnections -= callback.address
        }
        f
    }.getOrElse(
      Future failed MissingConnection(callback)
    )
  }

  private[this] def sendCurrentConnection(callback: CurrentConnectionCallback,
                                          request: ResponseRequest,
                                          ttl: Duration): Future[Unit] = {
    currentConnections.get(callback.identifier).map {
      wsConnection: CurrentConnection =>

        log.debug(
          s"Trying to send response to current connection ${callback.identifier}"
        )
        val f = wsConnection.handler(request)
        f.onComplete {
          case Success(_) =>
            log.debug(
              s"Response  send successfully to current connection ${callback.identifier}"
            )
          case Failure(t: Throwable) =>
            log.debug(
              s"Response send  to current connection ${callback.identifier} failed, ${
                t.getMessage
              }. Connection removed."
            )
            currentConnections -= callback.identifier
        }
        f
    }.getOrElse(
      Future failed MissingConnection(callback)
    )
  }

  /** TODO: Test needs NodeSeq messaging
    * Send callback xml message containing `data` to `address`
    *
    * @param address           Uri that tells the protocol and address for the callback
    * @param currentConnection Optional value that maybe contains a active connection
    * @return future for the result of the callback is returned without blocking the calling thread
    *         def sendCallback( callback: DefinedCallback,
    *         omiMessage: OmiRequest,
    *         ttl: Duration
    *         ): Future[Unit] = {
    *         *
    *         sendHttp(callback, omiMessage, ttl)
    *         }
    */

  def createCallbackAddress(
                             address: String,
                             currentConnection: Option[CurrentConnection] = None
                           ): Try[Callback] = {
    address match {
      case "0" if currentConnection.nonEmpty =>
        Try {
          val cc = currentConnection.getOrElse(
            throw new Exception("Impossible empty CurrentConnection Option")
          )
          if (currentConnections.get(cc.identifier).isEmpty)
            currentConnections += cc.identifier -> cc
          CurrentConnectionCallback(cc.identifier)
        }
      case "0" if currentConnection.isEmpty =>
        Try {
          throw new Exception("Callback 0 not supported with http/https try using ws(websocket) instead")
        }
      case other: String =>
        Try {
          val uri = Uri(address)
          val hostAddress = uri.authority.host.address
          // Test address validity (throws exceptions when invalid)
          InetAddress.getByName(hostAddress)
          val scheme = uri.scheme
          webSocketConnections.get(uri.toString).map {
            wsConnection => WSCallback(uri)
          }.getOrElse {
            scheme match {
              case "http" | "https" => HTTPCallback(uri)
              case "ws" | "wss" =>
                val handler = createWebsocketConnectionHandler(uri)
                webSocketConnections += uri.toString -> handler
                WSCallback(uri)
            }
          }

        }
    }
  }

  private def createWebsocketConnectionHandler(uri: Uri) = {
    val queueSize = settings.websocketQueueSize
    val (outSource, futureQueue) =
      peekMatValue(Source.queue[ws.Message](queueSize, OverflowStrategy.fail))

    // keepalive? http://doc.akka.io/docs/akka/2.4.8/scala/stream/stream-cookbook.html#Injecting_keep-alive_messages_into_a_stream_of_ByteStrings
    
    val queue = WSQueue(futureQueue)

    val wsSink: Sink[Message, Future[akka.Done]] =
      Sink.foreach[Message] {
        case message: TextMessage.Strict =>
          log.warn(s"Received WS message from $uri. Message is ignored. ${message.text}")
        case _ =>
      }

    //def sendHandler = (response: ResponseRequest) => queue.send(Future(response.asXMLSource)) map { _ => () }

    val wsFlow = httpExtension.webSocketClientFlow(WebSocketRequest(uri))
    val (upgradeResponse, closed) = outSource.viaMat(wsFlow)(Keep.right)
      .toMat(wsSink)(Keep.both)
      .run()

    upgradeResponse.flatMap { upgrade =>
      if (upgrade.response.status == StatusCodes.OK) {
        log.info(s"Successfully connected WebSocket callback to $uri")
        Future.successful(akka.Done)
      } else {
        val msg = s"connection to  WebSocket callback: $uri failed: ${upgrade.response.status}"
        log.warn(msg)
        Future.failed(new Exception(msg))
      }
    }

    queue.sendHandler
  }
}
