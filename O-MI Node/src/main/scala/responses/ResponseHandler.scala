
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
import types._
import http.{ActorSystemContext, Actors}

trait ResponseHandler extends OmiRequestHandlerBase{
  protected def agentSystem : ActorRef
  /** Method for handling ResponseRequest.
    * @param response request
    * @return (xml response, HTTP status code)
    */
  def handleResponse( response: ResponseRequest ) : Future[ResponseRequest] ={
    val ttl = response.handleTTL
    implicit val timeout = Timeout(ttl)
    val resultFuture = Future.sequence(response.results.collect{ 
        case omiResult : OmiResult if omiResult.odf.nonEmpty =>
        val odf = omiResult.odf.get
        val write = WriteRequest( odf, None,ttl)
        val result = (agentSystem ? ResponsibilityRequest("ResponseHandler", write)).mapTo[ResponsibleAgentResponse]
        result.recoverWith{
          case e : Throwable =>
            log.error(e, "Failure when writing")
            Future.failed(e)
        }


        result.onSuccess{ case succ => log.info( succ.toString) }
          val response : Future[OmiResult]= result.map{
            case SuccessfulWrite(_) => Results.Success() 
          case FailedWrite(paths, reasons) =>  
            val returnV : OmiReturn = Returns.InvalidRequest(
              Some(
                "Paths: " +  paths.mkString("\n") + " reason:\n" + reasons.mkString("\n") 
              )
            )
            OmiResult(returnV)
            
        case MixedWrite(successfulPaths, failed)=> 
          val returnV : OmiReturn = Returns.InvalidRequest(
            Some(
              "Following paths failed:\n" +
              failed.paths.mkString("\n") + 
              " reason:\n" + failed.reasons.mkString("\n")
            )
          )
          OmiResult(returnV) 
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
