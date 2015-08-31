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

import scala.concurrent._

import akka.actor.ActorSystem
import spray.http.{StatusCode, HttpResponse, HttpRequest, Uri}
import spray.client.pipelining._


/**
 * Handles sending data to callback addresses 
 */
object CallbackHandlers {
  sealed trait CallbackResult
  // Base error
  sealed class CallbackFailure              extends Throwable with CallbackResult
  //Success
  case object  CallbackSuccess               extends CallbackResult
  // Errors
  case class   HttpError(status: StatusCode) extends CallbackFailure
  case object  ProtocolNotSupported          extends CallbackFailure


  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures

  private[this] val httpHandler: HttpRequest => Future[HttpResponse] = sendReceive

  private[this] def sendHttp(address: Uri, data: xml.NodeSeq): Future[CallbackResult] = {

      val request = Post(address, data)
      val responseFuture = httpHandler(request)
      
      responseFuture map { response =>

        if (response.status.isSuccess)//Content of response will not be handled.
          CallbackSuccess
        else
          HttpError(response.status)
      }
  }

  /**
   * Send callback xml message containing `data` to `address`
   * @param address spray Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
   */
  def sendCallback(address: Uri, data: xml.NodeSeq): Future[CallbackResult] = {

    address.scheme match {

      case "http" =>
        sendHttp(address, data)

      case "https" =>
        sendHttp(address, data)

      case _ =>
        Future{ ProtocolNotSupported }

    }
  }

}
