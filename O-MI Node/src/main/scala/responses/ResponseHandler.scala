
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

import agentSystem.{PromiseResult, PromiseWrite}
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode

import akka.actor.ActorRef
import types.OmiTypes._
import types._

trait ResponseHandler extends OmiRequestHandlerBase{
  def agentSystem : ActorRef
  /** Method for handling ResponseRequest.
    * @param response request
    * @return (xml response, HTTP status code)
    */
  def handleResponse( response: ResponseRequest ) : Future[ResponseRequest] ={
    val ttl = handleTTL(response.ttl)
    val resultFuture = Future.sequence(response.results.collect{ 
        case omiResult : OmiResult if omiResult.odf.nonEmpty =>
        val odf = omiResult.odf.get
        val promiseResult = PromiseResult()
        val write = WriteRequest( odf, None,ttl)
        agentSystem ! PromiseWrite(promiseResult, write)
        val successF = promiseResult.isSuccessful
        successF.recoverWith{
          case e : Throwable=>
            log.error(e, "Failure when writing")
            Future.failed(e)
          }

          val response = successF.map{
            succ => Results.Success()
          }
          response
          //We do not want response request loops between O-MI Nodes
          /*
        case omiResult : OmiResult if omiResult.odf.isEmpty =>
          Future.successful(Results.Success())
        */
      }.toSeq
    )

  resultFuture.map{
    results => ResponseRequest(results.toVector)
  }

  }
}
