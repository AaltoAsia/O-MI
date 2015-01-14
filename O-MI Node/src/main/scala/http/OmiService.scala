package http

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import responses._

import parsing._
import sensorDataStructure.SensorMap
import xml._

class OmiServiceActor(val sensorDataStorage: SensorMap) extends Actor with OmiService {

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

  val sensorDataStorage: SensorMap

  //Get the files from the html directory; http://localhost:8080/html/form.html
  val staticHtml =
    pathPrefix("html"){
      getFromDirectory("html")
    }

  // should be removed?
  val helloWorld = 
    path("") { // Root
      get {
        respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default
          complete {
            <html>
              <body>
                <h1>Say hello to <i>O-MI Node service</i>!</h1>
              </body>
            </html>
          }
        }
      }
    }

  val getDataDiscovery = 
    path(Rest){ path =>
      get {
        Read.generateODFresponse(path, sensorDataStorage) match {
          case Some(Left(value)) =>
            respondWithMediaType(`text/plain`) {
              complete(value)
            }
          case Some(Right(xmlData)) => 
            respondWithMediaType(`text/xml`) {
              complete(xmlData)
            }
          case None =>
            respondWithMediaType(`text/xml`) {
              complete(404, <error>No object found</error>)
            }
        }
      }
    }

  val getXMLResponse = entity(as[NodeSeq]) { xml => 
    val omi = OmiParser.parse(xml.toString)
    val requests = omi.filter(r => r != ParseError)
    val errors = omi.filter(e => e == ParseError)
    if(errors.isEmpty) {
      complete{
        requests.map{
          case oneTimeRead: OneTimeRead => ???
          case write: Write => ???
          case subscription: Subscription => ??? 
        }.mkString("\n")
      }
    } else {
      complete {
        ???
      }
    }
  } 
  // Combine all handlers
  val myRoute = helloWorld ~ staticHtml ~ getDataDiscovery

}

