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
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

import akka.actor.ActorSystem
import spray.http.{StatusCode, HttpResponse, HttpRequest, Uri}
import spray.client.pipelining._
import java.sql.Timestamp
import java.util.Date


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

  private[this] def sendHttp(
    address: Uri,
    data: xml.NodeSeq,
    tryUntil: Timestamp): Future[CallbackResult] = Future{

      val request = Post(address, data)
      try{
        var keepTrying = true
        var result : CallbackResult = new CallbackFailure
        while( keepTrying && tryUntil.after( new Timestamp( new Date().getTime ) )  ){
          val responseFuture = httpHandler(request)
          val duration =Duration(tryUntil.getTime - new Date().getTime , MILLISECONDS) 
          val response = Await.result[HttpResponse](
            responseFuture,
            duration
          )

          if (response.status.isSuccess)//Content of response will not be handled.
            result = CallbackSuccess
          else
            result = HttpError(response.status)
          result match{
            case cs: CallbackSuccess.type =>
                keepTrying = false
          } 
          
        }
        result
      } catch {
        case e: Exception => new CallbackFailure          
      }
    }

  /**
   * Send callback xml message containing `data` to `address`
   * @param address spray Uri that tells the protocol and address for the callback
   * @param data xml data to send as a callback
   * @return future for the result of the callback is returned without blocking the calling thread
   */
  def sendCallback(
    address: Uri,
    data: xml.NodeSeq,
    tryUntil: Timestamp
  ): Future[CallbackResult] = {

    address.scheme match {

      case "http" =>
        sendHttp(address, data, tryUntil)

      case "https" =>
        sendHttp(address, data, tryUntil)

      case _ =>
        Future{ ProtocolNotSupported }

    }
  }

}
