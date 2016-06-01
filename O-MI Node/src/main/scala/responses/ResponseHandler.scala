
package responses

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import agentSystem.{PromiseResult, PromiseWrite}
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import spray.http.StatusCode

import akka.actor.ActorRef
import responses.OmiGenerator._
import types.OmiTypes._
import types._

trait ResponseHandler extends OmiRequestHandler{
  def agentSystem : ActorRef
  /** Method for handling ResponseRequest.
    * @param response request
    * @return (xml response, HTTP status code)
    */
  def handleResponse( response: ResponseRequest ) : Future[NodeSeq] ={
    val ttl = handleTTL(response.ttl)
    val resultFuture = Future.sequence(response.results.map{ 
        case OmiResult(_,_,_,_,Some(odf)) =>
        val promiseResult = PromiseResult()
        val write = WriteRequest( ttl, odf)
        agentSystem ! PromiseWrite(promiseResult, write)
        val successF = promiseResult.isSuccessful
        successF.recoverWith{
          case e : Throwable=>
            log.error(e, "Failure when writing")
            Future.failed(e)
          }

          val response = successF.map{
            succ => Results.success 
          }
          response
        case OmiResult(_,_,_,_,None) =>
          Future.successful(Results.success)
        
      }.toSeq
    )

  resultFuture.map(results =>
    xmlFromResults(
      1.0,
      results:_*
    )
)

  }
}
