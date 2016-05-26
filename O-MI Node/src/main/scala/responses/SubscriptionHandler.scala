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

import parsing.xmlGen.xmlTypes.RequestResultType

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions.{ iterableAsScalaIterable, asJavaIterable}
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.collection.breakOut
import scala.xml.{ NodeSeq, XML }
//import spray.http.StatusCode

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.util.Timeout
import akka.pattern.ask


import types._
import OmiTypes._
import OdfTypes._
import OmiGenerator._
import parsing.xmlGen.{ xmlTypes, scalaxb, defaultScope }
import CallbackHandlers._
import database._

trait SubscriptionHandler extends OmiRequestHandler{
  import http.Boot
  def subscriptionManager : ActorRef
  handler{
    case subscription: SubscriptionRequest => handleSubscription(subscription)
  }

  /** Method for handling SubscriptionRequest.
    * @param _subscription request
    * @return (xml response, HTTP status code)
    */
  def handleSubscription(_subscription: SubscriptionRequest): Future[NodeSeq] = {
    //if interval is below allowed values, set it to minimum allowed value
    val subscription: SubscriptionRequest = _subscription match {
      case SubscriptionRequest( _, interval, _, _, _, _) if interval < Boot.settings.minSubscriptionInterval && interval.toSeconds >= 0 =>
        _subscription.copy(interval=Boot.settings.minSubscriptionInterval)
      case s => s
    }
    val ttl = handleTTL(subscription.ttl)
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    val subFuture: Future[RequestResultType] = (subscriptionManager ? NewSubscription(subscription)).mapTo[Try[Long]].map( res=>res  match {
        case Success(id: Long) if _subscription.interval != subscription.interval =>
          Results.subscription(id.toString,subscription.interval.toSeconds)
        case Success(id: Long) =>
          Results.subscription(id.toString)
        case Failure(ex) => throw ex
      }).recoverWith{
      case e: IllegalArgumentException => Future.successful(Results.invalidRequest(e.getMessage()))
      case e => Future.failed(new RuntimeException(s"Error when trying to create subscription: ${e.getMessage}", e))
    }
    subFuture.map{ response =>{

        xmlFromResults(
          1.0,
          response)

    }}
  }
}
