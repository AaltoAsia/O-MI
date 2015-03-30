package http

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingAdapter
import akka.pattern.ask

import spray.routing._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import MediaTypes._

import responses._
import parsing._
import parsing.Types._
import sensordata.SensorData
import database.SQLite

import scala.concurrent.duration._
import scala.concurrent._
import xml._

class OmiServiceActor(subHandler: ActorRef) extends Actor with ActorLogging with OmiService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // Used for O-MI subscriptions
  val subscriptionHandler = subHandler

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)

}

// this trait defines our service behavior independently from the service actor
trait OmiService extends HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global
  def log: LoggingAdapter
  val subscriptionHandler: ActorRef

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

  val CancelResponseGen = new OMICancelGen(subscriptionHandler)

  /* Receives HTTP-POST directed to root (localhost:8080) */
  val getXMLResponse = post { // Handle POST requests from the client
    path("") {
      corsHeaders {
        entity(as[NodeSeq]) { xml =>
          val omi = OmiParser.parse(xml.toString)
          val errors = omi.collect {
            case e: ParseError => e
          }
          val requests = omi diff errors // exclude errors from omi

          if (errors.isEmpty) {
            respondWithMediaType(`text/xml`) {
              
              var returnStatus = 200

              //FIXME: Currently sending multiple omi:omiEnvelope
              val result = requests.map {

                case oneTimeRead: OneTimeRead =>
                  log.debug(oneTimeRead.toString)

                  if (oneTimeRead.requestId.isEmpty) {
                    ReadResponseGen.runRequest(oneTimeRead)

                  } else {
                    oneTimeRead.requestId.map{reqId =>
                      OMISubscription.OMISubscriptionResponse(reqId.toInt) // FIXME: parse id in parsing (errorhandling)

                    }.reduceLeft(_ ++ _)
                  }


                case write: Write => 
                  log.debug(write.toString) 
                  returnStatus = 501
                  ErrorResponse.notImplemented

                case subscription: Subscription => 
                  log.debug(subscription.toString) 

                  val (id, response) = OMISubscription.setSubscription(subscription) //setSubscription return -1 if subscription failed

                  if (subscription.callback.isDefined && subscription.callback.get.length > 3 && id >= 0) // XXX: hack check for valid url :D
                    subscriptionHandler ! NewSubscription(id)

                  response

                case cancel: Cancel =>
                  log.debug(cancel.toString)

                  CancelResponseGen.runRequest(cancel)

                case _ => log.warning("Unknown request")
                  returnStatus = 400

              }.mkString("\n")

              complete(returnStatus, result)

            }
          } else {
            //Errors found
            log.warning("Parse Errors: {}", errors.mkString(", "))
            complete (400,
              ErrorResponse.parseErrorResponse(errors)  
            )
          }
        }
      }
    }
  }

  // Combine all handlers
  val myRoute = helloWorld ~ staticHtml ~ getDataDiscovery ~ getXMLResponse
}
