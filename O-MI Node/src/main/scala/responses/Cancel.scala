package responses

import Common._
import parsing.Types._
import database._
import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

object OMICancel {

  def OMICancelResponse(request: Cancel): NodeSeq = {

    var requestIds = request.requestId

    val response =
      omiResponse {
        var nodes = NodeSeq.Empty

        for (id <- requestIds) {
          nodes ++= result{
            try {
              // TODO: update somehow SubscriptionHandlerActor's internal memory
              if (SQLite.removeSub(id.toInt))
                returnCode200
              else
                returnCode(404, "Subscription with requestId not found")

            } catch {
              case n: NumberFormatException =>
                returnCode(400, "Invalid requestId")
            } 
          } 
        }
        nodes
      }

    response
  }
}
