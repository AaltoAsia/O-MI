

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
import CallbackHandlers._
import database._

trait CancelHandler extends OmiRequestHandler{
  def subscriptionManager : ActorRef
  handler{
    case cancel: CancelRequest => handleCancel(cancel)
  }

  /** Method for handling CancelRequest.
    * @param cancel request
    * @return (xml response, HTTP status code) wrapped in a Future
    */
  def handleCancel(cancel: CancelRequest): Future[NodeSeq] = {
    log.debug("Handling cancel.")
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    var returnCode = 200
    val jobs = Future.sequence(cancel.requestID.map { id =>
      (subscriptionManager ? RemoveSubscription(id)).mapTo[Boolean].map( res =>
        if(res){
          Results.success
        }else{
          Results.notFoundSub
        }
      ).recoverWith{
        case e => {
          val error = "Error when trying to cancel subcription: "
          log.error(e, error)
          Future.successful(Results.internalError(error + e.toString))
        }
      }
    })

    jobs.map( res =>
    (
      xmlFromResults(
        1.0,
      res.toSeq: _*
        )))
  }
}
