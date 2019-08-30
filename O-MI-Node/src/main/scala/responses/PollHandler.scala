/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package responses

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout
import http.OmiConfigExtension
import types.omi._
import types.odf._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

trait PollHandler extends Actor with ActorLogging {

  protected def subscriptionManager: ActorRef

  protected implicit val settings: OmiConfigExtension

  /** Method for handling PollRequest.
    *
    * @param poll request
    * @return (xml response, HTTP status code)
    */
  def handlePoll(poll: PollRequest): Future[ResponseRequest] = {
    val ttl = poll.handleTTL
    implicit val timeout: Timeout = Timeout(ttl)
    val resultsFut =
      Future.sequence(poll.requestIDs.map { id: RequestID =>

        val objectsF: Future[Option[ODF]] = (subscriptionManager ? PollSubscription(id, ttl)).mapTo[Option[ODF]]
        objectsF.recoverWith {
          case NonFatal(e) =>
            log.error(e.getMessage)
            e.printStackTrace()
            Future.failed(new RuntimeException(
              s"Error when trying to poll subscription: ${e.getMessage}"))
          case e: Throwable =>
            Future.failed(new RuntimeException(
              s"Error when trying to poll subscription: ${e.getMessage}"))
        }

        objectsF.map {
          case Some(objects: ODF) =>
            Results.Poll(id, objects)
          case None =>
            Results.NotFoundRequestIDs(Vector(id))
        }
      })
    val response = resultsFut.map(results =>
      ResponseRequest(results)
    )

    response
  }
}
