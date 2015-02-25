package http

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import spray.routing._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import MediaTypes._
import responses._

import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent._
import parsing._
import parsing.Types._
import xml._
import sensordata.SensorData

class OmiServiceActor extends Actor with ActorLogging with OmiService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

}

// this trait defines our service behavior independently from the service actor
trait OmiService extends HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global
  def log: LoggingAdapter

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


  /* Receives HTTP-POST directed to root (localhost:8080) */
  val getXMLResponse = post { // Handle POST requests from the client
    path("") {
      corsHeaders {
        entity(as[NodeSeq]) { xml =>
          val omi = OmiParser.parse(xml.toString)
          val requests = omi.filter {
            case ParseError(_) => false
            case _ => true
          }
          val errors = omi.filter {
            case ParseError(_) => true
            case _ => false
          }

          if (errors.isEmpty) {
            respondWithMediaType(`text/xml`) {
              
              var returnStatus = 200
              //TODO: Currently sending multiple omi:omiEnvelope
              val result = requests.map {
                case oneTimeRead: OneTimeRead =>
                  log.debug("read")
                  log.debug("Begin: " + oneTimeRead.begin + ", End: " + oneTimeRead.end)
                  val response = Future{ Read.OMIReadResponse(oneTimeRead) }
                  Await.result(response, oneTimeRead.ttl.toDouble  seconds).asInstanceOf[NodeSeq]
                case write: Write => 
                  log.debug("write") 
                  ErrorResponse.notImplemented
                  returnStatus = 501
                case subscription: Subscription => 
                  log.debug("sub") 
                  ErrorResponse.notImplemented
                  returnStatus = 501
                case cancel: Cancel =>
                  log.debug("cancel")
                  ErrorResponse.notImplemented
                  returnStatus = 501
                case _ => log.warning("Unknown request")
                  returnStatus = 400
              }.mkString("\n")
              complete(returnStatus, result)
            }
          } else {
            //Error found
            complete (400,{
              log.error("ERROR")
              println(errors)
              ErrorResponse.parseErrorResponse(errors.map{err => err.asInstanceOf[ParseError]})  
            })
          }
        }
      }
    }
  }

  // Combine all handlers
  val myRoute = helloWorld ~ staticHtml ~ getDataDiscovery ~ getXMLResponse
}
