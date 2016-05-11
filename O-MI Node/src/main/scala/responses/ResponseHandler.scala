
package responses

import parsing.xmlGen.xmlTypes.RequestResultType

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable
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
import agentSystem.InputPusher
import CallbackHandlers._
import database._

trait ResponseHandler extends OmiRequestHandler{
  handler{
    case response: ResponseRequest => handleResponse(response)
  }
  /** Method for handling ResponseRequest.
    * @param response request
    * @return (xml response, HTTP status code)
    */
  def handleResponse( response: ResponseRequest ) : Future[NodeSeq] ={
      val ttl = handleTTL(response.ttl)
      val resultFuture = Future.sequence(response.results.map{ result =>
           result.odf match {
            case Some(odf) =>
            val future =  InputPusher.handleObjects(odf.objects, new Timeout(ttl)).mapTo[Try[Boolean]]
              future.map{res => res match {
                case Success(true) => Results.success
                case Success(false) => Results.invalidRequest("Failed without exception.")
                case Failure(thro: Throwable) => throw thro
              }}
            case None => //noop?
              Future.successful(Results.success)
          }
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
