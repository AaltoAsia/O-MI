/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package responses

import types._
import OmiTypes._
import OdfTypes._
import parsing.xmlGen.{ xmlTypes, scalaxb }
import database.DB
import agentSystem.InputPusher
import CallbackHandlers._

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.util.Timeout
import akka.pattern.ask

import scala.collection.breakOut
import scala.collection.JavaConversions.iterableAsScalaIterable
import java.sql.Timestamp
import java.util.Date
import java.net.{ URL, InetAddress, UnknownHostException }

import scala.xml.{ NodeSeq, XML }

import scala.concurrent.ExecutionContext.Implicits.global
import OmiGenerator._
import parsing.xmlGen.defaultScope
/**
 * Actor for handling all request.
 *
 */
class RequestHandler(val subscriptionHandler: ActorRef)(implicit val dbConnection: DB) {

  import http.Boot.system.log
  private[this] def date = new Date()

  /**
   * Main interface for hanling O-MI request
   *
   * @param request request is O-MI request to be handled
   */
  def handleRequest(request: OmiRequest)(implicit ec: ExecutionContext): (NodeSeq, Int) = {

    def checkCallback(address: String) = Try {
      val url = new URL(address)
      val addr = InetAddress.getByName(url.getHost)
      val protocol = url.getProtocol()
      if (protocol != "http" && protocol != "https")
        throw new java.net.ProtocolException(s"Unsupported protocol: $protocol")

    }

    def handleSubDataRequest(subdata: SubDataRequest, address: String) = {
      val sub = subdata.sub
      val interval = sub.interval
      val callbackAddr = address
      val (xmlMsg, returnCode) = runGeneration(subdata)
      log.info(s"Sending in progress; Subscription subId:${sub.id} addr:$callbackAddr interval:$interval")

      def failed(reason: String) =
        log.warning(
          s"Callback failed; subscription id:${sub.id} interval:$interval  reason: $reason")

      sendCallback(callbackAddr, xmlMsg) onComplete {
        case Success(CallbackSuccess) =>
          log.info(s"Callback sent; subscription id:${sub.id} addr:$callbackAddr interval:$interval")

        case Success(fail: CallbackFailure) =>
          failed(fail.toString)
        case Failure(e) =>
          failed(e.getMessage)
      }
      (success, 200) //DUMMY
    }

    request.callback match {
      
      case Some(address) => {
        checkCallback(address).map { x =>
          request match {
            case sub: SubscriptionRequest => runGeneration(sub)
            case subdata: SubDataRequest => {
              handleSubDataRequest(subdata, address)
            }
            case _ => {
              // TODO: Can't cancel this callback
              Future { runGeneration(request) } map {
                case (xml: NodeSeq, code: Int) =>
                  sendCallback(address, xml)
              }
              (
                xmlFromResults(
                  1.0,
                  Results.simple("200", Some("OK, callback job started"))),
                  200)
            }
          }
        } match {
          case Success(res)                               => res
          case Failure(e: java.net.MalformedURLException) => (invalidCallback(e.getMessage), 200)
          case Failure(e: UnknownHostException)           => (invalidCallback("Unknown host: " + e.getMessage), 200)
          case Failure(e: SecurityException)              => (invalidCallback("Unauthorized " + e.getMessage), 200)
          case Failure(e: java.net.ProtocolException)     => (invalidCallback(e.getMessage), 200)
          case Failure(t)                                 => throw t
        }

      }
      case None => {
        request match {
          case _ => runGeneration(request)
        }
      }
    }
  }

  /**
   * Method for runnig response generation. Handles tiemout etc. upper level failures.
   *
   * @param request request is O-MI request to be handled
   */
  def runGeneration(request: OmiRequest)(implicit ec: ExecutionContext): (NodeSeq, Int) = {
    val responseFuture = Future { xmlFromRequest(request) }

    Try {
      Await.result(responseFuture, request.ttl)
    } match {
      case Success((xml: NodeSeq, code: Int)) => (xml, code)
      case Success(a)                         => a //TODO does this fix default case not specified problem?

      case Failure(e: TimeoutException) =>
        (
          xmlFromResults(
            1.0,
            Results.simple("500", Some("TTL timeout, consider increasing TTL or is the server overloaded?"))),
            500)
      case Failure(e: IllegalArgumentException) =>
        (invalidRequest(e.getMessage), 400)

      case Failure(e: RequestHandlingException) =>
        actionOnInternalError(e)
        (
          xmlFromResults(
            1.0,
            Results.simple(e.errorCode.toString, Some(e.getMessage()))),
            501)
      case Failure(e) =>
        actionOnInternalError(e)
        (
          xmlFromResults(
            1.0,
            Results.simple("501", Some("Internal server error: " + e.getMessage()))),
            501)
    }
  }

  /**
   * Method to be called for handling internal server error, logging and stacktrace.
   *
   * @param request request is O-MI request to be handled
   */
  def actionOnInternalError: Throwable => Unit = { error =>
    println("[ERROR] Internal Server error:")
    error.printStackTrace()
  }

  /**
   * Generates xml from request, match request and call specific method for generation.
   *
   * @param request request is O-MI request to be handled
   * @return Tuple containing xml message and HTTP status code
   */
  def xmlFromRequest(request: OmiRequest): (NodeSeq, Int) = request match {
    case read: ReadRequest => {
      handleRead(read)
    }
    case poll: PollRequest => {
      //When sender wants to poll data of some subscription
      handlePoll(poll)
    }
    case subscription: SubscriptionRequest => {
      //When subscription is created
      handleSubscription(subscription)
    }
    case write: WriteRequest => {
      InputPusher.handleObjects(write.odf.objects)
      //XXX:
      (success, 200)
    }
    case response: ResponseRequest => {
      log.debug("Response received.")
      (xmlFromResults(
        1.0,
        response.results.map{
          result => 
          result.odf match {
            case Some(odf) =>
              InputPusher.handleObjects(odf.objects)
            case None =>//noop?
          }
          Results.success
        }.toSeq:_*
      ),
      200
      )
    }
    case cancel: CancelRequest => {
      handleCancel(cancel)
    }
    case subdata: SubDataRequest => {
      handleSubData(subdata)
    }
    case _ => {
      (xmlFromResults(1.0, Results.simple("500", Some("Unknown request."))), 500)
    }
  }

  /** Method for handling ReadRequest.
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): (NodeSeq, Int) = {
    val objectsO: Option[OdfObjects] = dbConnection.getNBetween(getLeafs(read.odf), read.begin, read.end, read.newest, read.oldest)

    objectsO match {
      case Some(objects) =>
        val found = Results.read(objects)
        val requestsPaths = getLeafs(read.odf).map { _.path }
        val foundOdfAsPaths = getLeafs(objects).flatMap { _.path.getParentsAndSelf }.toSet
        val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
        var results = Seq(found)
        if (notFound.nonEmpty)
          results ++= Seq(Results.simple("404",
            Some("Could not find the following elements from the database:\n" + notFound.mkString("\n"))))

        (
          xmlFromResults(
            1.0,
            results: _*),
            200)
      case None =>
        (xmlFromResults(
          1.0, Results.notFound), 404)
    }
  }

  /** Method for handling PollRequest.
    * @param poll request
    * @return (xml response, HTTP status code)
    */
  def handlePoll(poll: PollRequest): (NodeSeq, Int) = {
    val time = date.getTime
    val results =
      poll.requestIDs.map { id =>

        val objectsO: Option[OdfObjects] = dbConnection.getPollData(id, new Timestamp(time))

        objectsO match {
          case Some(objects) =>
            Results.poll(id.toString, objects)
          case None =>
            Results.notFoundSub(id.toString)
        }
      }
    val returnTuple = (
      xmlFromResults(
        1.0,
        results.toSeq: _*),
        if (results.exists(_.returnValue.returnCode == "404")) 404 else 200)

    returnTuple
  }

  /** Method for handling SubscriptionRequest.
    * @param subscription request
    * @return (xml response, HTTP status code)
    */
  def handleSubscription(subscription: SubscriptionRequest): (NodeSeq, Int) = {
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    val subFuture = subscriptionHandler ? NewSubscription(subscription)
    val (response, returnCode) =
      Await.result(subFuture, Duration.Inf) match {
        case Failure(e: IllegalArgumentException) =>
          (Results.invalidRequest(e.getMessage), 400)
        case Failure(t) =>
          (Results.internalError(
            s"Internal server error when trying to create subscription: ${t.getMessage}"),
            500)
        case Success(id: Long) =>
          (Results.subscription(id.toString), 200)
        case Success(received) =>
          (Results.internalError(
            s"Internal server error: Invalid response type from SubscriptionHandler: ${received.getClass().getName}"),
            500)
      }
    (
      xmlFromResults(
        1.0,
        response),
        returnCode)
  }

  /** Method for handling CancelRequest.
    * @param cancel request
    * @return (xml response, HTTP status code)
    */
  def handleCancel(cancel: CancelRequest): (NodeSeq, Int) = {
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    var returnCode = 200
    val jobs = cancel.requestID.map { id =>
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
              case true => Results.success
              case false => {
                returnCode = 404
                Results.notFoundSub
              }
              case _ => {
                returnCode = 501
                Results.internalError()
              }
            }
          case Failure(n: NumberFormatException) => {
            returnCode = 400
            Results.simple(returnCode.toString, Some("Invalid requestID"))
          }
          case Failure(e: RequestHandlingException) => {
            returnCode = e.errorCode
            Results.simple(returnCode.toString, Some(e.msg))
          }
          case Failure(e) => {
            returnCode = 501
            Results.internalError("Internal server error, when trying to cancel subscription: " + e.toString)
          }
        }(breakOut): _*),
        returnCode)
  }

  def handleSubData(subdata: SubDataRequest) = {
    val objectsO: Option[OdfObjects] = dbConnection.getSubData(subdata.sub.id)

    objectsO match {
      case Some(objects) =>
        (xmlFromResults(
          1.0, Results.poll(subdata.sub.id.toString, objects)), 200)

      case None =>
        (xmlFromResults(
          1.0, Results.notFound), 404)
    }

  }

  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def generateODFREST(orgPath: Path): Option[Either[String, xml.Node]] = {

    // Removes "/value" from the end; Returns (normalizedPath, isValueQuery)
    def restNormalizePath(path: Path): (Path, Option[String]) = path.lastOption match {
      case attr @ Some("value")    => (path.init, attr)
      case attr @ Some("MetaData") => (path.init, attr)
      case _                       => (path, None)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val (path, wasValue) = restNormalizePath(orgPath)

    dbConnection.get(path) match {
      case Some(infoitem: OdfInfoItem) =>

        wasValue match {
          case Some("value") =>
            Some(Left(
              infoitem.values.headOption match {
                case Some(value: OdfValue) => value.value
                case None                  => "NO VALUE FOUND"
              }))
          case Some("MetaData") =>
            val metaDataO = dbConnection.getMetaData(path)
            metaDataO match {
              case None =>
                Some(Left("No metadata found."))
              case Some(metaData) =>
                Some(Right(XML.loadString(metaData.data)))
            }

          case _ =>
            return Some(Right(
              scalaxb.toXML[xmlTypes.InfoItemType](infoitem.asInfoItemType, Some("odf"), Some("InfoItem"), defaultScope).headOption.getOrElse(
                <error>Could not create from OdfInfoItem </error>)))
        }
      case Some(odfObj: OdfObject) =>
        val xmlReturn = scalaxb.toXML[xmlTypes.ObjectType](odfObj.asObjectType, Some("odf"), Some("Object"), defaultScope).headOption.getOrElse(
          <error>Could not create from OdfObject </error>)
        Some(Right(xmlReturn))

      case Some(odfObj: OdfObjects) =>
        val xmlReturn = scalaxb.toXML[xmlTypes.ObjectsType](odfObj.asObjectsType, Some("odf"), Some("Objects"), defaultScope).headOption.getOrElse(
          <error>Could not create from OdfObjects </error>)
        Some(Right(xmlReturn))

      case None => None
    }
  }
  case class RequestHandlingException(errorCode: Int, msg: String) extends Exception(msg)
}
