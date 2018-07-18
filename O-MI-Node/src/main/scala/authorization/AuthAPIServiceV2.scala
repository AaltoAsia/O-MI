package authorization

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.client.RequestBuilding.RequestBuilder
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.{Http, HttpExt}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import database.journal.Models.GetTree
import http.OmiConfigExtension
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.{JObject, JString, _}
import org.slf4j.{Logger, LoggerFactory}
import types.OmiTypes._
import types.Path
import types.odf._

import scala.concurrent.{Await, Future}
import scala.util.Try


/**
  * API response class for getting the permission data from external service.
  */
case class AuthorizationResponse(allow: Set[Path], deny: Set[Path])


trait AuthApiJsonSupport {
  //class PathSerializer extends CustomSerializer[Path](format =>
  //    ( { case JString(s) => Path(s)  }
  //    , { case p => JString(p.toString)  }
  //    )
  //)
  //implicit val jsonSerialization = JsonSerialization
  //implicit val json4sFormats: Formats = DefaultFormats + new PathSerializer // + new NoneSerializer
  //implicit def jsonMarshaller[T <: AnyRef] = marshaller[T](jsonSerialization, json4sFormats)

  protected implicit val materializer: ActorMaterializer
  protected val httpExtension: HttpExt
  protected implicit val system: ActorSystem

  import system.dispatcher

  protected def bodyStringF(http: HttpMessage): Future[String] =
    Unmarshal(http.entity).to[String]

  protected def bodyString(http: HttpMessage)(implicit t: Timeout): String =
    Await.result(Unmarshal(http.entity).to[String], t.duration)

  protected def sendAndReceiveAsAuthorizationResponse(httpRequest: HttpRequest)
                                                     (implicit t: Timeout): Future[AuthorizationResponse] =
    httpExtension.singleRequest(httpRequest)
      .flatMap { response =>
        if (response.status.isSuccess) {
          bodyStringF(response) map { (str) =>
            val json = parse(str) //.extract[AuthorizationResponse] doesnt work for some reason
          val allow = for {
            JObject(contents) <- json
            JField("allowed", JArray(list)) <- contents
            JString(pathStr) <- list
          } yield Path(pathStr)
            val deny = for {
              JObject(contents) <- json
              JField("denied", JArray(list)) <- contents
              JString(pathStr) <- list
            } yield Path(pathStr)

            AuthorizationResponse(allow.toSet, deny.toSet)
          }
        } else Future.failed(new Exception(s"Request failed. Response was: $response"))
      }
}


/**
  * Version 2 of AuthAPI service. It provides functionality of the internal AuthAPI interface to external authorization services.
  * This V2 has different interface to allow easier partial authorization by having "deny" rules in addition to "allow" rules.
  */
class AuthAPIServiceV2(
                        val hierarchyStore: ActorRef,
                        val settings: OmiConfigExtension,
                        protected implicit val system: ActorSystem,
                        protected override implicit val materializer: ActorMaterializer
                      ) extends AuthApi with AuthApiJsonSupport {

  import settings.AuthApiV2._


  protected val log: Logger = LoggerFactory.getLogger(classOf[AuthAPIServiceV2])


  import system.dispatcher

  protected val httpExtension: HttpExt = Http(system)


  def filterODF(originalRequest: OdfRequest, filters: AuthorizationResponse): Future[Option[OmiRequest]] = {
    implicit val timeout: Timeout = originalRequest.handleTTL
    val odfTreeF: Future[ImmutableODF] = (hierarchyStore ? GetTree).mapTo[ImmutableODF]
    odfTreeF.map { odfTree =>
      originalRequest match {
        case rr: ReadRequest => // requests with existing tree, requires permissions to whole sub trees

          // Calculate required changes to the request

          // partition to request paths and non-allowed remove paths
          val (requestPaths, nonAllowPaths) =
            originalRequest.odf
              .getLeafPaths
              .partition{ p =>
                filters.allow.exists(a => (a isAncestorOf p) || a == p)
              }

          // partition with foldLeft to childDeny and other active deny paths
          val (childDenyPaths, otherDenyPaths) =
            filters.deny
              .foldLeft((Set[Path](),Set[Path]())){
                case ((_childDenyPaths, _otherDenyPaths), deniedPath) =>
                  if (requestPaths exists (rp => rp.isAncestorOf(deniedPath)))
                    (_childDenyPaths + deniedPath, _otherDenyPaths)
                  else if (requestPaths.exists(rPath => deniedPath.isAncestorOf(rPath) || rPath == deniedPath))
                    (_childDenyPaths, _otherDenyPaths + deniedPath)
                  else
                    (_childDenyPaths, _otherDenyPaths)
              }

          // items that need to be queried from hierarchystore
          val newChildren =
            childDenyPaths
              .flatMap{ deniedPath =>
                odfTree.getChilds(deniedPath.init)
              }
              .map{
                case ii:InfoItem => ii.copy(descriptions=Set.empty, metaData=None)
                case obj:Object => obj.copy(descriptions=Set.empty)
                case other => other
              }


          // Execute required changes to the request, TODO: cleaner code

          val filteredOdf = ImmutableODF(
              (requestPaths -- (childDenyPaths union otherDenyPaths))
                .flatMap(originalRequest.odf.getNodesMap.get(_))
                .toSeq
            )
            .addNodes(newChildren.toSeq)
          //  originalRequest.odf
          //    .removePaths(childDenyPaths union nonAllowPaths union otherDenyPaths) addNodes newChildren.toSeq


          //log.debug(s"FILTERODF \n original $originalRequest \n requestPaths $requestPaths \n childDenyPaths $childDenyPaths \n otherDenyPaths $otherDenyPaths \n newChildren $newChildren \n nonAllowPaths $nonAllowPaths \n filteredOdf $filteredOdf")

          // TODO: Cleaner code
          if (filteredOdf.isEmpty && !(originalRequest.odf.isEmpty && filters.allow.contains(Path("Objects"))))
            None
          else
            Some(originalRequest replaceOdf filteredOdf)

        case _ => // requests with new tree, requires permissions to parents and overwriting items
          val filteredNodes = originalRequest.odf.getNodesMap.filterKeys{ p =>
            filters.allow.exists(a => (a isAncestorOf p) || p == a) &&
            !filters.deny.exists(d => (d isAncestorOf p) || p == d)
          }

          // Constructor builds missing ancestors
          val filteredOdf = ImmutableODF(filteredNodes.values)

          //log.debug(s"FILTERODF \n original $originalRequest \n filteredNodes $filteredNodes \n filteredOdf $filteredOdf")

          // TODO: Cleaner code
          //if (filteredOdf.isEmpty && !(originalRequest.odf.isEmpty && filters.allow.contains(Path("Objects"))))
          if (filteredOdf.getNodesMap.size < originalRequest.odf.getNodesMap.size)
            None
          else
            //Some(originalRequest replaceOdf filteredOdf)
            Some(originalRequest)
      }
    }
  }

  def optionToAuthResult: Option[OmiRequest] => (UserInfo => AuthorizationResult) = {
    case None => Unauthorized(_)
    case Some(req) => Changed(req, _)
  }


  protected def extractParameter(
                                  httpMessage: HttpMessage, rawOmi: Option[RawRequestWrapper],
                                  fromContext: String, from: String)(implicit t: Timeout): Option[String] =
    fromContext match {
      case "authorizationheader" =>
        httpMessage.header[headers.Authorization].map(_.value.drop(from.length + 1))

      case "omienvelope" =>
        rawOmi.flatMap(_.omiEnvelope.attr(from))

      case "headers" =>
        httpMessage.headers.find(header => from == header.name).map(_.value)

      case "query" =>
        httpMessage match {
          case r: HttpRequest => r.uri.query().get(from)
          case _ => None
        }

      case "cookie" =>
        httpMessage match {
          case r: HttpRequest => r.cookies.find(_.name == from).map(_.value)
          case r: HttpResponse => r.headers.collectFirst {
            case c: headers.`Set-Cookie` if c.cookie.name == from => c.cookie.value
          }
        }

      case "jsonbody" => Try[String] {
        val json = parse(bodyString(httpMessage))
        val searchResult = json \\ from
        searchResult match {
          case JString(str) => str
          case other =>
            val res = compact(render(other))
            log.debug(s"Not yet implemented: jsonbody search result: $searchResult, for search term: $from, resulting variable string: $res")
            res
        }
      }.toOption
    }

  type VariableMap = Map[String, String]

  protected def extractToMap(httpRequest: HttpMessage,
                             rawOmi: Option[RawRequestWrapper],
                             confObject: ParameterExtraction)(implicit t: Timeout): VariableMap = {
    for {
      (key, value) <- confObject
      (paramKey, variable) <- value
      paramOption = extractParameter(httpRequest, rawOmi, key, paramKey)
      if (paramOption.isDefined)
    } yield (variable, paramOption.get) // get: None checked on the previous line
  }

  protected def createRequest(base: RequestBuilder,
                              baseUri: Uri,
                              confObject: ParameterExtraction,
                              variables: VariableMap): HttpRequest = {
    def mapGet(context: String): Map[String, String] =
      confObject.getOrElse(context, Map.empty)

    def keyValues(context: String): Seq[(String, String)] = for {
      (key, variable) <- mapGet(context).toSeq
      value <- variables.get(variable).toSeq
    } yield key -> value


    val query = Uri.Query(keyValues("query"): _*)
    val uri = baseUri.withQuery(query)

    val extraHeaders = (for {
      (key, variable) <- mapGet("headers").toSeq
      value <- variables.get(variable).toSeq
    } yield headers.RawHeader(key, value))

    val cookies = {
      val cookiePairs = keyValues("cookies")
      if (cookiePairs.nonEmpty) Seq(headers.Cookie(cookiePairs: _*))
      else Seq.empty
    }

    val authHeader = for {
      (key, variable) <- mapGet("authorizationheader").headOption.toSeq
      value <- variables.get(variable).toSeq
    } yield headers.RawHeader("Authorization", s"$key $value")

    val json = keyValues("jsonbody").foldLeft(JObject()) {
      (a: JObject, b: (String, String)) => a ~ b
    }

    val formdataQuery = Uri.Query(keyValues("form-urlencoded"): _*)

    val req = base(uri).withHeaders((extraHeaders ++ authHeader ++ cookies): _*)

    if (json.obj.nonEmpty)
      req.withEntity(
        HttpEntity.Strict(`application/json`, ByteString(compact(render(json))))
      )
    else if (formdataQuery.nonEmpty)
      req.withEntity(
        FormData(formdataQuery).toEntity
      )
    else req
  }

  protected def isAuthorizedForOdfRequest(httpRequest: HttpRequest,
                                          rawOmiRequest: RawRequestWrapper): AuthorizationResult = {

    implicit val timeout: Timeout = Timeout(rawOmiRequest.handleTTL)

    val requestType = (rawOmiRequest.requestVerb match {
      case RawRequestWrapper.MessageType.Response => RawRequestWrapper.MessageType.Write
      case x => x
    }).name

    val vars: Map[String, String] = parametersConstants ++
      extractToMap(httpRequest, Some(rawOmiRequest), parametersFromRequest) +
      ("requestTypeChar" -> requestType.head.toString) +
      ("requestType" -> requestType)

    log.debug(s"Parameter variables: $vars")
    val copiedHeaders = httpRequest.headers.filter(omiHttpHeadersToAuthentication contains _.lowercaseName)


    val resultF = for {

      authenticationResult <-
      if (authenticationEndpoint.isEmpty || parametersSkipOnEmpty.forall(param => vars.getOrElse(param,"").isEmpty)) {
        Future.successful(vars)
      } else {

        val authenticationRequest = createRequest(authenticationMethod,
          authenticationEndpoint,
          parametersToAuthentication,
          vars)
          .mapHeaders(_ ++ copiedHeaders)

        log.debug(s"AuthenticationRequest: $authenticationRequest")

        httpExtension.singleRequest(authenticationRequest) map { authenticationResponse =>

          log.debug(s"Authentication call successful: $authenticationResponse")

          vars ++ extractToMap(authenticationResponse, None, parametersFromAuthentication)
        }
      }

      authorizationRequest = createRequest(
        authorizationMethod, authorizationEndpoint, parametersToAuthorization, authenticationResult)

      _ <- Future.successful {
        log.debug(s"Parameter variables: $authenticationResult")
        log.debug(s"AuthorizationRequest: $authorizationRequest")
      }

      authorizationResponse <- sendAndReceiveAsAuthorizationResponse(authorizationRequest)

      _ <- Future
        .successful(log.debug(s"Authorization call successfull: ${authorizationResponse.toString.take(160)}..."))

      omiRequest <- Future.fromTry {
        rawOmiRequest.unwrapped
      }
      odfRequest = omiRequest match {
        case o: OdfRequest => o
        case _ => throw new Error("impossible")
      }

      filteredRequest <- filterODF(odfRequest, authorizationResponse)

      user = UserInfo(name = authenticationResult.get("username"))

    } yield optionToAuthResult(filteredRequest)(user)

    resultF.failed.map(log debug _.getMessage)

    Await.result(resultF, timeout.duration)
  }


  override def isAuthorizedForRawRequest(httpRequest: HttpRequest, rawRequest: String): AuthorizationResult = {
    val rawRequestWrapper = RawRequestWrapper(rawRequest, UserInfo())

    if (rawRequestWrapper.msgFormat.contains("odf"))
      isAuthorizedForOdfRequest(httpRequest, rawRequestWrapper)
    else
      Authorized(UserInfo(None)) // TODO: Cancel, Poll?

    //omiRequest match {
    //  case o: OdfRequest => isAuthorizedForOdfRequest(httpRequest, o)
    //  case _ => Authorized(UserInfo(None)) // TODO: Cancel, Poll?
    //}

    //return one of:
    // case class Authorized(user: UserInfo) extends AuthorizationResult {def instance: Authorized = this}
    // case class Unauthorized(user: UserInfo = UserInfo()) extends AuthorizationResult {def instance: Unauthorized = this}
    // case class Partial(authorized: JavaIterable[Path], user: UserInfo) extends AuthorizationResult
  }
}
