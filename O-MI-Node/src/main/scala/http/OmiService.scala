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

import java.net.{InetAddress, URI, URLDecoder}
import java.nio.file.{Files, Paths}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers._
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable}
import akka.http.scaladsl.model.{ws, _}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, _}
import akka.stream.alpakka.xml._
import akka.util.{ByteString,Timeout}

import authorization.Authorization._
import authorization._
import database.SingleStores
import org.slf4j.LoggerFactory
import responses.CallbackHandler._
import responses.{CallbackHandler, RESTHandler, RemoveSubscription}
import types.OmiTypes.Callback._
import types.OmiTypes._
import types.odf._
import types.{ParseError, Path}

import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise, TimeoutException, Await, ExecutionContext}
import scala.language.postfixOps
import scala.collection.SeqView
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq
import util._
import utils._
import TemporaryRequestInfoStore._

trait OmiServiceAuthorization
  extends ExtensibleAuthorization
    with LogPermissiveRequestBeginning // Log Permissive requests
    with IpAuthorization // Write and Response requests for configured server IPs
    with SamlHttpHeaderAuth // Write and Response requests for configured saml eduPersons
    with AllowConfiguredTypesForAll // allow basic requests: Read, Sub, Cancel
    with AuthApiProvider // Easier java api for authorization
    with LogUnauthorized // Log everything else


/**
  * Actor that handles incoming http messages
  */
class OmiServiceImpl(
                      protected val system: ActorSystem,
                      val materializer: ActorMaterializer,
                      protected val subscriptionManager: ActorRef,
                      val settings: OmiConfigExtension,
                      val singleStores: SingleStores,
                      protected val requestHandler: ActorRef,
                      protected val callbackHandler: CallbackHandler,
                      protected val requestStorage: ActorRef
                    )
  extends {
    // Early initializer needed (-- still doesn't seem to work)
    override val log = LoggerFactory.getLogger(classOf[OmiService])
  } with OmiService {

  //example auth API service code in java directory of the project
  if( settings.enableExternalAuthorization) {
    log.info("External Auth API v1 module enabled")
    log.info(s"External Authorization port ${settings.externalAuthorizationPort}")
    log.info(s"External Authorization useHttps ${settings.externalAuthUseHttps}")
    registerApi(new AuthAPIService(settings.externalAuthUseHttps, settings.externalAuthorizationPort))
  }

  //example auth API service code in java directory of the project
  if( settings.AuthApiV2.enable) {
    log.info("External Auth API v2 modules enabled")
    log.info(s"External Auth API settings ${settings.AuthApiV2}")
    registerApi(new AuthAPIServiceV2(singleStores.hierarchyStore, settings, system, materializer))
  }

}


/**
  * this trait defines our service behavior independently from the service actor
  */
trait OmiService
  extends CORSSupport
    with WebSocketOMISupport
    with OmiServiceAuthorization {


  protected def log: org.slf4j.Logger

  protected def requestStorage: ActorRef
  protected def requestHandler: ActorRef

  protected def callbackHandler: CallbackHandler

  protected val system: ActorSystem

  import system.dispatcher

  implicit def materializer: ActorMaterializer
  //Get the files from the html directory; http://localhost:8080/html/form.html
  //this version works with 'sbt run' and 're-start' as well as the packaged version
  val staticHtml: Route = if (Files.exists(Paths.get("./html"))) {
    getFromDirectory("./html")
  } else getFromDirectory("O-MI-Node/html")
  //val staticHtml = getFromResourceDirectory("html")


  /** Some trickery to extract the _decoded_ uri path: */
  def pathToString: Uri.Path => String = {
    case Uri.Path.Empty => ""
    case Uri.Path.Slash(tail) => "/" + pathToString(tail)
    case Uri.Path.Segment(head, tail) => head + pathToString(tail)
  }

  // Change default to xml mediatype and require explicit type for html
  val htmlXml: ToEntityMarshaller[NodeSeq] = ScalaXmlSupport.nodeSeqMarshaller(MediaTypes.`text/html`)
  implicit val xmlCT: ToEntityMarshaller[NodeSeq] = ScalaXmlSupport.nodeSeqMarshaller(MediaTypes.`text/xml`)

  // should be removed?
  val helloWorld: Route = get {
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
  def parseEventsToHttpEntity(events: Iterable[ParseEvent]): HttpEntity.Chunked = {
    val chunkStream = parseEventsToByteSource(events).map(HttpEntity.ChunkStreamPart.apply)
    HttpEntity.Chunked(ContentTypes.`text/xml(UTF-8)`, chunkStream)
  }

  val getDataDiscovery: Route =
    path(Remaining) { uriPath =>
      get {
        makePermissionTestFunction() { hasPermissionTest =>
          extractClientIP { user =>

            // convert to our path type (we don't need very complicated functionality)
            val pathStr = uriPath.split("/").map { id => URLDecoder.decode(id, "UTF-8") }.toSeq
            implicit val timeout: Timeout = settings.journalTimeout

            val origPath = Path(pathStr)
            val path = origPath match {
              case _path if _path.lastOption
                .exists(List("value", "MetaData", "description", "id", "name").contains(_)) =>
                Path(_path.init) // check permission for parent infoitem/object
              case _path => _path
            }

            val asReadRequestF: Future[Option[ReadRequest]] = singleStores.getHierarchyTree()
              .map(_.get(Path(path.takeWhile(_!="MetaData"))).map {
              n: Node => ImmutableODF(Vector(n))
            }.map {
              p =>
                ReadRequest(p, user0 = UserInfo(remoteAddress = Some(user)))
            })

            def errorResult(msg: String ) = {
              Right(
                Vector(
                  StartDocument,
                  StartElement("error"),
                  Characters(msg),
                  EndElement("error"),
                  EndDocument
                ).view
              )
            }
            val response: Future[Either[String,SeqView[ParseEvent,Seq[_]]]] = asReadRequestF.flatMap {
              case Some(readReq) =>
                hasPermissionTest(readReq) match {
                  case Success(_) => 
                    RESTHandler.handle(origPath)(singleStores,timeout) 
                  
                  case Failure(e: UnauthorizedEx) => // Unauthorized
                    Future.successful(errorResult("No object found"))

                  case Failure(pe: ParseError) =>
                    log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$origPath]")
                    Future.successful(errorResult("No object found"))

                  case Failure(ex) =>
                    Future.successful(errorResult("Internal Server Error"))
                }
              case None =>
                log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$origPath]")
                Future.successful(Right(Vector.empty.view))
            }
            

            onComplete(response) {
              case Success(Right(xmlResp)) => 
                if( xmlResp.isEmpty ){
                  val errorEvents= errorResult("No Object Found").value
                  val entity = parseEventsToHttpEntity(errorEvents)
                  complete((StatusCodes.NotFound,entity))
                } else {
                  val xmlWithNamespace = xmlResp.take(2).map{
                    // take StartDocument and first StartElement
                    case s: StartElement =>
                      val ns = Version.OdfVersion.OdfVersion1.namespace
                      s.copy(namespace=Some(ns),namespaceCtx=List(Namespace(ns)))
                    case x => x // noop
                  } ++ xmlResp.drop(2)

                  val entity = parseEventsToHttpEntity(xmlWithNamespace)
                  complete(entity)
                }
              case Success(Left(strResp)) => 
                complete(strResp)
              case Failure(ex) => {
                log.error("Failure while creating response", ex)
                complete(ToResponseMarshallable(
                  <error>Internal Server Error</error>
                )(
                  fromToEntityMarshaller(StatusCodes.InternalServerError)(xmlCT)
                ))
              }
            }
          }

        }

      }

    }


  def handleRequest(
                     hasPermissionTest: PermissionTest,
                     requestSource: Source[String,_],
                     currentConnectionCallback: Option[Callback] = None,
                     remote: RemoteAddress,
                   ): Future[Future[ResponseRequest]] = {
    Future {

      val startTime = currentTimestamp

      

      val originalReq = RawRequestWrapper(requestSource, UserInfo(remoteAddress = Some(remote)))

      val omiVersion = OmiVersion.fromNameSpace(originalReq.omiEnvelope.namespace.getOrElse("default"))
      val odfVersion = originalReq.odfObjects.map{ objs => OdfVersion.fromNameSpace(objs.namespace.getOrElse("default") )}

      val requestToken = Await.result(
        singleStores.addRequestInfo(startTime.getTime()*1000 + originalReq.handleTTL.toSeconds, omiVersion, odfVersion),
        originalReq.handleTTL) // TODO better infinite ttl handling (not only this line, overall)
      val rt = Some(requestToken)

      requestStorage ! AddRequest( requestToken )
      requestStorage ! AddInfos( requestToken, Seq(
        RequestTimestampInfo("start-time",  startTime),
        RequestAnyInfo("omiVersion", omiVersion),
        RequestAnyInfo("odfVersion", odfVersion)
      ))


      val ttlPromise = Promise[ResponseRequest]()
      originalReq.ttl match {
        case ttl: FiniteDuration => ttlPromise.completeWith(
          akka.pattern.after(ttl, using = system.scheduler) {
            log.info(s"TTL timed out after $ttl seconds")
            Future.successful(Responses.TTLTimeout())
          }
        )
        case _ => //noop
      }

      //timer.step("Request wrapping")
      val responseF: Future[ResponseRequest] = hasPermissionTest(originalReq) match {
        case Success((req: RequestWrapper, user: UserInfo)) => { // Authorized
          //timer.step("Permission test")
          req.user = UserInfo(user.remoteAddress, user.name) //Copy user info to requestwrapper
          requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("pre-parse-time",  currentTimestamp)))
          val parsed = req.parsed
          //timer.step("Parsed")
          requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("post-parse-time",  currentTimestamp)))
          parsed match {
            case Right(requests) =>
              val unwrappedRequest = req.unwrapped // NOTE: Be careful when implementing multi-request messages
              unwrappedRequest match {
                case Success(request: OmiRequest) =>
                  
                  //timer.step("Unwrapped request")
                  defineCallbackForRequest(request, currentConnectionCallback).flatMap {
                    request: OmiRequest => 
                      //timer.step("Callback defined")
                      requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("callback-time",  currentTimestamp)))
                      val handle: Future[ResponseRequest] = handleOmiRequest(request.withRequestToken(rt))
                      handle.map{
                        response =>
                        //timer.step("Request handled")
                        requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("handle-end-time",  currentTimestamp)))
                        response
                      }
                  }.recover {
                    case e: TimeoutException => Responses.TTLTimeout(Some(e.getMessage))
                    case e: IllegalArgumentException => Responses.InvalidRequest(Some(e.getMessage))
                    case icb: InvalidCallback => Responses.InvalidCallback(icb.callback, Some(icb.message))
                    case t: Throwable =>
                      log.error("Internal Server Error: ", t)
                      Responses.InternalError(t)
                  }
                case Failure(t: Throwable) =>
                  log.error("Internal Server Error: ", t)
                  Future.successful(Responses.InternalError(t))
              }
            case Left(errors) => { // Parsing errors found

              log.warn("Parse Errors: {}", errors.mkString(", "))

              val errorResponse = Responses.ParseErrors(errors.toVector)

              Future.successful(errorResponse)
            }
          }
        }
        case Failure(e: UnauthorizedEx) => // Unauthorized
          Future.successful(Responses.Unauthorized())
        case Failure(pe: ParseError) =>
          val errorResponse = Responses.ParseErrors(Vector(pe))
          Future.successful(errorResponse)
        case Failure(ex) =>
          Future.successful(Responses.InternalError(ex))
      }



      // if timeoutfuture completes first then timeout is returned
      Future.firstCompletedOf(Seq(responseF, ttlPromise.future)) map {
        response: ResponseRequest =>
          
          //timer.step("Omi response ready")
          // check the error code for logging
          val statusO = response.results.map { result => result.returnValue.returnCode }
          if (statusO exists (_ != "200")) {
            log.debug(s"Error code $statusO with received request")
          }

          //requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("toXML-start-time",  currentTimestamp)))
          //timer.step("Omi response status check")
          //val xmlResponse = response.asXML // return
          ////timer.step("Omi response to XML")
          //requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("toXML-end-time",  currentTimestamp)))
          //timer.total()

          //val xmlStream = Source
          //  .fromIterator(() => xmlResponse.iterator)
          //  .via( XmlWriting.writer )
          //  .toMat(Sink.fold[ByteString, ByteString](Bytestring())((t, u) => t + u))(Keep.right)

          //HttpEntity.Strict(ContentType.`text/xml(UTF-8)`, xmlStream)
          //HttpEntity.CloseDelimited(ContentType.`text/xml(UTF-8)`, xmlStream)
          //
          //
          requestStorage ! AddInfos( requestToken, Seq( RequestTimestampInfo("final-time",  currentTimestamp)))
          requestStorage ! RemoveRequest( requestToken )

          response.withRequestToken(rt) //TODO: Clean requestToken passing
      }

    }.recover{

      case ex: IllegalArgumentException => {
        log.debug(ex.getMessage)
        Future.successful(Responses.InvalidRequest(Some(ex.getMessage)))
      }
      case ex: Throwable => { // Catch fatal errors for logging
        log.error("Fatal server error", ex)
        Future.failed(ex)
      }
    }//.map(f => f.map(_.withRequestToken(rt))) //TODO: Clean requestToken passing
  }

  def handleOmiRequest(request: OmiRequest): Future[ResponseRequest] = {
    implicit val to: Timeout = Timeout(request.handleTTL)
    request match {
      // Part of a fix to stop response request infinite loop (server and client sending OK to others' OK)
      case respRequest: ResponseRequest if respRequest.results.forall { result => result.odf.isEmpty } =>
        Future.successful(Responses.NoResponse())
      case sub: SubscriptionRequest => (requestHandler ? sub).mapTo[ResponseRequest]
      case other: OmiRequest =>
        request.callback match {
          case None => (requestHandler ? other).mapTo[ResponseRequest]
          case Some(callback: RawCallback) =>
            Future.successful(
              Responses.InvalidCallback(
                callback,
                Some("Callback 0 not supported with http/https try using ws(websocket) instead")
              )
            )
          case Some(callback: DefinedCallback) => {
            (requestHandler ? other).mapTo[ResponseRequest] map { response =>
              callbackHandler.sendCallback(callback, response.withRequestToken(request.requestToken))
            }
            Future.successful(
              Responses.Success(description = Some("OK, callback job started"))
            )
          }
        }
    }
  }

  def chunkedStream(fResponse: Future[ResponseRequest]): ResponseEntity = {
    val chunkStream =
      Source.fromFutureSource(for {
        response <- fResponse
        versions <- singleStores.getRequestInfo(response)
        
      } yield response.asXMLByteSourceWithVersion(versions))
        .map(HttpEntity.ChunkStreamPart.apply)

    HttpEntity.Chunked(ContentTypes.`text/xml(UTF-8)`, chunkStream)
  }


  def defineCallbackForRequest(
                                request: OmiRequest,
                                currentConnectionCallback: Option[Callback]
                              ): Future[OmiRequest] = request.callback match {
    case None => Future.successful(request)
    case Some(definedCallback: DefinedCallback) =>
      Future.successful(request)
    case Some(RawCallback("0")) if currentConnectionCallback.nonEmpty =>
      Future.successful(request.withCallback(currentConnectionCallback))
    case Some(RawCallback("0")) if currentConnectionCallback.isEmpty =>
      Future
        .failed(InvalidCallback(RawCallback("0"),
          "Callback 0 not supported with http/https try using ws(websocket) instead"))
    case Some(cba@RawCallback(address)) =>
      // Check that the RemoteAddress Is the same as the callback address if user is not Admin

      lazy val userAddr = for {
        remoteAddr <- request.user.remoteAddress
        hostAddr <- remoteAddr.getAddress.asScala
        callbackAddr <- Try(InetAddress.getByName(new URI(address).getHost).getHostAddress).toOption
        userAddress = hostAddr.getHostAddress
      } yield (userAddress, callbackAddr)

      //TODO Check if admin from somewhere else than config
      val admin = request.user.name.exists{
        name: String => 
          settings.admins.contains(name)
      }

      if (!settings.callbackAuthorizationEnabled || admin || userAddr.exists(asd => asd._1 == asd._2)) {
        val cbTry = callbackHandler.createCallbackAddress(address)
        val result = cbTry.map(callback =>
          request.withCallback(Some(callback))).recoverWith {
          case throwable: Throwable =>
            Try {
              throw InvalidCallback(RawCallback(address), throwable.getMessage, throwable)
            }
        }
        Future.fromTry(result)
      } else {
        log.debug(s"\n\n FAILED WITH ADDRESSESS $userAddr \n\n")
        Future
          .failed(InvalidCallback(cba,
            "Callback to remote addresses(different than user address) require admin privileges"))
      }
  }


  /**
    * Receives HTTP-POST directed to root with o-mi xml as body. (Non-standard convenience feature)
    */
  val postXMLRequest: Route = post {
    // Handle POST requests from the client
    makePermissionTestFunction() { hasPermissionTest =>
      extractDataBytes { requestSource => 
        extractClientIP { user =>
          val response = handleRequest(hasPermissionTest, requestSource.via(byteStringToUTF8Flow), remote = user)
          onSuccess(response) {r =>
            complete(HttpResponse(entity=chunkedStream(r)))
          }
        }
      }
    }
  }

  //TODO: Filter the field msg and decode value
  /**
    * Receives POST at root with O-MI compliant msg parameter.
    */
  val postFormXMLRequest: Route = post {
    makePermissionTestFunction() { hasPermissionTest =>
      headerValue{
        header: HttpHeader =>
          header match {
            case ct: headers.`Content-Type` if ct.is("application/x-www-form-urlencoded") => 
              Some(true)
            case other: HttpHeader => None
          }
      }{ correctCT =>
        extractDataBytes { requestSource =>
          extractClientIP { user =>
            val decodedSource = requestSource.via(byteStringToUTF8Flow).via(urlDecoderFlow)

            val response = handleRequest(hasPermissionTest, decodedSource, remote = user)
            onSuccess(response) {r =>
              complete(HttpResponse(entity=chunkedStream(r)))
            }
          }
        }
      }
    }
  }
  def byteStringToUTF8Flow = Flow[ByteString].map{ bStr: ByteString => bStr.decodeString("UTF-8")}

  def urlDecoderFlow = Flow[String].statefulMapConcat{ () =>
              var msgHandled = false
              var bufferString =""
              str: String => {
                bufferString += str
                if( !msgHandled ){
                  if( bufferString.startsWith("msg=") ){
                    val (_,content) = bufferString.splitAt("msg=".size)
                    bufferString = content
                    msgHandled = true
                  } 
                  Vector.empty
                } else { 
                  if( bufferString.contains("%") ){
                    def decode(encodedString: String, result: String = ""): String ={
                      val i = encodedString.indexOf("%")
                      if( i > -1 ){
                        if( i + 2 >= encodedString.size ){
                          bufferString = encodedString
                          result
                        } else {
                          var (res: String, tail: String) = encodedString.splitAt(i)
                          val encoded = tail.slice(1,3)
                          val value = new String(Array(Integer.parseInt(encoded,16).toByte),"utf-8")
                          res = res + value
                          decode(tail.drop(3), result + res)
                        }
                      } else {
                        bufferString = ""
                        val res =result + encodedString
                        res
                      }
                    }
                    val decoded = decode( bufferString )
                    if( decoded.nonEmpty){
                      Vector(decoded)
                    } else {
                      Vector.empty
                    } 
                  } else {
                    val res = Vector(bufferString)
                    bufferString = ""
                    res
                  }
                }
              }
            }.mapError{
              case error: java.lang.NumberFormatException => 
                new ParseError("Invalid url encoding: " + error.getMessage, "")
              case error: Throwable => error
            }

  // Combine all handlers
  val myRoute: Route = corsEnabled {
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
trait WebSocketOMISupport extends WebSocketUtil {
  self: OmiService =>
  protected def system: ActorSystem

  implicit def materializer: ActorMaterializer

  protected def subscriptionManager: ActorRef

  import system.dispatcher

  type InSink = Sink[ws.Message, _]
  type OutSource = Source[ws.Message, SourceQueueWithComplete[ws.Message]]

  def webSocketUpgrade: Route = //(implicit r: RequestContext): Directive0 =
    makePermissionTestFunction() { hasPermissionTest =>
      extractUpgradeToWebSocket { wsRequest =>
        extractClientIP { ip =>

          val (inSink, outSource) = createInSinkAndOutSource(hasPermissionTest, ip)
          complete(
            wsRequest.handleMessagesWithSinkSource(inSink, outSource)
          )
        }
      }
    }



  // akka.stream
  protected def createInSinkAndOutSource(hasPermissionTest: PermissionTest,
                                         user: RemoteAddress): (InSink, OutSource) = {
    val queueSize = settings.websocketQueueSize
    val (outSource, futureQueue) =
      peekMatValue(Source.queue[ws.Message](queueSize, OverflowStrategy.fail))

    // keepalive? http://doc.akka.io/docs/akka/2.4.8/scala/stream/stream-cookbook.html#Injecting_keep-alive_messages_into_a_stream_of_ByteStrings


    val queue = WSQueue(futureQueue, Some(subscriptionManager))

    val wsConnection = CurrentConnection(queue.connectionIdentifier, queue.sendHandler)

    def createZeroCallback = callbackHandler.createCallbackAddress("0", Some(wsConnection)).toOption

    val inSink = Sink.foreach[ws.Message]{
      case ws.TextMessage.Strict("") =>

      case textMessage: ws.TextMessage =>
        val futureResponse: Future[ws.TextMessage] = for {
          r2 <- handleRequest(hasPermissionTest,
            textMessage.textStream.filter{ str => str.nonEmpty},
            createZeroCallback,
            user
          )
          r <- r2

          versions <- singleStores.getRequestInfo(r)
        } yield ws.TextMessage.Streamed(r.asXMLSourceWithVersion(versions))

        queue.send(futureResponse)
      case msg: ws.Message => 
          queue.send(Future.successful(ws.TextMessage("Error: Could not process ws message, use text instead of binary")))
    }
    /*
    val msgSink = Sink.foreach[Future[String]] { future: Future[String] =>
      future.flatMap {
        case "" => //Keep alive 
        case requestString: String =>

          val futureResponse: Future[ws.TextMessage] = for {
            r <- handleRequest(hasPermissionTest,
                requestString,
                createZeroCallback,
                user
              )
            versions <- singleStores.getRequestInfo(r)
          } yield ws.TextMessage.Streamed(r.asXMLSourceWithVersion(versions))

          queue.send(futureResponse)
      }
    }

    val inSink = stricted.to(msgSink)
    */
    (inSink, outSource)
  }

}
trait WebSocketUtil {
  protected def system: ActorSystem
  protected def log: org.slf4j.Logger
  
  // Queue howto: http://loicdescotte.github.io/posts/play-akka-streams-queue/
  // T is the source type
  // M is the materialization type, here a SourceQueue[String]
  def peekMatValue[T, M](src: Source[T, M]): (Source[T, M], Future[M]) = {
    val p = Promise[M]
    val s = src.mapMaterializedValue { m =>
      p.trySuccess(m)
      m
    }
    (s, p.future)
  }

  case class WSQueue(val futureQueue: Future[SourceQueueWithComplete[ws.Message]], val subscriptionManager: Option[ActorRef] = None, val singleStores: Option[SingleStores] = None)(implicit val ex: ExecutionContext) {
    val connectionIdentifier = futureQueue.hashCode

		def send(futureResponse: Future[ws.TextMessage], ids: Seq[RequestID]=Vector.empty): Future[QueueOfferResult] = {
      val result = for {
        response <- futureResponse
        //if (response.nonEmpty)

        queue <- futureQueue

        // TODO: check what happens when sending empty String
        queueResult <- queue offer response
      } yield queueResult


      def removeRelatedSub() = {
        ids.foreach {
          id =>
            subscriptionManager.map(_ ! RemoveSubscription(id, 2 minutes))
        }
      }

      result onComplete {
        case Success(QueueOfferResult.Enqueued) => // Ok
        case e @ (Success(_: QueueOfferResult) | Failure(_)) => // Others mean failure
          log.warn(s"WebSocket response queue failed, reason: $e")
          removeRelatedSub()
          sendHandler(Responses.InternalError(Some(e.toString)))
      }
      result
    }

    val sendHandler = (response: ResponseRequest) => {
      send(
        singleStores
          .map(ss =>
              ss.getRequestInfo(response)
                .map(v => response.asXMLSourceWithVersion(v)))
          .getOrElse(Future.successful(response.asXMLSource))
          .map(ws.TextMessage.Streamed(_))
      , Vector(connectionIdentifier)) map { _ => () }
    }
  }

}
