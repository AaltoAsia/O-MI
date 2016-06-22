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

import java.lang.{Iterable => JavaIterable}

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.collection.mutable.Buffer
import scala.util.{Failure, Success, Try}

import database._
import http.Authorization.{AuthorizationExtension, CombinedTest}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives.extract
import types.OdfTypes._
import types.OmiTypes._
import types.Path


sealed trait AuthorizationResult
case object Authorized extends AuthorizationResult {def instance = this}
case object Unauthorized extends AuthorizationResult {def instance = this}
case class Partial(authorized: JavaIterable[Path]) extends AuthorizationResult

/**
 * Implement one method of this interface and register the class through AuthApiProvider.
 */
trait AuthApi {


  /**
   * This can be overridden or isAuthorizedForType can be overridden instead.
   *  @param httpRequest http headers and other data as they were received to O-MI Node.
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
   *  @param httpRequest http headers and other data as they were received to O-MI Node.
   *  @param isWrite True if the request requires write permissions, false if it's read only. 
   *  @param paths O-DF paths to all of the requested or to be written InfoItems.
   *  @return True if user is authorized, false if unauthorized
   */
  def isAuthorizedForType(httpRequest: HttpRequest, isWrite: Boolean, paths: JavaIterable[Path]): AuthorizationResult

  /**
   * This is used if the parser is wanted to be skipped, e.g. for forwarding to the 
   * original request to some authentication/authorization service.
   *
   *  @param httpRequest http headers and other data as they were received to O-MI Node.
   */
  def isAuthorizedForRawRequest(httpRequest: HttpRequest, omiRequestXml: String): AuthorizationResult = ???

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
                      OdfTreeCollection(r.results.head.copy(odf = Some(newOdf))) // TODO: make better copy logic?
                    ))
                    case r: AnyRef => throw new NotImplementedError(
                      s"Partial authorization granted for ${maybePaths.mkString(", ")}, BUT request '${r.getClass.getSimpleName}' not yet implemented in O-MI node.")
                  }
                case _ => None
              }
            case Failure(exception) =>
                log.error("While running AuthPlugins. => Unauthorized, trying next plugin", exception)
                None
          }
        )
      }
    }
  )
}

