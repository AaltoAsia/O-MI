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

package authorization

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.optionalHeaderValue
import authorization.Authorization.{AuthorizationExtension, CombinedTest, PermissionTest, UnauthorizedEx}
import http.OmiConfigExtension
import types.OmiTypes._

import scala.util.{Failure, Success}


/** EduPersonPrincipalName, used as user identifier */
case class Eppn(user: String)


/**
  * SAML authorization using http headers got from some reverse-proxying server (e.g. nginx, apache)
  * preferably running on the same computer (for security reasons).
  * Authorizes PermissiveRequests for all users who are specified by EPPN in config whitelist
  * EPPNs are usually in format "username@organizationdomain"
  */
trait SamlHttpHeaderAuth extends AuthorizationExtension {
  private type User = Option[Eppn]
  val settings: OmiConfigExtension

  private[this] lazy val whitelistedUsers: Vector[Eppn] = {
    val tmp = settings.inputWhiteListUsers.map(Eppn(_))

    log.info(s"O-MI node is configured to allow SAML users: $tmp")
    if (tmp.nonEmpty)
      log
        .info("Make sure that you have SAML service provider setup correctly, otherwise you may have a security issue!")

    tmp
  }

  /**
    * Select header with the right data in it.
    * EduPersonPrincipalName
    * Is it uppercase? Docs say it depends on tool.
    */
  def headerSelector(header: HttpHeader): Boolean =
    header.name == "HTTP_eppn" || header.name == "HTTP_EPPN"

  private def extractUserData: Directive1[User] = optionalHeaderValue(header =>
    if (headerSelector(header))
      Some(Eppn(header.value))
    else
      None
  )

  private def hasPermission: User => PermissionTest = {
    case u@Some(user) => (wrap: RequestWrapper) =>
      wrap.unwrapped flatMap {

        case r: PermissiveRequest =>

          val result = whitelistedUsers contains user

          if (result) {
            log.info(s"Authorized user: $u for ${r.toString.take(80)}...")
            Success((r, r.user.copy(name = Some(user.user))))
          } else {
            log.warn(s"Unauthorized user: $u")
            Failure(UnauthorizedEx())
          }

        case _ => Failure(UnauthorizedEx())
      }
    case _ => { _ => Failure(UnauthorizedEx()) }
  }

  abstract override def makePermissionTestFunction: CombinedTest =
    combineWithPrevious(
      super.makePermissionTestFunction,
      extractUserData map hasPermission)

}
