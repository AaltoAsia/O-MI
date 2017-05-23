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
import scala.collection.mutable
import scala.collection.mutable.Buffer
import scala.util.{Failure, Success, Try}

import database._
import http.Authorization.{UnauthorizedEx, AuthorizationExtension, CombinedTest}
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives.extract
import types.OdfTypes._
import types.OmiTypes._
import types.Path


sealed trait AuthorizationResult{
  def user: UserInfo
}
case class Authorized(user: UserInfo) extends AuthorizationResult {def instance = this}
case class Unauthorized(user: UserInfo = UserInfo()) extends AuthorizationResult {def instance = this}
case class Partial(authorized: JavaIterable[Path], user: UserInfo) extends AuthorizationResult
/**
 * Wraps a new O-MI request that is potentially modified from the original to pass authorization.
 * Can be used instead of [[Partial]] to define partial authorization. 
 */
case class Changed(authorizedRequest: RawRequestWrapper, user: UserInfo) extends AuthorizationResult

/**
 * Implement one method of this interface and register the class through AuthApiProvider.
 */
trait AuthApi {


  /**
   * This can be overridden or isAuthorizedForType can be overridden instead. Has scala default implementation for
   * calling [[isAuthorizedForType]] function correctly.
   *  @param httpRequest http headers and other data as they were received to O-MI Node.
   */
  def isAuthorizedForRequest(httpRequest: HttpRequest, omiRequest: OmiRequest): AuthorizationResult = {
    omiRequest match {
      case odfRequest: OdfRequest =>

        val paths = getLeafs(odfRequest.odf) map (_.path) // todo: refactor getLeafs to member lazy to re-use later

        odfRequest match {
          case r: PermissiveRequest =>  // Write or Response
            isAuthorizedForType(httpRequest, isWrite = true, paths)

          case r : OdfRequest => // All else odf containing requests (reads)
            isAuthorizedForType(httpRequest, isWrite = false, paths)

      }
      case _ => Unauthorized()
    }
  }


  /** This can be overridden or isAuthorizedForType can be overridden instead.
   *  @param httpRequest http headers and other data as they were received to O-MI Node.
   *  @param isWrite True if the request requires write permissions, false if it's read only.
   *  @param paths O-DF paths to all of the requested or to be written InfoItems.
   */
  def isAuthorizedForType(httpRequest: HttpRequest, isWrite: Boolean, paths: JavaIterable[Path]): AuthorizationResult = {
    Unauthorized()
  }

  /**
   * This is used if the parser is wanted to be skipped, e.g. for forwarding to the 
   * original request to some authentication/authorization service.
   *
   *  @param httpRequest http headers and other data as they were received to O-MI Node.
   *  @param omiRequestXml contains the original request as received by the server.
   */
  def isAuthorizedForRawRequest(httpRequest: HttpRequest, omiRequestXml: String): AuthorizationResult = {
    Unauthorized()
  }
}

trait AuthApiProvider extends AuthorizationExtension {
  val singleStores: SingleStores

  private[this] val authorizationSystems: mutable.Buffer[AuthApi] = mutable.Buffer()


  /**
   * Register authorization system that tells if the request is authorized.
   * Registration should be done once.
   */
  def registerApi(newAuthSystem: AuthApi) = authorizationSystems += newAuthSystem


  // AuthorizationExtension implementation
  abstract override def makePermissionTestFunction: CombinedTest = combineWithPrevious(
    super.makePermissionTestFunction,
    extract {context => context.request} map {(httpRequest: HttpRequest) => (orgOmiRequest: RequestWrapper) =>

      // for checking if path is infoitem or object
      val currentTree = singleStores.hierarchyStore execute GetTree()

      // helper function
      def convertToWrapper: Try[AuthorizationResult] => Try[RequestWrapper] = {
        case Success(Unauthorized(user0)) => Failure(UnauthorizedEx())
        case Success(Authorized(user0)) => {
          orgOmiRequest.user = user0.copy(remoteAddress = orgOmiRequest.user.remoteAddress)
          log.debug(s"GOT USER:\nRemote: ${orgOmiRequest.user.remoteAddress.getOrElse("Empty")}\nName: ${orgOmiRequest.user.name.getOrElse("Empty")}")
          Success(orgOmiRequest)
        }
        case Success(Changed(reqWrapper,user0)) => {
          reqWrapper.user = user0
          orgOmiRequest.user = user0
          Success(reqWrapper)
        }
        case Success(Partial(maybePaths,user0)) => {
          orgOmiRequest.user = user0
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
              orgOmiRequest.unwrapped flatMap {
                case r: ReadRequest         => Success(r.copy(odf = newOdf))
                case r: SubscriptionRequest => Success(r.copy(odf = newOdf))
                case r: WriteRequest        => Success(r.copy(odf = newOdf))
                case r: ResponseRequest     => Success(r.copy(results =
                  OdfTreeCollection(r.results.head.copy(odf = Some(newOdf))) // TODO: make better copy logic?
                ))
                case r: AnyRef => throw new NotImplementedError(
                  s"Partial authorization granted for ${maybePaths.mkString(", ")}, BUT request '${r.getClass.getSimpleName}' not yet implemented in O-MI node.")
              }
            case _ => Failure(UnauthorizedEx())
          }
        }
        case f @ Failure(exception) =>
          log.error("Error while running AuthPlugins. => Unauthorized, trying next plugin", exception)
          Failure(UnauthorizedEx())
      }


      authorizationSystems.foldLeft[Try[RequestWrapper]] (Failure(UnauthorizedEx())) {(lastTest, nextAuthApi) =>
        lastTest orElse {

          lazy val rawReqResult = convertToWrapper(
            Try{nextAuthApi.isAuthorizedForRawRequest(httpRequest, orgOmiRequest.rawRequest)}
          )

          lazy val reqResult = 
            orgOmiRequest.unwrapped flatMap {omiReq =>
              convertToWrapper(
                Try{nextAuthApi.isAuthorizedForRequest(httpRequest, omiReq)}
              )
            }

          // Choose optimal test order
          orgOmiRequest match {
            case raw: RawRequestWrapper => rawReqResult orElse reqResult
            case other: RequestWrapper => reqResult orElse rawReqResult

          }
        }
      }
    }
  )
}

