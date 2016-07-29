/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package http

import java.nio.file.{Files, Paths}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.util.{Success, Failure, Try}
import scala.xml.NodeSeq

import org.slf4j.LoggerFactory

import akka.util.ByteString
import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive0
import akka.stream.scaladsl._
import akka.stream._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.ws

import accessControl.AuthAPIService
import http.Authorization._
import parsing.OmiParser
import responses.{RequestHandler, RemoveSubscription}
import types.OmiTypes._
import types.Path

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
class OmiServiceImpl(reqHandler: RequestHandler, val subscriptionManager: ActorRef)(implicit val system: ActorSystem)
     extends {
       // Early initializer needed (-- still doesn't seem to work)
       override val log = LoggerFactory.getLogger(classOf[OmiService])
  } with OmiService {

  registerApi(new AuthAPIService())

  //Used for O-MI subscriptions
  val requestHandler = reqHandler

}


/**
 * this trait defines our service behavior independently from the service actor
 */
trait OmiService
     extends CORSSupport
     with WebSocketOMISupport
     with OmiServiceAuthorization
     {

  val system : ActorSystem
  import system.dispatcher
  def log: org.slf4j.Logger
  val requestHandler: RequestHandler


  //Get the files from the html directory; http://localhost:8080/html/form.html
  //this version words with 'sbt run' and 're-start' as well as the packaged version
  val staticHtml = if(Files.exists(Paths.get("./html"))){
    getFromDirectory("./html")
  } else getFromDirectory("O-MI Node/html")
  //val staticHtml = getFromResourceDirectory("html")


  /** Some trickery to extract the _decoded_ uri path: */
  def pathToString: Uri.Path => String = {
    case Uri.Path.Empty              => ""
    case Uri.Path.Slash(tail)        => "/"  + pathToString(tail)
    case Uri.Path.Segment(head, tail)=> head + pathToString(tail)
  }

  // Change default to xml mediatype and require explicit type for html
  val htmlXml = ScalaXmlSupport.nodeSeqMarshaller(MediaTypes.`text/html`)
  implicit val xml = ScalaXmlSupport.nodeSeqMarshaller(MediaTypes.`text/xml`)

  // should be removed?
  val helloWorld = get {
     val document = { 
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

    // XML is marshalled to `text/xml` by default
    complete(ToResponseMarshallable(document)(htmlXml))
  }

  val getDataDiscovery =
    path(Remaining) { uriPath =>
      get {
        // convert to our path type (we don't need very complicated functionality)
        val pathStr = uriPath // pathToString(uriPath)
        val path = Path(pathStr)

        requestHandler.generateODFREST(path) match {
          case Some(Left(value)) =>
            complete(value)
          case Some(Right(xmlData)) =>
            complete(xmlData)
          case None =>            {
            log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$path]")

            // TODO: Clean this code
            complete(
              ToResponseMarshallable(
              <error>No object found</error>
              )(
                fromToEntityMarshaller(StatusCodes.NotFound)(xml)
              )
            )
          }
        }
      }
    }

  def handleRequest(
    hasPermissionTest: PermissionTest,
    requestString: String,
    currentConnectionCallback: Option[Callback] = None
  ): Future[NodeSeq] = {
    try {
      val eitherOmi = OmiParser.parse(requestString)
      eitherOmi match {
        case Right(requests) =>

          val ttlPromise = Promise[ResponseRequest]()

          val request = requests.headOption  // TODO: Only one request per xml is supported currently
          val response: Future[ResponseRequest] = request match {

            case Some(originalReq : OmiRequest) =>
              hasPermissionTest(originalReq) match {
                case Some(req) =>{
                  req.ttl match{
                    case ttl: FiniteDuration => ttlPromise.completeWith(
                      akka.pattern.after(ttl, using = system.scheduler) {
                        log.info(s"TTL timed out after $ttl")
                        Future.successful(Responses.TimeOutError())
                      }
                    )
                    case _ => //noop
                  }
                  val fixedReq =
                    if (req.callback.map(_.uri).getOrElse("") == "0")
                      req.withCallback(currentConnectionCallback)
                    else req

                  requestHandler.handleRequest(fixedReq)(system)
                }
                case None =>
                  Future.successful(Responses.Unauthorized())
              }
            case _ =>  Future.successful(Responses.NotImplemented())
          }

          //if timeoutfuture completes first then timeout is returned
          Future.firstCompletedOf(Seq(response, ttlPromise.future)) map {
            response : ResponseRequest =>
              request match {
                case Some(resp: ResponseRequest) if !resp.results.exists{ result => result.odf.nonEmpty }=>
                  NodeSeq.Empty
                case Some(other : OmiRequest) =>
                  // check the error code for logging
                  if(response.results.exists{ result => result.returnValue.returnCode != "200"}){
                    log.warn(s"Errors with following request:\n${requestString}")
                  }

                  response.asXML // return
              }
          }

        case Left(errors) => { // Errors found

          log.warn(s"${requestString}")
          log.warn("Parse Errors: {}", errors.mkString(", "))

          val errorResponse = Responses.ParseErrors(errors.toVector)

          Future.successful(errorResponse.asXML)
        }
      }
    } catch {
      case ex: Throwable => { // Catch fatal errors for logging
        log.error("Fatal server error", ex)
        throw ex
      }
    }
  }


  /** 
   * Receives HTTP-POST directed to root with o-mi xml as body. (Non-standard convenience feature)
   */
  val postXMLRequest = post {// Handle POST requests from the client
    makePermissionTestFunction() { hasPermissionTest =>
      entity(as[String]) {requestString =>   // XML and O-MI parsed later
        complete(handleRequest(hasPermissionTest, requestString))
      }
    }
  }

  /**
   * Receives POST at root with O-MI compliant msg parameter.
   */
  val postFormXMLRequest = post {
    makePermissionTestFunction() { hasPermissionTest =>
      formFields("msg".as[String]) {requestString =>
        complete(handleRequest(hasPermissionTest, requestString))
      }
    }
  }

  // Combine all handlers
  val myRoute = corsEnabled {
    path("") {
      webSocketUpgrade ~
      postFormXMLRequest ~
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


     /**
      * This trait implements websocket support for O-MI message handling using akka-http
      */
     trait WebSocketOMISupport {
       self: OmiService =>
         import system.dispatcher
         def subscriptionManager : ActorRef

         type InSink = Sink[ws.Message, _]
         type OutSource = Source[ws.Message, SourceQueueWithComplete[ws.Message]]

         def webSocketUpgrade = //(implicit r: RequestContext): Directive0 =
           makePermissionTestFunction() { hasPermissionTest =>
             extractUpgradeToWebSocket {wsRequest =>

               val (inSink, outSource) = createInSinkAndOutSource(hasPermissionTest)
               complete(
                 wsRequest.handleMessagesWithSinkSource(inSink, outSource)
               )
             }
           }

         // Queue howto: http://loicdescotte.github.io/posts/play-akka-streams-queue/
         // T is the source type
         // M is the materialization type, here a SourceQueue[String]
         private def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
           val p = Promise[M]
           val s = src.mapMaterializedValue { m =>
             p.trySuccess(m)
             m
           }
           (s, p.future)
         }

         // akka.stream
         protected def createInSinkAndOutSource( hasPermissionTest: PermissionTest): (InSink, OutSource) = {
           val queueSize = 10
           val (outSource, futureQueue) =
             peekMatValue(Source.queue[ws.Message](queueSize, OverflowStrategy.fail))

           def queueSend(futureResponse: Future[NodeSeq]): Future[QueueOfferResult] = {
             val result = for {
               response <- futureResponse
               if (response.nonEmpty)

                 queue <- futureQueue

               // TODO: check what happens when sending empty String
               resultMessage = ws.TextMessage(response.toString)
               queueResult <- queue offer resultMessage
             } yield {queueResult}

             result onComplete {
               case Success(QueueOfferResult.Enqueued) => // Ok
               case Success(e: QueueOfferResult) => log.warn(s"WebSocket response queue failed, reason: $e")
               futureResponse.map{ 
                 response =>
                   val ids = (response \\ "requestID").map{ 
                     node =>
                       node.text.toLong
                   }
                   ids.foreach{ 
                     id =>
                       subscriptionManager ! RemoveSubscription(id)
                   }
               }
               case Failure(e) => log.warn("WebSocket response queue failed, reason: ", e)
               futureResponse.map{ 
                 response =>
                   val ids = (response \\ "requestID").map{ 
                     node =>
                       node.text.toLong
                   }
                   ids.foreach{ 
                     id =>
                       subscriptionManager ! RemoveSubscription(id)
                   }
               }
             }
             result
           }

           def createZeroCallback = Some(Callback{(response: OmiRequest) => 
             queueSend(Future(response.asXML)) map {_ => ()}
           })


           val msgSink = Sink.foreach[String]{ requestString: String  => 
              val futureResponse: Future[NodeSeq] = handleRequest(hasPermissionTest, requestString, createZeroCallback)
                 queueSend(futureResponse)
           }
           val formatter = Flow.fromGraph(new OmiChecker()(materializer))
           val inSink = formatter.to(msgSink)
           (inSink, outSource)
         }

         val system : ActorSystem 
         val materializer : Materializer = ActorMaterializer()(system)

     }

