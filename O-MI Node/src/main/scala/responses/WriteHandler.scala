
package responses

import parsing.xmlGen.xmlTypes.RequestResultType

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException, Promise }
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
import agentSystem.{SuccessfulWrite, ResponsibleAgentResponse, PromiseWrite, PromiseResult }
import CallbackHandlers._
import database._

trait WriteHandler extends OmiRequestHandler{
  def agentSystem : ActorRef
  handler{
    case write: WriteRequest => handleWrite(write)
  }
  /** Method for handling WriteRequest.
    * @param write request
    * @return (xml response, HTTP status code)
    */
  def handleWrite( write: WriteRequest ) : Future[NodeSeq] ={
      val ttl = handleTTL(write.ttl)

      val promiseResult = PromiseResult()
      agentSystem ! PromiseWrite(promiseResult, write)
      val successF = promiseResult.isSuccessful
      successF.recoverWith{
        case e =>{
        log.error(e, "Failure when writing")
        Future.failed(e)
      }}


      val response = successF.map{
        succ => success 
      }
      response
  }
}
