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
package http

import java.nio.file.{Files, Paths}

import accessControl.AuthAPIService
import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingAdapter
import http.Authorization._
import parsing.OmiParser
import responses.OmiGenerator._
import responses.{Results, RequestHandler}
import spray.http.MediaTypes._
import spray.http._
import spray.routing._
import types.OmiTypes._
import types.Path

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

trait OmiServiceAuthorization
  extends ExtensibleAuthorization
     with LogPermissiveRequestBeginning // Log Permissive requests
     with IpAuthorization         // Write and Response requests for configured server IPs
     with SamlHttpHeaderAuth      // Write and Response requests for configured saml eduPersons
     with AllowNonPermissiveToAll // basic requests: Read, Sub, Cancel
     with AuthApiProvider         // Easier java api for authorization
     with LogUnauthorized         // Log everything else

/**
 * Actor that handles incoming http messages
 * @param reqHandler ActorRef that is used in subscription handling
 */
class OmiServiceActor(reqHandler: RequestHandler)
  extends Actor
     with ActorLogging
     with OmiService
     {

  registerApi(new AuthAPIService())
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


}

/**
 * this trait defines our service behavior independently from the service actor
 */
trait OmiService
  extends HttpService
     with CORSSupport
     with OmiServiceAuthorization
     {

  import scala.concurrent.ExecutionContext.Implicits.global
  def log: LoggingAdapter
  val requestHandler: RequestHandler


  //Get the files from the html directory; http://localhost:8080/html/form.html
  //this version words with 'sbt run' and 're-start' as well as the packaged version
  val staticHtml = if(Files.exists(Paths.get("./html"))){
    getFromDirectory("./html")
  } else getFromDirectory("O-MI Node/html")
  //val staticHtml = getFromResourceDirectory("html")


  /** Some trickery to extract the _decoded_ uri path in current version of spray: */
  def pathToString: spray.http.Uri.Path => String = {
    case Uri.Path.Empty              => ""
    case Uri.Path.Slash(tail)        => "/"  + pathToString(tail)
    case Uri.Path.Segment(head, tail)=> head + pathToString(tail)
  }

  // should be removed?
  val helloWorld = get {
    respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default
      complete {
        <html>
        <body>
          <h1>Say hello to <i>O-MI Node service</i>!</h1>
          <ul>
            <li><a href="Objects">Url Data Discovery /Objects: Root of the hierarchy</a>
              <p>
                With url data discovery you can discover or request Objects,
                 InfoItems and values with HTTP Get request by giving some existing
                 path to the O-DF xml hierarchy.
              </p>
            </li>
            <li><a href="html/webclient/index.html">O-MI Test Client WebApp</a>
              <p>
                You can test O-MI requests here with the help of this webapp.
              </p>
            </li>
            <li style="color:gray;"><a style="text-decoration:line-through" href="html/old-webclient/form.html">Old WebApp</a>
              <p>
                Very old version of the webapp.
              </p>
            </li>
            <li><a href="html/ImplementationDetails.html">Implementation details, request-response examples</a>
              <p>
                Here you can view examples of the requests this project supports.
                These are tested against our server with <code>http.SystemTest</code>.
              </p>
            </li>
          </ul>
        </body>
        </html>
      }
    }
  }

  val getDataDiscovery =
    path(RestPath) { sprayPath =>
      get {
        // convert to our path type (we don't need very complicated functionality)
        val pathStr = pathToString(sprayPath)
        val path = Path(pathStr)

        requestHandler.generateODFREST(path) match {
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
              complete((404, <error>No object found</error>))
            }
        }
      }
    }

  /* Receives HTTP-POST directed to root */
  val postXMLRequest = post { try {// Handle POST requests from the client
    makePermissionTestFunction() { hasPermissionTest =>
      entity(as[String]) {requestString =>

        val eitherOmi = OmiParser.parse(requestString)


        respondWithMediaType(`text/xml`) {
          eitherOmi match {
            case Right(requests) =>

              val ttlPromise = Promise[NodeSeq]()

              val request = requests.headOption  // TODO: Only one request per xml is supported currently
              val response = request match {

                case Some(originalReq : OmiRequest) =>
                  hasPermissionTest(originalReq) match {
                    case Some(req) =>{
                      req.ttl match{
                        case ttl: FiniteDuration => ttlPromise.completeWith(akka.pattern.after(ttl, using = http.Boot.system.scheduler)(Future.successful(xmlFromResults(1.0, Results.timeOutError("ttl timed out")))))
                        case ttl => //noop
                      }
                      requestHandler.handleRequest(req)
                    }
                    case None =>
                      Future.successful(unauthorized)
                  }
                case _ =>  Future.successful(notImplemented)
              }

              //if timeoutfuture completes first then timeout is returned
              onComplete(Future.firstCompletedOf(Seq(response, ttlPromise.future))){
                case Success(value) => {
                  if(value.\\("return").map(_.\@("returnCode")).exists(n=> n.size > 1 && n != "200")){
                    log.warning(s"Errors with following request:\n${requestString}")
                  }

                  complete(value)
                }
                case Failure(ex) => throw ex
              }

            case Left(errors) =>  // Errors found

              log.warning(s"${requestString}")
              log.warning("Parse Errors: {}", errors.mkString(", "))

              val errorResponse = parseError(errors.toSeq:_*)

              complete(errorResponse)
          }
        }
      }
    }
    }catch {
        case ex: Throwable => {log.error(ex, "Fatal server error"); throw ex}
    }
  }

  // Combine all handlers
  val myRoute = cors {
    path("") {
      postXMLRequest ~
      helloWorld
    } ~
    pathPrefix("html") {
      staticHtml
    } ~
    pathPrefixTest("Objects") {
      getDataDiscovery
    }
  }
}
