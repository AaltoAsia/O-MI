/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package responses

import java.sql.Timestamp
import java.util.Date

import akka.actor.ActorSystem
import spray.client.pipelining._
import spray.http.{HttpRequest, HttpResponse, StatusCode, Uri}

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Try, Failure, Success}
import java.net.{ URL, InetAddress, UnknownHostException }

/**
 * Handles sending data to callback addresses 
 */
object CallbackHandlers {
  val supportedProtocols = Vector("http", "https")
  sealed trait CallbackResult
  // Base error
  sealed class CallbackFailure(msg: String, callback: Uri)              extends Exception(msg) with CallbackResult
  //Success
  case class  CallbackSuccess()               extends CallbackResult
  // Errors
  case class   HttpError(status: StatusCode, callback: Uri )  extends CallbackFailure(s"Received HTTP status $status from $callback.", callback)
  case class  ProtocolNotSupported(protocol: String, callback: Uri)          extends CallbackFailure(s"$protocol is not supported, use one of " + supportedProtocols.mkString(", "), callback)
  case class  ForbiddenLocalhostPort( callback: Uri)        extends CallbackFailure(s"Callback address is forbidden.", callback)

  protected def currentTimestamp =  new Timestamp( new Date().getTime ) 
  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures
  val settings = http.Boot.settings 
  private[this] val httpHandler: HttpRequest => Future[HttpResponse] = sendReceive

  private[this] def sendHttp(
    address: Uri,
    data: xml.NodeSeq,
    ttl: Duration): Future[CallbackResult] = {
      val tryUntil =  new Timestamp( new Date().getTime + (ttl match {
        case ttl: FiniteDuration => ttl.toMillis
        case _ => http.Boot.settings.callbackTimeout.toMillis
      }))
      def newTTL = Duration(tryUntil.getTime - currentTimestamp.getTime, MILLISECONDS )
      val request = Post(address, data)
          system.log.info(
            s"Trying to send POST request to $address, will keep trying until $tryUntil."
          ) 
          val check : PartialFunction[HttpResponse, CallbackResult] = { 
            case response: HttpResponse =>
              system.log.info(
                s"Successful send POST request to $address."
              ) 
            if (response.status.isSuccess){
              //TODO: Handle content of response, possible piggypacking
              system.log.info(
                s"Successful send POST request to $address."
              ) 
              CallbackSuccess()
            } else throw HttpError(response.status, address)
          }
          def trySend = httpHandler(request)
          val retry = retryUntilWithCheck[HttpResponse, CallbackResult](
            trySend,
            check,
            settings.callbackDelay,
            tryUntil 
          )
          retry.onFailure{
            case e =>
            system.log.warning(
              s"Failed to send POST request to $address after trying until ttl ended."
            ) 
          }
          retry
          
    }

    private def retrySendingUntilWithCheck[T,U]( message: HttpRequest, check: PartialFunction[HttpResponse,U], delay: FiniteDuration, tryUntil: Timestamp ) : Future[U] = {
      val future = httpHandler(message)
    future.map{ check }.recoverWith{
      case e if tryUntil.after( currentTimestamp ) => 
        system.log.debug(
          s"Retrying after $delay."
        ) 
        Thread.sleep(delay.toMillis)
        retrySendingUntilWithCheck[T,U](message, check, delay,tryUntil)  
    }
  }

  private def retryUntilWithCheck[T,U]( creator: => Future[T] , check: PartialFunction[T,U], delay: FiniteDuration, tryUntil: Timestamp, attempt: Int = 1 ) : Future[U] = {
    val future = creator
    future.map{ check }.recoverWith{
      case e if tryUntil.after( currentTimestamp ) => 
        system.log.debug(
          s"Retrying after $delay. Will keep trying until $tryUntil. Attempt $attempt."
        ) 
        Thread.sleep(delay.toMillis)
        retryUntilWithCheck[T,U](creator, check, delay,tryUntil, attempt + 1 )  
    }
  }
  /**
   * Send callback xml message containing `data` to `address`
   * @param address spray Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
   */
  def sendCallback(
    address: String,
    data: xml.NodeSeq,
    ttl: Duration
  ): Future[CallbackResult] = {

    checkCallback(address).flatMap{ uri =>
          sendHttp(uri, data, ttl)
    }
  }

  def checkCallback( callback: String ): Future[Uri]= Future{
    
    val uri = Uri(callback)
    val hostAddress = uri.authority.host.address
    val IpAddress = InetAddress.getByName(hostAddress)
    
    val portsUsedByNode =settings.ports.values.toSeq 
    val validScheme = supportedProtocols.contains(uri.scheme)
    val validPort = hostAddress != "localhost" || !portsUsedByNode.contains(uri.effectivePort)
    val invalidPortMsg = "Tryed to send callback to port used by O-MI Node"
    val invalidSchemeMsg = "Tryed to send callback to port used by O-MI Node"
    (validScheme, validPort )match {
      case ( true, true ) =>
        uri
      case ( false, _ ) =>
        throw ProtocolNotSupported(uri.scheme,uri)
      case ( true, false ) =>
        throw ForbiddenLocalhostPort(uri)
    }
  }
}
