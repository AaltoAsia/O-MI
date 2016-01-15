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

/**
 * Implement this interface and register the class through AuthApiProvider
 */
trait AuthApi {
  /** This can be overridden or isAuthorizedForType can be overridden instead.
   *  @return True if user is authorized
   */
  def isAuthorizedForRequest(httpRequest: HttpRequest, omiRequest: OmiRequest): Boolean = {
    omiRequest match {
      case odfRequest: OdfRequest =>

        val paths = OdfTypes.getLeafs(odfRequest.odf) map (_.path) // todo: refactor getLeafs to member lazy to re-use later

        odfRequest match {
          case r: PermissiveRequest =>  // Write or Response
            isAuthorizedForType(httpRequest, true, paths)

          case r => // All else odf containing requests (reads)
            isAuthorizedForType(httpRequest, false, paths)

      }
      case _ => false
    }
  }


  /** This can be overridden or isAuthorizedForType can be overridden instead.
   *  @param httpRequest http headers and other data as they was received by Spray.
   *  @param isWrite True if the request requires write permissions, false if it's read only. 
   *  @param paths O-DF paths to all of the requested or to be written InfoItems.
   *  @return True if user is authorized, false if unauthorized
   */
  def isAuthorizedForType(httpRequest: HttpRequest, isWrite: Boolean, paths: JavaIterable[Path]): Boolean

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
      authorizationSystems exists {_.isAuthorizedForRequest(httpRequest, omiRequest)}
    }
  )
}

