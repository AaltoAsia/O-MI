
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
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode
import akka.actor.ActorRef
import akka.util.Timeout
import akka.pattern.ask

import agentSystem._
import types.OmiTypes._
import http.{ActorSystemContext, Actors}

trait WriteHandler extends OmiRequestHandlerBase{
  import nc._
  /** Method for handling WriteRequest.
    * @param write request
    * @return (xml response, HTTP status code)
    */
  def handleWrite( write: WriteRequest ) : Future[ResponseRequest] ={
    val ttl = handleTTL(write.ttl)
    implicit val timeout = Timeout(ttl)

      val result = (agentSystem ? ResponsibilityRequest("WriteHandler", write)).mapTo[ResponsibleAgentResponse]

      result.recoverWith{
        case e : Throwable =>
        log.error(e, "Failure when writing")
        Future.failed(e)
      }


      result.onSuccess{ case succ => log.debug( succ.toString) }
      val response = result.map{
        case SuccessfulWrite(_) => Responses.Success()
        case FailedWrite(paths, reasons) =>  
          val returnV : OmiReturn = OmiReturn(
            "400",
            Some(
              "Paths: " +  paths.mkString("\n") + " reason:\n" + reasons.mkString("\n") 
            )
          )
          val result : OmiResult = OmiResult(returnV)
          ResponseRequest( Vector( result ))
          
        case MixedWrite(successfulPaths, failed)=> 
          val returnV : OmiReturn = OmiReturn(
            "400",
            Some(
              "Following paths failed:\n" +
              failed.paths.mkString("\n") + 
              " reason:\n" + failed.reasons.mkString("\n")
            )
          )
          val result : OmiResult = OmiResult(returnV)
          ResponseRequest( Vector( result))
          
      }
      response
  }
}
