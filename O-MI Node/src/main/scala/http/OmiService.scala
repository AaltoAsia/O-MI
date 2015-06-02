package http

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingAdapter
import spray.routing._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import MediaTypes._
import java.net.InetSocketAddress
import java.net.InetAddress

import responses._
import parsing._
import PermissionCheck._
import parsing.Types._
import parsing.Types.OmiTypes._
import database._

import xml._
import scala.collection.JavaConverters._
import scala.collection.JavaConversions.iterableAsScalaIterable

/**
 * Actor that handles incoming http messages
 * @param requestHandler ActorRef that is used in subscription handling
 */
class OmiServiceActor(reqHandler: RequestHandler) extends Actor with ActorLogging with OmiService {

  /**
   * the HttpService trait defines only one abstract member, which
   * connects the services environment to the enclosing actor or test
   */
  def actorRefFactory = context

  //Used for O-MI subscriptions
  val requestHandler = reqHandler

  /**
   * this actor only runs our route, but you could add
   * other things here, like request stream processing
   * or timeout handling
   */
  def receive = runRoute(myRoute)

  implicit val dbobject = new SQLiteConnection

}

/**
 * this trait defines our service behavior independently from the service actor
 */
trait OmiService extends HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global
  def log: LoggingAdapter
  val requestHandler: RequestHandler

  implicit val dbobject: DB

    //Handles CORS allow-origin seems to be enough
  private def corsHeaders =
    respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))

  //Get the files from the html directory; http://localhost:8080/html/form.html
  val staticHtml =
    pathPrefix("html") {
      getFromDirectory("html")
    }

  // should be removed?
  val helloWorld =
    get {
      path("") { // Root
        corsHeaders {
          respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default
            complete {
              <html>
                <body>
                  <h1>Say hello to <i>O-MI Node service</i>!</h1>
                  <a href="/Objects">Url Data Discovery /Objects: Root of the hierarchy</a>
                  <p>
                    With url data discovery you can discover or request Objects,
                     InfoItems and values with HTTP Get request by giving some existing
                     path to the O-DF xml hierarchy.
                  </p>
                  <a href="/html/form.html">O-MI Test Client WebApp</a>
                </body>
              </html>
            }
          }
        }
      }
    }

  val getDataDiscovery =
    get {
      path(Rest) { pathStr =>
        corsHeaders {
          val path = Path(pathStr)
          Read.generateODFREST(path) match {
            case Some(Left(value)) =>
              respondWithMediaType(`text/plain`) {
                complete(value)
              }
            case Some(Right(xmlData)) =>
              respondWithMediaType(`text/xml`) {
                complete(xmlData)
              }
            case None =>
              log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$path]")
              respondWithMediaType(`text/xml`) {
                complete(404, <error>No object found</error>)
              }
          }
        }
      }
    }

  import OMISubscription._
  // XXX: lazy maybe fixes bug

  /* Receives HTTP-POST directed to root (localhost:8080) */
  val getXMLResponse = post { // Handle POST requests from the client
    clientIP { ip =>
      path("") {
        corsHeaders {
           entity(as[NodeSeq]) { xml =>
            val omi = OmiParser.parse(xml.toString)

            if (omi.isRight) {
              val request = omi.right.get.head
              respondWithMediaType(`text/xml`) {
                val (response,returnCode) = request match {
                  case write : WriteRequest => 
                    if(ip.toOption.nonEmpty && hasPermission(ip.toOption.get))
                      requestHandler.handleRequest(request)
                    else
                      (requestHandler.unauthorized, 401)
                  case req : OmiRequest => 
                      requestHandler.handleRequest(request)
                }
                complete(returnCode, response)
              }
            } else {
              val errors = omi.left.get
              //Errors found
              log.warning("Parse Errors: {}", errors.mkString(", "))
              complete(400,
              ErrorResponse.parseErrorResponse(errors))
            }
          }
        }
      }
    }
  }

  // Combine all handlers
  val myRoute = helloWorld ~ staticHtml ~ getDataDiscovery ~ getXMLResponse

}
