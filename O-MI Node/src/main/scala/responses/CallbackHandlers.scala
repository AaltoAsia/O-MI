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
import java.lang.Exception


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
    ttl: Duration): Future[CallbackResult] = Future{

      val tryUntil =  new Timestamp( new Date().getTime + ttl.toMillis)
      def currentTimestamp =  new Timestamp( new Date().getTime ) 
      def newTTL = Duration(tryUntil.getTime - currentTimestamp.getTime, MILLISECONDS )
      val request = Post(address, data)
      var attemps = 1
      try{
        var keepTrying = true
        var result : CallbackResult = new CallbackFailure
        while( keepTrying && tryUntil.after( currentTimestamp ) ){
          system.log.info(
            s"Trying to send POST request to $address, attemp: $attemps, will keep trying until $tryUntil."
          ) 
          val responseFuture = httpHandler(request)
          Await.ready[HttpResponse](
            responseFuture,
            newTTL
          )
          responseFuture.value match{
            case Some( Success( response ) )  =>
            if (response.status.isSuccess)//Content of response will not be handled.
              result = CallbackSuccess
            else
              result = HttpError(response.status)
            case Some( Failure(response) ) =>
            case None => result = new CallbackFailure
          }


          result match{
            case cs: CallbackSuccess.type =>
              //system.log.info(s"Successfully send POST request to $address")
              keepTrying = false
            case _ =>
              attemps += 1 
              Thread.sleep(5000)
              //system.log.info(s"Need to retry sending POST reqeust to $address, will keep trying until $tryUntil.") 
          } 
          
        }
        result
      } catch {
        case e: Exception =>
        system.log.error(
          e,"CallbackHandler"
        )
        e.printStackTrace()
        new CallbackFailure          
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
    ttl: Duration
  ): Future[CallbackResult] = {

    address.scheme match {

      case "http" =>
        sendHttp(address, data, ttl)

      case "https" =>
        sendHttp(address, data, ttl)

      case _ =>
        Future{ ProtocolNotSupported }

    }
  }

}
