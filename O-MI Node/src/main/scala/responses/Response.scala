package responses

import Common._
import CallbackHandlers.sendCallback
import parsing.Types.OmiRequest

import scala.xml._
import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Await, ExecutionContext, TimeoutException}

/**
 * xxxx Responses should inherit this and implement e.g. a singleton that takes other paramaters
 * through class constructor arguments. This trait leaves genResult and toXML defs abstract.
 */
trait ResponseGen[R <: OmiRequest] {

  def runRequest(request: R)(implicit ec: ExecutionContext): NodeSeq = {
    if (request.hasCallback){
      // TODO: Can't cancel this callback
      Future{ runGeneration(request) } map { xml =>
        sendCallback(request.callback.get, xml)
      }

      omiResult(returnCode(200, "OK, callback job started"))

    } else {
      runGeneration(request)
    }
  }

  /**
   * Overload this or genResult. Should generate the odf msg part 
   */
  def genMsg(request: R): OmiOdfMsg = ???


  /**
   * You can overload this. Default implementation calls genMsg and sets returnCode 200.
   * Generates the Result, inner response body of the Response.
   */
  def genResult(request: R): OmiResult = odfResultWrapper(returnCode200 :+ genMsg(request))

  /**
   * Used to create an OmiEnvelope from this response objects. Should handle all response
   * related actions except for subscriptions. Subscriptions should have only message generation step
   * wrapped in a Response.
   *
   * @param ttlTimeout TTL timeout from the original request.
   * @return Returns the full omiEnvelope result with either an error response or the result of the response.
   */
  def runGeneration(request: R)(implicit ec: ExecutionContext): NodeSeq = {

    val timeout = if (request.ttl > 0) request.ttl.seconds else Duration.Inf

    val responseFuture = Future{genResult(request)}

    val result: OmiResult =
      Try {
        Await.result(responseFuture, timeout)

      } match {
        case Success(res : OmiResult) => res

        case Failure(e: TimeoutException) => OmiResult(
          returnCode(408, "TTL timeout, consider increasing TTL or is the server overloaded?")
        )
        case Failure(e) => OmiResult(
          returnCode(501, "Internal server error: " + e.getMessage())
        )
      }

    omiResponse(result)
  }

}
