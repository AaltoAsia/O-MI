package authorization

import scala.concurrent.{Future,Await}
import scala.concurrent.duration._

import org.prevayler._
import akka.util.{Timeout, ByteString}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.http.scaladsl.{ Http, HttpExt}
//import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes, HttpEntity}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.client.RequestBuilding.{Post,Get}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshalling.Marshal
import de.heikoseeberger.akkahttpjson4s.Json4sSupport
import org.json4s.{DefaultFormats, Formats,CustomSerializer, JString}
import org.json4s.native
import agentSystem._
import org.slf4j.{Logger, LoggerFactory}


import database.GetTree
import database.OdfTree
import types.odf._
import types.Path
import types.OmiTypes.{OmiRequest, OdfRequest, UserInfo}
import http.OmiConfigExtension

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
  implicit val jsonSerialization = org.json4s.native.Serialization
  implicit val json4sFormats: Formats = DefaultFormats + new PathSerializer // + new NoneSerializer
  implicit def jsonMarshaller[T <: AnyRef] = marshaller[T](jsonSerialization, json4sFormats)
}

/**
  * API call class for getting the permission data from external service.
  */
//case class AuthorizationRequest()

/**
  * API response class for getting the permission data from external service.
  */
case class AuthorizationResponse(allow: List[Path], deny: List[Path])

/**
  * API response class for validating user credentials from external service. Includes all usual keynames.
  */
case class AuthenticationResponse(
  email: Option[String],
  userid: Option[String],
  user: Option[String],
  isadmin: Option[Boolean]
) {
  def userResult: String = (email orElse userid orElse user).getOrElse("default")
}

case class AuthorizationRequest(username: String, request: String)

/**
  * Version 2 of AuthAPI service. It provides functionality of the internal AuthAPI interface to external authorization services.
  * This V2 has different interface to allow easier partial authorization by having "deny" rules in addition to "allow" rules.
  */
class AuthAPIServiceV2(
    val hierarchyStore: Prevayler[OdfTree],
    val settings : OmiConfigExtension,
    protected implicit val system : ActorSystem,
    protected implicit val materializer : ActorMaterializer
  ) extends AuthApi with AuthApiJsonSupport {
  import settings.AuthApiV2._


  protected val log = LoggerFactory.getLogger(classOf[AuthAPIServiceV2])

  val timeout = 3.seconds

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
        if (response.status.isSuccess || response.status == StatusCodes.NotFound) f(response)
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

  // This temporary implementation because of problems with json4s serializing case class AuthorizationRequest
  private def authorizationJson(a:String, b:String) =
    ByteString(
      "{\"username\": \""+a+
      "\", \"request\" :\""+b+"\"}"
      )

  protected def isAuthorizedForOdfRequest(httpRequest: HttpRequest, odfRequest: OdfRequest): AuthorizationResult = {
    val odfTree = hierarchyStore execute GetTree()

    val authenticationRequest = Get(
      authenticationEndpoint.copy(rawQueryString=httpRequest.uri.rawQueryString)
    ).withHeaders(httpRequest.headers.filter(omiHttpHeadersToAuthentication contains _.lowercaseName))


    val resultF = for { 
      authenticationResponse <- sendAndReceiveAs[AuthenticationResponse](authenticationRequest)


      // This temporary implementation because of problems with json4s serializing case class AuthorizationRequest
      authorizationRequest = Post(
        authorizationEndpoint,
          HttpEntity.Strict(`application/json`,
            authorizationJson(authenticationResponse.userResult,odfRequest.requestVerb.name.head.toString)
          )
      )
      //authorizationRequest = Post(
      //  authorizationEndpoint,
      //    AuthorizationRequest(authenticationResponse.userResult,odfRequest.requestVerb.name.head.toString)
      //  )
      //)

      _ <- Future.successful{
        log.debug(s"Authentication call successfull: $authenticationResponse")
        log.debug(s"AuthorizationRequest: $authorizationRequest")
      }

      authorizationResponse <- sendAndReceiveAs[AuthorizationResponse](authorizationRequest)

      _ <- Future.successful(log.debug(s"Authorization call successfull: ${authorizationResponse.toString.take(160)}..."))

      filteredRequest = filterODF(odfRequest, authorizationResponse)

      user = UserInfo(name=authenticationResponse.email)

    } yield optionToAuthResult(filteredRequest)(user)

    resultF.failed.map(log debug _.getMessage)
    
    Await.result(resultF, timeout)
  }


  override def isAuthorizedForRequest(httpRequest: HttpRequest, omiRequest: OmiRequest): AuthorizationResult = {
    omiRequest match {
      case o: OdfRequest => isAuthorizedForOdfRequest(httpRequest, o)
      case _ => Authorized(UserInfo(None)) // TODO: Cancel, Poll?
    }

    //return one of:
    // case class Authorized(user: UserInfo) extends AuthorizationResult {def instance: Authorized = this}
    // case class Unauthorized(user: UserInfo = UserInfo()) extends AuthorizationResult {def instance: Unauthorized = this}
    // case class Partial(authorized: JavaIterable[Path], user: UserInfo) extends AuthorizationResult
  }
}
