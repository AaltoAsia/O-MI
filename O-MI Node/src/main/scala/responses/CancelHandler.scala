

/**********************************************************************************
 *    Copyright (c) 2015 Aalto University.                                        *
 *                                                                                *
 *    Licensed under the 4-clause BSD (the "License");                            *
 *    you may not use this file except in compliance with the License.            *
 *    You may obtain a copy of the License at top most directory of project.      *
 *                                                                                *
 *    Unless required by applicable law or agreed to in writing, software         *
 *    distributed under the License is distributed on an "AS IS" BASIS,           *
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    *
 *    See the License for the specific language governing permissions and         *
 *    limitations under the License.                                              *
 **********************************************************************************/

package responses


import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

import responses.OmiGenerator._
import types.OmiTypes._
import types._
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import spray.http.StatusCode

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout


trait CancelHandler extends OmiRequestHandlerBase{
  def subscriptionManager : ActorRef

  /** Method for handling CancelRequest.
    * @param cancel request
    * @return (xml response, HTTP status code) wrapped in a Future
    */
  def handleCancel(cancel: CancelRequest): Future[NodeSeq] = {
    log.debug("Handling cancel.")
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    val jobs = Future.sequence(cancel.requestID.map { id =>
      (subscriptionManager ? RemoveSubscription(id)).mapTo[Boolean].map( res =>
        if(res){
          Results.success
        }else{
          Results.notFoundSub
        }
      ).recoverWith{
        case e : Throwable => {
          val error = "Error when trying to cancel subcription: "
          log.error(e, error)
          Future.successful(Results.internalError(error + e.toString))
        }
      }
    })

    jobs.map( res =>
    (
      xmlFromResults(
        1.0,
      res.toSeq: _*
        )))
  }
}
