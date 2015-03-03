package responses

import Common._
import parsing.Types._
import database._

import scala.util.{ Try, Success, Failure }
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration.Duration
import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import akka.actor.ActorRef
import akka.util.Timeout
import Timeout._
import akka.pattern.ask

object OMICancel {
  implicit val timeout: Timeout = Timeout(6000) // NOTE: ttl will timeout from OmiService

  def OMICancelResponse(request: Cancel, subHandler: ActorRef): NodeSeq = {

    var requestIds = request.requestId

    omiResponse {
      var nodes = NodeSeq.Empty

      val jobs = requestIds.map { id =>
        Try {
          val parsedId = id.toInt
          subHandler ? RemoveSubscription(parsedId)
        }
      }

      jobs.map {
        case Success(removeFuture) =>
          // NOTE: ttl will timeout from OmiService
          result {
            Await.result(removeFuture, Duration.Inf) match {
              case true => returnCode200
              case false => returnCode(404, "Subscription with requestId not found")
              case _ => returnCode(501, "Internal server error")
            }
          }
        case Failure(n: NumberFormatException) =>
          result { returnCode(400, "Invalid requestId") }
        case _ =>
          result { returnCode(501, "Internal server error") }
      }
    }
  }
}
