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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import parsing.xmlGen.xmlTypes.RequestResultType
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import types.OmiTypes._
import types._
import http.{ActorSystemContext, Actors, Settings, OmiConfigExtension }

trait SubscriptionHandler {

  protected def subscriptionManager : ActorRef
  protected implicit val settings: OmiConfigExtension
  /** Method for handling SubscriptionRequest.
    * @param _subscription request
    * @return (xml response, HTTP status code)
    */
  def handleSubscription(_subscription: SubscriptionRequest): Future[ResponseRequest] = {
    //if interval is below allowed values, set it to minimum allowed value
    val subscription: SubscriptionRequest = _subscription match {
      case SubscriptionRequest(  interval, _, _, _, _,  _, _, _) if interval < settings.minSubscriptionInterval && interval.toSeconds >= 0 =>
        _subscription.copy(interval= settings.minSubscriptionInterval)
      case s : SubscriptionRequest=> s
    }
    val ttl = subscription.handleTTL
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    val subFuture: Future[OmiResult] = (subscriptionManager ? NewSubscription(subscription))
      .mapTo[Try[Long]]
      .map{
        case Success(id: Long) if _subscription.interval != subscription.interval =>
          Results.Subscription(id,Some(subscription.interval))
        case Success(id: Long) =>
          Results.Subscription(id)
        case Failure(ex) => throw ex
      }.recoverWith{
      case e: IllegalArgumentException => Future.successful(Results.InvalidRequest(Some(e.getMessage())))
      case e : Throwable => Future.failed(new RuntimeException(s"Error when trying to create subscription: ${e.getMessage}", e))
    }
    subFuture.map{ results =>
      ResponseRequest( Vector(results) )
    }
  }
}
