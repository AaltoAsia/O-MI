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

import java.util.Date

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode

import akka.actor.{ActorRef, ActorLogging, Actor}
import akka.pattern.ask
import akka.util.Timeout
import types.odf._
import types.OmiTypes._
import types._
import http.{ActorSystemContext, Actors, Settings, OmiConfigExtension }

trait PollHandler extends Actor with ActorLogging{

  protected def subscriptionManager : ActorRef
  protected implicit val settings: OmiConfigExtension
  /** Method for handling PollRequest.
    * @param poll request
    * @return (xml response, HTTP status code)
    */
  def handlePoll(poll: PollRequest): Future[ResponseRequest] = {
    val ttl = poll.handleTTL
    implicit val timeout: Timeout = Timeout(ttl)
    val time = new Date().getTime
    val resultsFut =
      Future.sequence(poll.requestIDs.map { id : RequestID=>

      val objectsF: Future[Option[ODF] ] = (subscriptionManager ? PollSubscription(id)).mapTo[Option[ODF]]
      objectsF.recoverWith{
        case NonFatal(e) =>
          log.error( e.getMessage)
          e.printStackTrace()
          Future.failed(new RuntimeException(
        s"Error when trying to poll subscription: ${e.getMessage}"))
        case e: Throwable => 
          Future.failed(new RuntimeException(
        s"Error when trying to poll subscription: ${e.getMessage}"))
      }

      objectsF.map{
        case Some(objects: ODF) =>
          Results.Poll(id, objects)
        case None =>
          Results.NotFoundRequestIDs(Vector(id))
        case Some(objects: OdfTypes.OdfObject) =>
          Results.InternalError( Some("Wrong O-DF type when polled"))
        //case Failure(e) =>
        //  throw new RuntimeException(
        //    s"Error when trying to poll subscription: ${e.getMessage}")
      }
    })
    val response = resultsFut.map(results =>
        ResponseRequest(results)
    )

    response
  }
}
