package responses

import Common._
import parsing.Types._
import scala.util.{ Try, Success, Failure }
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map
import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask

/** class for generating responses for omi:cancel requests.
 * Should be instantiated once and use that one object for all requests.
 * class needed for saving subHandler.
 */
class OMICancelGen(val subHandler: ActorRef
                  ) extends ResponseGen[Cancel] {

  implicit val timeout= Timeout( 10.seconds ) // NOTE: ttl will timeout from elsewhere

  
  /**
   * Generates ODF containing a list of omi:results along with their return codes
   *
   * @param Cancel the cancel request given by the parser
   * @return ActorRef the subscription handler actor
   */
  override def genResult(request: Cancel) = {

    val requestIds = request.requestId


    val jobs = requestIds.map { id =>
      Try {
        val parsedId = id.toInt
        subHandler ? RemoveSubscription(parsedId)
      }
    }

    jobs.map {
      case Success(removeFuture) =>
        // NOTE: ttl will timeout from OmiService
        resultWrapper {
          Await.result(removeFuture, Duration.Inf) match {
            case true => returnCode200
            case false => returnCode(404, "Subscription with requestId not found")
            case _ => returnCode(501, "Internal server error")
          }
        }
      case Failure(n: NumberFormatException) =>
        resultWrapper { returnCode(400, "Invalid requestId") }
      case Failure(e) =>
        ErrorResponse.internalError(e)

    }.reduceLeft(_ ++ _)
  }
}
