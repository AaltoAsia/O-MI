
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
//import spray.http.StatusCode

import agentSystem.{PromiseResult, PromiseWrite}
import akka.actor.ActorRef
import responses.OmiGenerator._
import types.OmiTypes._

trait WriteHandler extends OmiRequestHandlerBase{
  def agentSystem : ActorRef
  /** Method for handling WriteRequest.
    * @param write request
    * @return (xml response, HTTP status code)
    */
  def handleWrite( write: WriteRequest ) : Future[NodeSeq] ={
      handleTTL(write.ttl)

      val promiseResult = PromiseResult()
      agentSystem ! PromiseWrite(promiseResult, write)
      val successF = promiseResult.isSuccessful
      successF.recoverWith{
        case e : Throwable =>{
        log.error(e, "Failure when writing")
        Future.failed(e)
      }}


      val response = successF.map{
        succ => success 
      }
      response
  }
}
