

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


import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import types.OmiTypes._
import types._
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import http.{ActorSystemContext, Actors, Settings, OmiConfigExtension }

trait CancelHandler {

  protected def subscriptionManager : ActorRef 
  protected implicit val settings: OmiConfigExtension
  /** Method for handling CancelRequest.
    * @param cancel request
    * @return (xml response, HTTP status code) wrapped in a Future
    */
  def handleCancel(cancel: CancelRequest): Future[ResponseRequest] = {
    implicit val timeout: Timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    val jobs: Future[Seq[OmiResult]] = Future.sequence(cancel.requestIDs.map {
      id =>
      (subscriptionManager ? RemoveSubscription(id)).mapTo[Boolean].map( res =>
        if(res){
          Results.Success()
        }else{
          Results.NotFoundRequestIDs(Vector(id))
        }
      ).recoverWith{
        case e : Throwable => {
          val error = "Error when trying to cancel subscription: "
          Future.successful(Results.InternalError(Some(error + e.toString)))
        }
      }
    })

    jobs.map{
      results => ResponseRequest(Results.unionReduce(results.toVector))
    }
  }
}
