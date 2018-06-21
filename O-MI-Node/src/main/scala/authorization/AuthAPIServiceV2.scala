package authorization

import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import scala.util.Try
import scala.language.implicitConversions
import scala.collection.JavaConversions._

import org.prevayler._
import akka.util.{Timeout, ByteString}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.http.scaladsl.{ Http, HttpExt}
//import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.model.{HttpMessage, HttpRequest, HttpResponse, StatusCodes, HttpEntity, headers, Uri}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.client.RequestBuilding.{RequestBuilder,Post,Get}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats,CustomSerializer, JString, JObject}
import org.json4s.native.JsonMethods._
import org.json4s.native
import org.json4s.JsonDSL._
import agentSystem._
import org.slf4j.{Logger, LoggerFactory}
import com.typesafe.config.{Config, ConfigObject}


import database.GetTree
import database.OdfTree
import types.odf._
import types.Path
import types.OmiTypes.{OmiRequest, OdfRequest, UserInfo, RawRequestWrapper}
import http.OmiConfigExtension

trait Hngh {
  protected implicit val materializer: ActorMaterializer
  protected def bodyString(http: HttpMessage)(implicit t: Timeout): String =
    Await.result(Unmarshal(http.entity).to[String], t.duration)
}

trait AuthApiJsonSupport extends Json4sSupport {
  // Serialize None to null?
  //class NoneSerializer extends CustomSerializer[Option[_]](format =>
  //    ( { case JNull => None  }
  //    , { case None => JNull  }
  //    )
  //)
  class PathSerializer extends CustomSerializer[Path](format =>
      ( { case JString(s) => Path(s)  }
      , { case p => JString(p.toString)  }
      )
  )
  implicit val jsonSerialization = native.Serialization
  implicit val json4sFormats: Formats = DefaultFormats + new PathSerializer // + new NoneSerializer
  implicit def jsonMarshaller[T <: AnyRef] = marshaller[T](jsonSerialization, json4sFormats)
}


/**
  * API response class for getting the permission data from external service.
  */
case class AuthorizationResponse(allow: List[Path], deny: List[Path])


/**
  * Version 2 of AuthAPI service. It provides functionality of the internal AuthAPI interface to external authorization services.
  * This V2 has different interface to allow easier partial authorization by having "deny" rules in addition to "allow" rules.
  */
class AuthAPIServiceV2(
    val hierarchyStore: Prevayler[OdfTree],
    val settings : OmiConfigExtension,
    protected implicit val system : ActorSystem,
    protected override implicit val materializer : ActorMaterializer
  ) extends AuthApi with AuthApiJsonSupport with Hngh{
  import settings.AuthApiV2._


  protected val log = LoggerFactory.getLogger(classOf[AuthAPIServiceV2])


  import system.dispatcher
  protected val httpExtension: HttpExt = Http(system)


  // No error response handling
  //def sendAndReceiveAs[T: Manifest](httpRequest: HttpRequest): Future[T] =
  //      httpExtension.singleRequest(httpRequest)
  //        .flatMap{(response: HttpResponse) => unmarshaller[T].apply(response.entity)}


  def sendAndReceiveAs[T: Manifest](httpRequest: HttpRequest): Future[T] =
    sendAndReceive(httpRequest, response => unmarshaller[T].apply(response.entity))

  private def sendAndReceive[T](request: HttpRequest, f: HttpResponse => Future[T]): Future[T] =
    httpExtension.singleRequest(request)
      .flatMap { response =>
        //if (response.status.isSuccess || response.status == StatusCodes.NotFound) f(response)
        if (response.status.isSuccess) f(response)
        else Future.failed(new Exception(s"Request failed. Response was: $response"))
      }

  def filterODF(originalRequest: OdfRequest, filters: AuthorizationResponse): Option[OmiRequest] = {

    val allowOdf = originalRequest.odf selectSubTree filters.allow
    val filteredOdf = allowOdf removePaths filters.deny

    if (filteredOdf.isEmpty) None
    else Some(originalRequest replaceOdf filteredOdf)
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

    val query = Uri.Query((
      for {
        (key, variable) <- mapGet("query").toSeq
        value <- variables.get(variable).orElse(Some(variable)).toSeq
      } yield key -> value
    ): _*)
    val uri = baseUri.withQuery(query)

    val extraHeaders = for {
      (key, variable) <- mapGet("headers").toSeq
      value <- variables.get(variable).orElse(Some(variable)).toSeq // default to variable for adding static headers
    } yield headers.RawHeader(key, value)
 

    val authHeader = for {
      (key, variable) <- mapGet("authorizationheader").headOption.toSeq
      value <- variables.get(variable).orElse(Some(variable)).toSeq
    } yield headers.RawHeader("Authorization", s"$key $value")

    val entity = (for {
      (key, variable) <- mapGet("jsonbody").toSeq
      value <- variables.get(variable).orElse(Some(variable)).toSeq // default to variable for adding static values
    } yield (key -> value)).foldLeft(JObject()){
      (a: JObject, b: (String,String)) => a ~ b
    }

    val req = base(uri).withHeaders((extraHeaders ++ authHeader):_*)

    if (entity.obj.nonEmpty)
      req.withEntity(
        HttpEntity.Strict(`application/json`, ByteString(compact(render(entity))))
      )
    else req
  }

  protected def isAuthorizedForOdfRequest(httpRequest: HttpRequest, rawOmiRequest: RawRequestWrapper): AuthorizationResult = {
    val odfTree = hierarchyStore execute GetTree()

    implicit val timeout = Timeout(rawOmiRequest.handleTTL)

    log.debug(s"request body ${bodyString(httpRequest)}")
    log.debug(s"message body ${bodyString(httpRequest: HttpMessage)}")

    val requestType = (rawOmiRequest.messageType match {
      case RawRequestWrapper.MessageType.Response => RawRequestWrapper.MessageType.Write
      case x => x
    }).name

    val vars = extractToMap(httpRequest, Some(rawOmiRequest), parametersFromRequest) + 
      ("requesttypechar" -> requestType.head.toString) +
      ("requesttype" -> requestType)

    val copiedHeaders = httpRequest.headers.filter(omiHttpHeadersToAuthentication contains _.lowercaseName)

    val authenticationRequest = createRequest(Get, authenticationEndpoint, parametersToAuthentication, vars)
      .mapHeaders(_ ++ copiedHeaders)

    log.debug(s"AuthenticationRequest: $authenticationRequest")

    val resultF = for { 
      authenticationResponse <- httpExtension.singleRequest(authenticationRequest)

      authenticationResult = vars ++ extractToMap(authenticationResponse, None, parametersFromAuthentication)

      authorizationRequest = createRequest(
        Post, authorizationEndpoint, parametersToAuthorization, authenticationResult)

      _ <- Future.successful{
        log.debug(s"Authentication call successfull: $authenticationResponse")
        log.debug(s"AuthorizationRequest: $authorizationRequest")
      }

      authorizationResponse <- sendAndReceiveAs[AuthorizationResponse](authorizationRequest)

      _ <- Future.successful(log.debug(s"Authorization call successfull: ${authorizationResponse.toString.take(160)}..."))

      omiRequest <- Future.fromTry{rawOmiRequest.unwrapped}
      odfRequest = omiRequest match {
        case o: OdfRequest => o
        case _ => throw new Error("impossible")
      }

      filteredRequest = filterODF(odfRequest, authorizationResponse)

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
