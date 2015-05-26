package responses

import parsing.Types.OmiTypes._
import parsing.Types.OdfTypes._
import parsing.xmlGen
import parsing.xmlGen.scalaxb
import database._
import CallbackHandlers.sendCallback

import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._
import scala.concurrent.{Future, Await, ExecutionContext, TimeoutException}

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingAdapter
import akka.util.Timeout
import akka.pattern.ask

import scala.xml.NodeSeq
import scala.collection.JavaConversions.iterableAsScalaIterable
import java.sql.Timestamp
import java.util.Date

class RequestHandler(val  subscriptionHandler: ActorRef)(implicit val dbConnection: DB) {
  
  import scala.concurrent.ExecutionContext.Implicits.global
  private def date = new Date()

  def handleRequest(request: OmiRequest)(implicit ec: ExecutionContext): (NodeSeq, Int) = {
    if (request.callback.nonEmpty){
      // TODO: Can't cancel this callback
      Future{ runGeneration(request) } map { case (xml : NodeSeq, code: Int) =>
        sendCallback(request.callback.get.toString, xml)
      }
      (
        xmlFromResults(
          1.0,
          Result.simpleResult("200", Some("OK, callback job started"))
        ),
        200
      )

    } else {
      runGeneration(request)
    }
  }

  def runGeneration(request: OmiRequest)(implicit ec: ExecutionContext): (NodeSeq, Int) = {

    val timeout = if (request.ttl > 0) request.ttl.seconds else Duration.Inf

    val responseFuture = Future{xmlFromRequest(request)}

    Try {
      Await.result(responseFuture, timeout)

    } match {
      case Success((xml: NodeSeq, code : Int)) => (xml, code)

      case Failure(e: TimeoutException) => 
      (
        xmlFromResults(
          1.0,
          Result.simpleResult("500", Some("TTL timeout, consider increasing TTL or is the server overloaded?"))
        ),
        500
      )
      case Failure(e) => 
        actionOnInternalError(e)
        (
          xmlFromResults(
            1.0,
            Result.simpleResult("501", Some( "Internal server error: " + e.getMessage()))
          ),
          501
        )
    }
  }

  def actionOnInternalError: Throwable => Unit = { _ => /*noop*/ }

  def xmlFromRequest(request: OmiRequest) : (NodeSeq, Int) = request match {
    case read : ReadRequest =>
      (
        xmlFromResults(
          1.0,
          Result.readResult(getSensors(read))
        ),
        200
      )

    case poll : PollRequest =>
      (
        xmlFromResults(
          1.0,
          poll.requestIds.map{
            id => 
              val sensors = dbConnection.getSubData(id)
              Result.pollResult(id.toString,sensors) 
          }.toSeq : _*
        ),
        200
      )

    case subscription : SubscriptionRequest =>
      implicit val timeout= Timeout( 10.seconds ) // NOTE: ttl will timeout from elsewhere
      val subFuture = subscriptionHandler ? NewSubscription(subscription)
      var returnCode = 200
      println("Waitting for results... ")
      (
        xmlFromResults(
          1.0,
          Await.result(subFuture, Duration.Inf) match {
            case -1 => 
              returnCode = 501
              Result.simpleResult("501", Some( "Internal server error when trying to create subscription."))
            case id: Int =>
              Result.subscriptionResult(id.toString) 
          }
        ),
        returnCode
      )
    case write : WriteRequest =>
    (
      xmlFromResults(
        1.0,
        Result.simpleResult("505", Some( "Not implemented." ) ) 
      ),
      505
    )
    case response : ResponseRequest =>
    (
      xmlFromResults(
        1.0,
        Result.simpleResult("505", Some( "Not implemented." ) ) 
      ),
      505
    )

    case cancel : CancelRequest =>
     handleCancel(cancel)

    case subdata : SubDataRequest =>
      lazy val sensors = dbConnection.getSubData(subdata.sub.id)
      (
        xmlFromResults(
          1.0,
          Result.pollResult(subdata.sub.id.toString,sensors) 
        ),
        200
      )

    case _ => 
    (
      xmlFromResults(
        1.0,
        Result.simpleResult("500", Some( "Unknown request." ) ) 
      ),
      500
    )

  } 

  private val scope =scalaxb.toScope(
    None -> "odf.xsd",
    Some("omi") -> "omi.xsd",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )
  
  def getSensors(request: OdfRequest): Array[DBSensor] = {
    getPaths(request).map{path => dbConnection.get(path) }.collect{ case Some(sensor:DBSensor) => sensor }.toArray
  }

  def getPaths(request: OdfRequest) = getInfoItems( request.odf.objects ).map( info => info.path )

  
  def getInfoItems( objects: Iterable[OdfObject] ) : Iterable[OdfInfoItem] = {
    objects.flatten{
        obj => 
        obj.infoItems ++ getInfoItems(obj.objects)
    }
  }

  def wrapResultsToResponseAndEnvelope(ttl: Double, results: xmlGen.RequestResultType* ) = {
    OmiGenerator.omiEnvelope(
      ttl,
      "response",
      OmiGenerator.omiResponse(
        results:_*
      )
    )
  }

  def xmlFromResults(ttl: Double, results: xmlGen.RequestResultType* ) = {
    xmlMsg(
      wrapResultsToResponseAndEnvelope(ttl,results:_*)
    )
  }

  def xmlMsg( envelope: xmlGen.OmiEnvelope) = {
      scalaxb.toXML[xmlGen.OmiEnvelope](
        envelope,      
        Some("omi"), Some("omiEnvelope"), scope
      )
  }

  def handleCancel( cancel: CancelRequest ) : (NodeSeq, Int) = {
    implicit val timeout= Timeout( 10.seconds ) // NOTE: ttl will timeout from elsewhere
    var returnCode = 200
    val jobs = cancel.requestId.map { id =>
      Try {
        val parsedId = id.toInt
        subscriptionHandler ? RemoveSubscription(parsedId)
      }
    }
    (
      xmlFromResults(
        1.0,
        jobs.map {
          case Success(removeFuture) =>
            // NOTE: ttl will timeout from OmiService
            Await.result(removeFuture, Duration.Inf) match {
              case true => 
                  Result.successResult
              case false => 
                  returnCode = 404
                  Result.simpleResult("404", Some( "Subscription with requestId not found"))
              case _ =>
                  returnCode = 501
                  Result.simpleResult("501", Some( "Internal server error"))
            }
          case Failure(n: NumberFormatException) =>
            returnCode = 400
            Result.simpleResult("400", Some("Invalid requestId"))
          case Failure(e) =>
            returnCode = 501
            Result.simpleResult("501", Some( "Internal server error: " + e.getMessage()))

        }.toSeq:_*
      ),
      returnCode
    )
  }

  def notImplemented = xmlFromResults(
        1.0,
        Result.simpleResult("501", Some( "Not implemented." ) ) 
      )

  def internalError(e: Throwable) =
        xmlFromResults(
          1.0,
          Result.simpleResult("501", Some( "Internal server error: " + e.getMessage()))
        )
  def timeOutError = xmlFromResults(
          1.0,
          Result.simpleResult("500", Some("TTL timeout, consider increasing TTL or is the server overloaded?"))
        )
}
