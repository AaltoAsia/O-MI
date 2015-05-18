package responses

import Common._
import CallbackHandlers.sendCallback
import parsing.Types.OmiTypes.OmiRequest

import scala.xml._
import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.{Future, Await, ExecutionContext, TimeoutException}
import scala.collection.JavaConversions.iterableAsScalaIterable

/** Responses should inherit this and implement e.g. a class that takes other
 * paramaters through class constructor arguments. This trait leaves genMsg def
 * abstract genResult can also be overloaded.
 */
trait ResponseGen[R <: OmiRequest] {

  def runRequest(request: R)(implicit ec: ExecutionContext): NodeSeq = {
    if (request.hasCallback){
      // TODO: Can't cancel this callback
      Future{ runGeneration(request) } map { xml =>
        sendCallback(request.callback.get.toString, xml)
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
   * Extra action to run if internal error is caught. E.g. logging.
   * @param Throwable Exceptions have e.g. .getMessage() that can be used
   */
  def actionOnInternalError: Throwable => Unit = { _ => /*noop*/ }

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

        case Failure(e: TimeoutException) => ErrorResponse.ttlTimeOut

        case Failure(e) => 
          actionOnInternalError(e)
          ErrorResponse.internalError(e)
      }

    omiResponse(result)
  }

}
