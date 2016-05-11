
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

trait WriteHandler extends OmiRequestHandler{
  handler{
    case write: WriteRequest => handleWrite(write)
  }
  /** Method for handling WriteRequest.
    * @param write request
    * @return (xml response, HTTP status code)
    */
  def handleWrite( write: WriteRequest ) : Future[NodeSeq] ={
      val ttl = handleTTL(write.ttl)
      val future : Future[Try[Boolean]] = InputPusher.handleObjects(write.odf.objects, new Timeout(ttl.toSeconds, SECONDS)).mapTo[Try[Boolean]]
      future.recoverWith{case e =>{
        log.error(e, "Failure when writing")
        Future.failed(e)
      }}

      //val result = Await.result(future, ttl)
      future.flatMap(result => result match {
        case Success(b: Boolean ) =>
          if(b)
            Future.successful(success)
          else{
            log.warning("Write failed without exception")
            Future.failed(new RuntimeException("Write failed without exception."))
            }
        case Failure(thro: Throwable) => {
          log.error(thro, "Failure when writing")
          Future.failed(thro)
        }
      })
  }
}
