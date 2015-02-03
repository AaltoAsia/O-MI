package http

import akka.actor.Actor
import akka.event.LoggingAdapter
import akka.actor.ActorLogging
import spray.routing._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import MediaTypes._
import responses._

import parsing._
import xml._
import cors._

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
trait OmiService extends HttpService with CORSDirectives
  with DefaultCORSDirectives {
  def log: LoggingAdapter

  //Get the files from the html directory; http://localhost:8080/html/form.html
  val staticHtml =
    pathPrefix("html") {
      getFromDirectory("html")
    }

  // should be removed?
  val helloWorld =
    path("") { // Root
      get {
        respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) { //Handles CORS
          respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default
            complete {
              <html>
                <body>
                  <h1>Say hello to <i>O-MI Node service</i>!</h1>
                  <a href="/Objects">Url Data Discovery /Objects: Root of the hierarchy</a>
                  <p>With url data discovery you can discover or request Objects,
                     InfoItems and values with HTTP Get request by giving some existing
                     path to the O-DF xml hierarchy.</p>
                  <a href="/html/form.html">O-MI Test Client WebApp</a>
                </body>
              </html>
            }
          }
        }
      }
    }

  val getDataDiscovery =
    path(Rest) { path =>
      get {
        respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
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
              log.debug(s"Url Discovery fail: $path")
              respondWithMediaType(`text/xml`) {
                complete(404, <error>No object found</error>)
              }
          }
        }
      }
    }

  /*
  val getXMLResponse = entity(as[NodeSeq]) { xml => 
    val omi = OmiParser.parse(xml)
    val requests = omi.filter{
      case ParseError(_) => false
      case _ => true
    }
    val errors = omi.filter{ 
      case ParseError(_) => true
      case _ => false
    }
    if(errors.isEmpty) {
      complete{
        requests.map{
          case oneTimeRead: OneTimeRead => 
            log.warning("Not yet impelemented")
            "Not yet implemented"
          case write: Write =>
            log.warning("Not yet impelemented")
            "Not yet implemented"
          case subscription: Subscription =>
            log.warning("Not yet impelemented")
            "Not yet implemented"
          case a =>
            log.warning("Unknown O-MI request " + a.toString)
            "Unknown O-MI request"
        }.mkString("\n")
      }
    } else {
      //Error found
      complete {
        // TODO: make error response generator in responses package
        <error> {errors.mkString("; ")} </error>
        */
  /* Receives HTTP-POST directed to root (localhost:8080) */
  val getXMLResponse = path("") {
    (post | parameter('method ! "post")) { // Handle POST requests from the client
      respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*")) {
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
            complete {
              requests.map {
                case oneTimeRead: OneTimeRead =>
                  log.debug("read")
                  log.debug("Begin: " + oneTimeRead.begin + ", End: " + oneTimeRead.end)
                  Read.OMIReadResponse(requests.toList, oneTimeRead.begin, oneTimeRead.end)
                case write: Write => 
                  log.debug("write") 
                  ??? //TODO handle Write
                case subscription: Subscription => 
                  log.debug("sub") 
                  ??? //TODO handle sub
                case _ => log.warning("Unknown request")
              }.mkString("\n")
            }
          } else {
            //Error found
            complete {
              log.error("ERROR")
              ??? // TODO handle error
            }
          }
        }
      }
    }
  }

  // Combine all handlers

  val myRoute = helloWorld ~ staticHtml ~ getDataDiscovery ~ getXMLResponse
}
