package http

import spray.routing.Directives.extract
import spray.routing.Directive1
import spray.http.HttpRequest
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import java.lang.{ Iterable => JavaIterable }

import types.Path
import types.OmiTypes._
import types.OdfTypes
import Authorization.AuthorizationExtension

import scala.collection.mutable.Buffer


sealed trait AuthorizationResult
case object Authorized extends AuthorizationResult {def instance = this}
case object Unauthorized extends AuthorizationResult {def instance = this}
case class Partial(authorized: JavaIterable[Path]) extends AuthorizationResult

/**
 * Implement this interface and register the class through AuthApiProvider
 */
trait AuthApi {


  /** This can be overridden or isAuthorizedForType can be overridden instead.
   *  @return True if user is authorized
   */
  def isAuthorizedForRequest(httpRequest: HttpRequest, omiRequest: OmiRequest): AuthorizationResult = {
    omiRequest match {
      case odfRequest: OdfRequest =>

        val paths = OdfTypes.getLeafs(odfRequest.odf) map (_.path) // todo: refactor getLeafs to member lazy to re-use later

        odfRequest match {
          case r: PermissiveRequest =>  // Write or Response
            isAuthorizedForType(httpRequest, true, paths)

          case r => // All else odf containing requests (reads)
            isAuthorizedForType(httpRequest, false, paths)

      }
      case _ => Unauthorized
    }
  }


  /** This can be overridden or isAuthorizedForType can be overridden instead.
   *  @param httpRequest http headers and other data as they was received by Spray.
   *  @param isWrite True if the request requires write permissions, false if it's read only. 
   *  @param paths O-DF paths to all of the requested or to be written InfoItems.
   *  @return True if user is authorized, false if unauthorized
   */
  def isAuthorizedForType(httpRequest: HttpRequest, isWrite: Boolean, paths: JavaIterable[Path]): AuthorizationResult

}

trait AuthApiProvider extends AuthorizationExtension {
  private[this] val authorizationSystems: Buffer[AuthApi] = Buffer()

  /**
   * Register authorization system that tells if the request is authorized.
   * Registration should be done once.
   */
  def registerApi(newAuthSystem: AuthApi) = authorizationSystems += newAuthSystem


  // AuthorizationExtension implementation
  abstract override def makePermissionTestFunction = combineWithPrevious(
    super.makePermissionTestFunction,
    extract {context => context.request} map {(httpRequest: HttpRequest) => (omiRequest: OmiRequest) =>
      authorizationSystems exists {authApi =>
        authApi.isAuthorizedForRequest(httpRequest, omiRequest) match {
          case Unauthorized => false
          case Authorized => true
          case Partial(paths) => throw new NotImplementedError(
            s"Partial authorization granted for ${paths.mkString(", ")}, BUT not yet implemented in O-MI node.")
        }
      }
    }
  )
}

