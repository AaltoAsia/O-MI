package responses

import scala.concurrent._

import akka.actor.ActorSystem
import spray.http._
import spray.client.pipelining._



// Errors
sealed trait CallbackResult
sealed class CallbackFailure               extends CallbackResult

case object  CallbackSuccess               extends CallbackResult
case class   HttpError(status: StatusCode) extends CallbackFailure
case object  ProtocolNotSupported          extends CallbackFailure



object CallbackHandlers {
  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures

  private val httpHandler: HttpRequest => Future[HttpResponse] = sendReceive

  private def sendHttp(address: Uri, data: xml.NodeSeq): Future[CallbackResult] = {
      val request = Post(address, data)
      val responseFuture = httpHandler(request)
      
      responseFuture map { response =>

        if (response.status.isSuccess)
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
