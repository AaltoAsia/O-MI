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

import java.net.InetAddress
import java.sql.Timestamp
import java.util.Date

import scala.collection.mutable.{Map => MutableMap}
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Try,Success,Failure}

import akka.actor.ActorSystem
import akka.http.scaladsl.{ Http, HttpExt}
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model._
import akka.stream.{ActorMaterializer, Materializer}
import types.OmiTypes._

import http.{OmiConfigExtension, Storages, OmiNodeContext, Settings, ActorSystemContext, Actors}
import CallbackHandler._
object CallbackHandler{
  val supportedProtocols = Vector("http", "https")
  type SendHandler = ResponseRequest => Future[Unit]

  // Base error
  sealed class CallbackFailure(msg: String, callback: Callback) extends Exception(msg)

  // Errors
  case class   HttpError(status: StatusCode, callback: HTTPCallback) extends
    CallbackFailure(s"Received HTTP status $status from ${callback.uri}.", callback)

  case class  ProtocolNotSupported(protocol: String, callback: Callback) extends
    CallbackFailure(s"$protocol is not supported, use one of " + supportedProtocols.mkString(", "), callback)

  case class  ForbiddenLocalhostPort( callback: Callback)  extends
    CallbackFailure(s"Callback address is forbidden.", callback)
  case class MissingConnection( callback: CurrentConnectionCallback ) extends
    CallbackFailure(s"CurrentConnection not found for ${callback.identifier}",callback)
}
/**
 * Handles sending data to callback addresses 
 */
class CallbackHandler(
  protected val settings: OmiConfigExtension
  )(
  protected implicit val system : ActorSystem,
  protected implicit val materializer : ActorMaterializer
){
  import system.dispatcher
  protected val httpExtension: HttpExt = Http(system)
  val portsUsedByNode = settings.ports.values.toSeq

  protected def currentTimestamp =  new Timestamp( new Date().getTime )

  private def log = system.log
  val currentConnections: MutableMap[Int, SendHandler ] = MutableMap.empty
  def addCurrentConnection( identifier: Int, handler: SendHandler) = currentConnections += identifier -> handler 
  private[this] def sendHttp( callback: HTTPCallback,
                              request: OmiRequest,
                              ttl: Duration): Future[Unit] = {
    val tryUntil =  new Timestamp( new Date().getTime + (ttl match {
      case ttl: FiniteDuration => ttl.toMillis
      case _ => settings.callbackTimeout.toMillis
    }))

    def newTTL = Duration(tryUntil.getTime - currentTimestamp.getTime, MILLISECONDS )

    val address = callback.uri
    val httpRequest = RequestBuilding.Post(address, request.asXML)

    log.info(
      s"Trying to send POST request to $address, will keep trying until $tryUntil."
    )

    val check : HttpResponse => Future[Unit] = { response =>
        if (response.status.isSuccess){
          //TODO: Handle content of response, possible piggypacking
          log.info(
            s"Successful send POST request to $address."
          )
          Future.successful(())
        } else Future failed HttpError(response.status, callback)
    }

    def trySend = httpExtension.singleRequest(httpRequest)//httpHandler(request)

    val retry = retryUntilWithCheck[HttpResponse, Unit](
            settings.callbackDelay,
            tryUntil
          )(check)(trySend)

    retry.onFailure{
      case e : Throwable=>
        system.log.warning(
          s"Failed to send POST request to $address after trying until ttl ended."
        )
    }

    retry

  }

  private def retryUntilWithCheck[T,U]( delay: FiniteDuration, tryUntil: Timestamp, attempt: Int = 1 )( check: T => Future[U])( creator: => Future[T] ) : Future[U] = {
    import system.dispatcher // execution context for futures
    val future = creator
    future.flatMap{ check }.recoverWith{
      case e if tryUntil.after( currentTimestamp ) && !system.isTerminated => 
        system.log.debug(
          s"Retrying after $delay. Will keep trying until $tryUntil. Attempt $attempt."
        )
        Thread.sleep(delay.toMillis)
        retryUntilWithCheck[T,U](delay,tryUntil, attempt + 1 )(check )(creator )
    }
  }
  /**
   * Send callback O-MI message to `address`
   * @param address Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
   */
  def sendCallback( callback: DefinedCallback, omiResponse: ResponseRequest): Future[Unit] = callback match {
      case cb : HTTPCallback =>
        sendHttp(cb, omiResponse, omiResponse.ttl)
      case cb : CurrentConnectionCallback =>
        sendCurrentConnection(cb, omiResponse, omiResponse.ttl)
  }
  private[this] def sendCurrentConnection( callback: CurrentConnectionCallback, request: ResponseRequest, ttl: Duration): Future[Unit] = {
    currentConnections.get(callback.identifier).map{
      case handler: (ResponseRequest => Future[Unit]) => 

            system.log.debug(
              s"Trying to send response to current connection ${callback.identifier}"
            )
        val f = handler(request)
        f.onComplete{
          case Success(_) => 
            system.log.debug(
              s"Response  send successfully to current connection ${callback.identifier}"
            )
          case Failure(t: Throwable) =>
            system.log.debug(
              s"Response send  to current connection ${callback.identifier} failed, ${t.getMessage()}. Connection removed."
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
   * @param address Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
  def sendCallback( callback: DefinedCallback,
                    omiMessage: OmiRequest,
                    ttl: Duration
                    ): Future[Unit] = {

      sendHttp(callback, omiMessage, ttl)
    }
   */
}
