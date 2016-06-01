package http

import java.lang.{Iterable => JavaIterable}

import database._
import http.Authorization.{AuthorizationExtension, CombinedTest}
import spray.http.HttpRequest
import spray.routing.Directives.extract
import types.OdfTypes._
import types.OmiTypes._
import types.Path

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.collection.mutable.Buffer
import scala.util.{Failure, Success, Try}


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

        val paths = getLeafs(odfRequest.odf) map (_.path) // todo: refactor getLeafs to member lazy to re-use later

        odfRequest match {
          case r: PermissiveRequest =>  // Write or Response
            isAuthorizedForType(httpRequest, true, paths)

          case r : OdfRequest => // All else odf containing requests (reads)
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
  abstract override def makePermissionTestFunction: CombinedTest = combineWithPrevious(
    super.makePermissionTestFunction,
    extract {context => context.request} map {(httpRequest: HttpRequest) => (orgOmiRequest: OmiRequest) =>

      val currentTree = SingleStores.hierarchyStore execute GetTree()
      //authorizationSystems exists {authApi =>

      authorizationSystems.foldLeft[Option[OmiRequest]] (None) {(lastTest, nextAuthApi) =>
        lastTest orElse (
          Try{nextAuthApi.isAuthorizedForRequest(httpRequest, orgOmiRequest)} match {
            case Success(Unauthorized) => None
            case Success(Authorized) => Some(orgOmiRequest)
            case Success(Partial(maybePaths)) => 

              val newOdfOpt = for {
                paths <- Option(maybePaths) // paths might be null

                // Rebuild the request having only `paths`
                pathTrees = paths collect {
                  case path: Path =>              // filter nulls out
                    currentTree.get(path) match { // figure out is it InfoItem or Object
                      case Some(nodeType) => createAncestors(nodeType)
                      case None => OdfObjects()
                    }
                }

              } yield pathTrees.fold(OdfObjects())(_ union _)

              newOdfOpt match {
                case Some(newOdf) if (newOdf.objects.nonEmpty) =>
                  orgOmiRequest match {
                    case r: ReadRequest         => Some(r.copy(odf = newOdf))
                    case r: SubscriptionRequest => Some(r.copy(odf = newOdf))
                    case r: WriteRequest        => Some(r.copy(odf = newOdf))
                    case r: ResponseRequest     => Some(r.copy(results = 
                      asJavaIterable(Iterable(r.results.head.copy(odf = Some(newOdf)))) // TODO: make better copy logic?
                    ))
                    case r: AnyRef => throw new NotImplementedError(
                      s"Partial authorization granted for ${maybePaths.mkString(", ")}, BUT request '${r.getClass.getSimpleName}' not yet implemented in O-MI node.")
                  }
                case _ => None
              }
            case Failure(exception) =>
                log.error(exception, "While running AuthPlugins. => Unauthorized, trying next plugin")
                None
          }
        )
      }
    }
  )
}

