
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

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode
import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask
import parsing.xmlGen.xmlTypes.RequestResultType


import agentSystem._
import types.OmiTypes._
import types.OdfTypes.OdfTreeCollection
import types._
import http.{ActorSystemContext, Actors}

/*
trait ResponseHandler extends OmiRequestHandlerBase{
  protected def agentSystem : ActorRef
  */
  /** Method for handling ResponseRequest.
    * @param response request
    * @return (xml response, HTTP status code)
  def handleResponse( response: ResponseRequest ) : Future[ResponseRequest] ={
    val ttl = response.handleTTL
    implicit val timeout = Timeout(ttl)
    val resultFuture : Future[OdfTreeCollection[OmiResult]]= Future.sequence(
      response.results.collect{ 
        case omiResult : OmiResult if omiResult.odf.nonEmpty =>
        val odf = omiResult.odf.get
        val write = WriteRequest( odf, None,ttl)
        val responseF = (agentSystem ? ResponsibilityRequest("ResponseHandler", write)).mapTo[ResponseRequest]
        val resultsF = responseF.map{
          case response: ResponseRequest => response.results
        }

        resultsF.recover{
          case e : Throwable =>
            log.error(e, "Failure when writing")
            Vector(Results.InternalError(e))
        }.map{
          case results: OdfTreeCollection[OmiResult] => Results.unionReduce(results)
        }
          //We do not want response request loops between O-MI Nodes
       // case omiResult : OmiResult if omiResult.odf.isEmpty =>
       //   Future.successful(Results.Success())
      }.toVector
    ).map( _.flatten )

  resultFuture.map{
    results => ResponseRequest(results.toVector)
  }

  }
}
*/
