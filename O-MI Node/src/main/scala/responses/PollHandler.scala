package responses

import parsing.xmlGen.xmlTypes.RequestResultType

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions.{ iterableAsScalaIterable, asJavaIterable}
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

trait PollHandler extends OmiRequestHandler{
  def subscriptionManager : ActorRef
  handler{
    case poll: PollRequest => handlePoll(poll)
  }

  /** Method for handling PollRequest.
    * @param poll request
    * @return (xml response, HTTP status code)
    */
  def handlePoll(poll: PollRequest): Future[NodeSeq] = {
    val ttl = handleTTL(poll.ttl)
    implicit val timeout = Timeout(ttl) 
    val time = date.getTime
    val resultsFut =
      Future.sequence(poll.requestIDs.map { id =>

      val objectsF: Future[ Any /* Option[OdfObjects] */ ] = (subscriptionManager ? PollSubscription(id)).mapTo[Future[Option[OdfObjects]]].flatMap(n=>n)
      objectsF.recoverWith{case e => Future.failed(new RuntimeException(
        s"Error when trying to poll subscription: ${e.getMessage}"))}

      objectsF.map(res => res match {
        case Some(objects: OdfObjects) =>
          Results.poll(id.toString, objects)
        case None =>
          Results.notFoundSub(id.toString)
        //case Failure(e) =>
        //  throw new RuntimeException(
        //    s"Error when trying to poll subscription: ${e.getMessage}")
      })
    })
    val returnTuple = resultsFut.map(results =>
      xmlFromResults(
        1.0,
        results.toSeq: _*)
    )

    returnTuple
  }
}
