package authorization

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try
import scala.language.implicitConversions
import scala.collection.JavaConversions._
import org.prevayler.Prevayler
import akka.util.{ByteString, Timeout}
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.http.scaladsl.{Http, HttpExt}
import database.journal.Models.GetTree
//import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse, StatusCodes, HttpEntity, headers, Uri, FormData}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.client.RequestBuilding.RequestBuilder
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats,CustomSerializer, JString, JObject}
import org.json4s.native.JsonMethods._
import org.json4s.native.{Serialization => JsonSerialization}
import org.json4s.JsonDSL._
import org.json4s._
import agentSystem._
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.{Config, ConfigObject}
import akka.pattern.ask

import types.odf._
import types.Path
import types.OmiTypes.{OmiRequest, OdfRequest, UserInfo, RawRequestWrapper}
import http.OmiConfigExtension




/**
  * API response class for getting the permission data from external service.
  */
case class AuthorizationResponse(allow: List[Path], deny: List[Path])



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
  protected implicit val system : ActorSystem
  import system.dispatcher

  protected def bodyStringF(http: HttpMessage): Future[String] =
    Unmarshal(http.entity).to[String]

  protected def bodyString(http: HttpMessage)(implicit t: Timeout): String =
    Await.result(Unmarshal(http.entity).to[String], t.duration)

  protected def sendAndReceiveAsAuthorizationResponse(httpRequest: HttpRequest)(implicit t: Timeout): Future[AuthorizationResponse] =
    httpExtension.singleRequest(httpRequest)
      .flatMap { response =>
        if (response.status.isSuccess) {
          bodyStringF(response) map {(str) =>
            val json = parse(str) //.extract[AuthorizationResponse] doesnt work for some reason
            val allow = for {
              JObject(contents) <- json
              JField("allowed", JArray(list)) <- contents
              JString(pathStr) <- list
            } yield Path(pathStr)
            val deny  = for {
              JObject(contents) <- json
              JField("denied", JArray(list)) <- contents
              JString(pathStr) <- list
            } yield Path(pathStr)

            AuthorizationResponse(allow, deny)
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
    val settings : OmiConfigExtension,
    protected implicit val system : ActorSystem,
    protected override implicit val materializer : ActorMaterializer
  ) extends AuthApi with AuthApiJsonSupport {
  import settings.AuthApiV2._


  protected val log = LoggerFactory.getLogger(classOf[AuthAPIServiceV2])


  import system.dispatcher
  protected val httpExtension: HttpExt = Http(system)


  def filterODF(originalRequest: OdfRequest, filters: AuthorizationResponse): Future[Option[OmiRequest]] = {
    implicit val timeout = new Timeout(2 minutes)
    val odfTreeF: Future[ImmutableODF] = (hierarchyStore ? GetTree).mapTo[ImmutableODF]
    odfTreeF.map{odfTree =>
    val requestedTree = odfTree selectSubTree originalRequest.odf.getPaths
    val allowOdf = requestedTree selectSubTree filters.allow
    val filteredOdf = allowOdf removePaths filters.deny

    if (filteredOdf.isEmpty) None
    else Some(originalRequest replaceOdf filteredOdf)
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
        httpMessage.header[headers.Authorization].map(_.value.drop(from.length +1))

      case "omienvelope" => 
        rawOmi.flatMap(_.omiEnvelope.attr(from))

      case "headers" =>
        httpMessage.headers.find(from == _).map(_.value)

      case "query" =>
        httpMessage match {
          case r: HttpRequest => r.uri.query().get(from)
          case _ => None
        }

      case "cookie" =>
        httpMessage match {
          case r: HttpRequest => r.cookies.find(_.name == from).map(_.value)
          case r: HttpResponse => r.headers.collect {
            case c: headers.`Set-Cookie` if c.cookie.name == from => c.cookie.value
          }.headOption
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

  type VariableMap = Map[String,String]

  protected def extractToMap(httpRequest: HttpMessage, rawOmi: Option[RawRequestWrapper], confObject: ParameterExtraction)(implicit t: Timeout): VariableMap = {
    for {
      (key, value) <- confObject
      (paramKey, variable) <- value
      paramOption = extractParameter(httpRequest, rawOmi, key, paramKey)
      if (paramOption.isDefined)
    } yield (variable, paramOption.get) // get: None checked on the previous line
  }

  protected def createRequest(base: RequestBuilder, baseUri: Uri, confObject: ParameterExtraction, variables: VariableMap): HttpRequest = {
    def mapGet(context: String): Map[String,String] =
      confObject.get(context).getOrElse(Map.empty)

    def keyValues(context: String): Seq[(String,String)] = for {
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
      if (cookiePairs.nonEmpty) Seq(headers.Cookie(cookiePairs:_*))
      else Seq.empty
    }
 
    val authHeader = for {
      (key, variable) <- mapGet("authorizationheader").headOption.toSeq
      value <- variables.get(variable).toSeq
    } yield headers.RawHeader("Authorization", s"$key $value")

    val json = keyValues("jsonbody").foldLeft(JObject()){
      (a: JObject, b: (String,String)) => a ~ b
    }
    
    val formdataQuery = Uri.Query(keyValues("form-urlencoded"): _*)

    val req = base(uri).withHeaders((extraHeaders ++ authHeader ++ cookies):_*)

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

  protected def isAuthorizedForOdfRequest(httpRequest: HttpRequest, rawOmiRequest: RawRequestWrapper): AuthorizationResult = {

    implicit val timeout = Timeout(rawOmiRequest.handleTTL)

    log.debug(s"request body ${bodyString(httpRequest)}")
    log.debug(s"message body ${bodyString(httpRequest: HttpMessage)}")

    val requestType = (rawOmiRequest.requestVerb match {
      case RawRequestWrapper.MessageType.Response => RawRequestWrapper.MessageType.Write
      case x => x
    }).name

    val vars = extractToMap(httpRequest, Some(rawOmiRequest), parametersFromRequest) +
      ("requestTypeChar" -> requestType.head.toString) +
      ("requestType" -> requestType) ++
      parametersConstants

    val copiedHeaders = httpRequest.headers.filter(omiHttpHeadersToAuthentication contains _.lowercaseName)


    val resultF = for { 

      authenticationResult <-
        if (authenticationEndpoint.isEmpty) {
          Future.successful(vars)
        } else {

          val authenticationRequest = createRequest(authenticationMethod, authenticationEndpoint, parametersToAuthentication, vars)
            .mapHeaders(_ ++ copiedHeaders)

          log.debug(s"AuthenticationRequest: $authenticationRequest")

          httpExtension.singleRequest(authenticationRequest) map {authenticationResponse =>

            log.debug(s"Authentication call successful: $authenticationResponse")

            vars ++ extractToMap(authenticationResponse, None, parametersFromAuthentication)
          }
        }

      authorizationRequest = createRequest(
        authorizationMethod, authorizationEndpoint, parametersToAuthorization, authenticationResult)

      _ <- Future.successful{
        log.debug(s"AuthorizationRequest: $authorizationRequest")
      }

      authorizationResponse <- sendAndReceiveAsAuthorizationResponse(authorizationRequest)

      _ <- Future.successful(log.debug(s"Authorization call successfull: ${authorizationResponse.toString.take(160)}..."))

      omiRequest <- Future.fromTry{rawOmiRequest.unwrapped}
      odfRequest = omiRequest match {
        case o: OdfRequest => o
        case _ => throw new Error("impossible")
      }

      filteredRequest <- filterODF(odfRequest, authorizationResponse)

      user = UserInfo(name=authenticationResult.get("username"))

    } yield optionToAuthResult(filteredRequest)(user)

    resultF.failed.map(log debug _.getMessage)
    
    Await.result(resultF, timeout.duration)
  }


  override def isAuthorizedForRawRequest(httpRequest: HttpRequest, rawRequest: String): AuthorizationResult = {
    val rawRequestWrapper = RawRequestWrapper(rawRequest, UserInfo())

    import RawRequestWrapper.MessageType._
    if (rawRequestWrapper.msgFormat == Some("odf"))
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
