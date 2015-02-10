package sensordata

// Json4s
import org.json4s._
import org.json4s.native.JsonMethods._


// Akka Actor system
import akka.actor.ActorSystem
 
// HTTP related imports
import spray.http.{ HttpRequest, HttpResponse }
import spray.client.pipelining._
 
// Futures related imports
import scala.concurrent.Future
import scala.util.{ Success, Failure } 

// Need to wrap in a package to get application supervisor actor
// "you need to provide exactly one argument: the class of the application supervisor actor"
package main.scala {

  // trait with single function to make a GET request
  trait WebClient {
    def get(url: String): Future[String]
  }

  // implementation of WebClient trait
  class SprayWebClient(implicit system: ActorSystem) extends WebClient {
    import system.dispatcher

    // create a function from HttpRequest to a Future of HttpResponse
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

    // create a function to send a GET request and receive a string response
    def get(url: String): Future[String] = {
      val futureResponse = pipeline(Get(url))
      futureResponse.map(_.entity.asString)
    }
  }

  object Program extends App {
    import scala.concurrent.ExecutionContext.Implicits.global
    // bring the actor system in scope
    implicit val system = ActorSystem()
    // Define formats
    implicit val formats = DefaultFormats
    
    // create the client
    val webClient = new SprayWebClient()(system)

    // send GET request with absolute URI
    val futureResponse = webClient.get("http://121.78.237.160:2100/")
    
    // wait for Future to complete
    futureResponse onComplete {
      case Success(response) => 
        // Json data received from the server
        val json = parse(response)

        // List of (sensorname, value) objects
        val list = for {
          JObject(child) <- json
          JField(sensor, JString(value)) <- child
        } yield (sensor, value)
        
        println(list)
        
        System.exit(1)
      case Failure(error) => println("An error has occured: " + error.getMessage)
    }
  }
} 