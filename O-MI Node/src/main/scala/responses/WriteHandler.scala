
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

trait WriteHandler extends OmiRequestHandler{
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
