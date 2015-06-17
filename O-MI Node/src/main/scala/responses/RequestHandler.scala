package responses

import types._
import types.OmiTypes._
import types.OdfTypes._
import types.Path
import parsing.xmlGen.xmlTypes
import parsing.xmlGen.scalaxb
import database._
import agentSystem.InputPusher
import CallbackHandlers.sendCallback

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingAdapter
import akka.util.Timeout
import akka.pattern.ask

import scala.xml.NodeSeq
import scala.collection.JavaConversions.iterableAsScalaIterable
import java.sql.Timestamp
import java.util.Date
import xml._
import scala.collection.mutable.Buffer

/** Class for handling all request.
  *
  **/
class RequestHandler(val subscriptionHandler: ActorRef)(implicit val dbConnection: DB) {

  import scala.concurrent.ExecutionContext.Implicits.global
  private def date = new Date()
  /** Main interface for hanling O-MI request
    *
    * @param request request is O-MI request to be handled
    **/
  def handleRequest(request: OmiRequest)(implicit ec: ExecutionContext): (NodeSeq, Int) = {
    request match {
      case sub : SubscriptionRequest => runGeneration(sub)
      case _ if (request.callback.nonEmpty) => {
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
      }
      case _ =>{
        runGeneration(request)
      } 
    }
  }

  /** Method for runnig response generation. Handles tiemout etc. upper level failures.
    *
    * @param request request is O-MI request to be handled
    **/
  def runGeneration(request: OmiRequest)(implicit ec: ExecutionContext): (NodeSeq, Int) = {
    val timeout = if (request.ttl > 0) request.ttl.seconds else Duration.Inf

    val responseFuture = Future{xmlFromRequest(request)}

    Try {
      Await.result(responseFuture, timeout)
    } match {
      case Success((xml: NodeSeq, code: Int)) => (xml, code)

      case Failure(e: TimeoutException) =>
        (
          xmlFromResults(
            1.0,
            Result.simpleResult("500", Some("TTL timeout, consider increasing TTL or is the server overloaded?"))
        ),
        500
      )
      case Failure(e: RequestHandlingException) => 
      actionOnInternalError(e)
      (
        xmlFromResults(
          1.0,
          Result.simpleResult(e.errorCode.toString, Some( e.getMessage()))
        ),
        501
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

  /** Method to be called for handling internal server error, logging and stacktrace.
    *
    * @param request request is O-MI request to be handled
    **/
  def actionOnInternalError: Throwable => Unit = { error =>
    println("[ERROR] Internal Server error:")
    error.printStackTrace()
  }
  
  /** Generates xml from request, match request and call specific method for generation.
    *
    * @param request request is O-MI request to be handled
    * @return Tuple containing xml message and HTTP status code
    **/
  def xmlFromRequest(request: OmiRequest) : (NodeSeq, Int) = request match {
    case read : ReadRequest =>{
      handleRead(read)
    }
    case poll : PollRequest =>{
      //When sender wants to poll data of some subscription
      handlePoll(poll)
    }
    case subscription : SubscriptionRequest =>{
      //When subscription is created
      handleSubscription(subscription)
    }
    case write : WriteRequest =>{
      InputPusher.handleObjects(write.odf.objects)
      ( success, 200 )
    }
    case response : ResponseRequest =>{
      ( notImplemented, 505 )
    }
    case cancel : CancelRequest =>{
      handleCancel(cancel)
    }
    case subdata : SubDataRequest => {
      //SHOULD NOT BE CALLED THERE 
      //Used whan O-MI Node need to send data on interval
      ( xmlFromResults( 1.0, Result.simpleResult("500", Some( "Subdata reuqeust shouldn't be send to RequestHandler.." ) ) ), 500)
    }
    case _ =>{
      ( xmlFromResults( 1.0, Result.simpleResult("500", Some( "Unknown request." ) ) ), 500)
    }
  }

  private val scope =scalaxb.toScope(
    None -> "odf.xsd",
    Some("omi") -> "omi.xsd",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )
  def wrapResultsToResponseAndEnvelope(ttl: Double, results: xmlTypes.RequestResultType* ) = {
    OmiGenerator.omiEnvelope( ttl, "response", OmiGenerator.omiResponse( results:_* ) )
  }

  def xmlFromResults(ttl: Double, results: xmlTypes.RequestResultType* ) = {
    xmlMsg( wrapResultsToResponseAndEnvelope(ttl,results:_*) )
  }

  /** Generates xml from xmlTypes
    *
    * @param envelope xmlType for OmiEnvelope containing response
    * @return xml.NodeSeq containing response
    **/
  def xmlMsg( envelope: xmlTypes.OmiEnvelope) = {
    scalaxb.toXML[xmlTypes.OmiEnvelope]( envelope, Some("omi"), Some("omiEnvelope"), scope )
  }


  def handleRead(read: ReadRequest) : (NodeSeq, Int) = {
      val objectsO : Option[OdfObjects] = dbConnection.getNBetween(getLeafs(read.odf), read.begin, read.end, read.newest, read.oldest )

      objectsO match {
        case Some(objects) =>
          (
            xmlFromResults(
              1.0,
              Result.readResult(objects)
            ),
            200
          )
        case None =>
          ( xmlFromResults(
          1.0, Result.notFound
          ), 404)
      }
  }

  def handlePoll( poll : PollRequest ) : (NodeSeq, Int ) ={
    val time = date.getTime
    val results =
      poll.requestIds.map{ id => 

        val objectsO : Option[OdfObjects] = dbConnection.getPollData(id,new Timestamp(time))

        objectsO match {
          case Some(objects) =>
            Result.pollResult( id.toString, objects ) 
          case None =>
            Result.notFound
        }
      }
    val returnTuple = (
      xmlFromResults(
        1.0,
        results.toSeq : _*
      ),
      200
    )

    returnTuple
  } 

  def handleSubscription( subscription: SubscriptionRequest ) : ( NodeSeq, Int) ={
    implicit val timeout= Timeout( 10.seconds ) // NOTE: ttl will timeout from elsewhere
    val subFuture = subscriptionHandler ? NewSubscription(subscription)
    var returnCode = 200
    (
      xmlFromResults(
        1.0,
        Await.result(subFuture, Duration.Inf) match {
          case -1 => 
            returnCode = 501
            Result.internalError("Internal server error when trying to create subscription")
          case id: Int =>
            Result.subscriptionResult(id.toString) 
        }
      ),
      returnCode
    )
  }

  def handleCancel(cancel: CancelRequest): (NodeSeq, Int) = {
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
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
              case true => Result.success
              case false =>{ 
                returnCode = 404
                Result.simpleResult("404", Some("TTL ended or no such subcription found."))
              }
              case _ =>{
                returnCode = 501
                Result.internalError()
              }
            }
            case Failure(n: NumberFormatException) =>{
              returnCode = 400
              Result.simpleResult(returnCode.toString, Some("Invalid requestId"))
            }
            case Failure(e : RequestHandlingException) =>{ 
              returnCode = e.errorCode
              Result.simpleResult(returnCode.toString, Some(e.msg))
            }
            case Failure(e) =>{
              returnCode = 501
              Result.internalError("Internal server error, when trying to cancel subscription: " + e.toString)
            }
        }.toSeq:_*
      ),
      returnCode
    )
  }

  def handleSubData( subdata : SubDataRequest ) = {
      val objectsO : Option[OdfObjects] = dbConnection.getSubData(subdata.sub.id)

      objectsO match {
        case Some(objects) =>
          ( xmlFromResults(
          1.0, Result.pollResult(subdata.sub.id.toString,objects)
          ), 200)

        case None =>
          ( xmlFromResults(
          1.0, Result.notFound
          ), 404)
      }
  
  }

  def notFound = xmlFromResults(
    1.0,
    Result.notFound
  )
  def success = xmlFromResults(
    1.0,
    Result.success
  )
  def unauthorized = xmlFromResults(
    1.0,
    Result.unauthorized
  )
  def notImplemented = xmlFromResults(
    1.0,
    Result.notImplemented 
  )
  def parseError(err: ParseError*) =
    xmlFromResults(
      1.0,
      Result.simpleResult("400", Some(err.map { e => e.msg }.mkString("\n"))))
  def internalError(e: Throwable) =
    xmlFromResults(
      1.0,
      Result.simpleResult("501", Some("Internal server error: " + e.getMessage())))
  def timeOutError = xmlFromResults(
    1.0,
    Result.simpleResult("500", Some("TTL timeout, consider increasing TTL or is the server overloaded?")))
  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def generateODFREST(orgPath: Path)(implicit dbConnection: DB): Option[Either[String, xml.Node]] = {

    // Removes "/value" from the end; Returns (normalizedPath, isValueQuery)
    def restNormalizePath(path: Path): (Path, Int) = path.lastOption match {
      case Some("value") => (path.init, 1)
      case Some("MetaData") => (path.init, 2)
      case _ => (path, 0)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val (path, wasValue) = restNormalizePath(orgPath)

    dbConnection.get(path) match {
      case Some(infoitem: OdfInfoItem) =>

        if (wasValue == 1){
          Some( Left(
            infoitem.values.headOption match{
              case Some(value: OdfValue) => value.value
              case None => "NO VALUE FOUND"
            }
          ) )
        }else if (wasValue == 2){
          val metaDataO = dbConnection.getMetaData(path)
          metaDataO match {
            case None =>
              Some(Left("No metadata found."))
            case Some(metaData) =>
              Some(Right(XML.loadString(metaData.data)))
          }

        }else{
          return Some( Right(
            scalaxb.toXML[xmlTypes.InfoItemType]( OdfInfoItemAsInfoItemType( infoitem ), Some("odf"), Some("InfoItem"), scope ).headOption.getOrElse(
              <error>Could not create from OdfInfoItem </error>
            )
          ) )
        }
      case Some(odfObj: OdfObject) =>
           val xmlReturn = scalaxb.toXML[xmlTypes.ObjectType]( OdfObjectAsObjectType( odfObj ), Some("odf"), Some("Object"), scope ).headOption.getOrElse(
          <error>Could not create from OdfObject </error>
        )
        Some(Right(xmlReturn))

      case Some(odfObj: OdfObjects) =>
           val xmlReturn = scalaxb.toXML[xmlTypes.ObjectsType]( OdfObjectsAsObjectsType( odfObj ), Some("odf"), Some("Objects"), scope ).headOption.getOrElse(
          <error>Could not create from OdfObjects </error>
        )
        Some(Right(xmlReturn))

      case None => None
    }
  }
  case class RequestHandlingException(errorCode: Int, msg: String) extends Exception(msg)
}
