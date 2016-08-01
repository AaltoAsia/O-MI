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

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.util.{Failure, Success}

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.server.Directive1
import akka.http.scaladsl.server.Directives.optionalHeaderValue

import types.OmiTypes._
import http.Authorization.{UnauthorizedEx, AuthorizationExtension, CombinedTest, PermissionTest}
import Boot.settings

// TODO: maybe move to Authorization package


/** EduPersonPrincipalName */
case class Eppn(user: String)


/**
 * SAML authorization using http headers got from some reverse-proxying server (e.g. nginx, apache)
 * preferrably running on the same computer (for security reasons).
 * Authorize [[PermissiveRequest]]s for all users who are specified by EPPN in config whitelist
 * EPPNs are usually in format "username@organizationdomain"
 */
trait SamlHttpHeaderAuth extends AuthorizationExtension {
  private type User = Option[Eppn]

  private[this] def whitelistedUsers: Vector[Eppn] =
    settings.inputWhiteListUsers.map(Eppn(_)).toVector

  log.info(s"O-MI node is configured to allow SAML users: $whitelistedUsers")
  if (whitelistedUsers.nonEmpty)
    log.info("Make sure that you have SAML service provider setup correctly, otherwise you may have a security issue!")

  /** 
   * Select header with the right data in it.
   * EduPersonPrincipalName
   * Is it uppercase? Docs say it depends on tool.
   */
  def headerSelector(header: HttpHeader): Boolean =
    header.name == "HTTP_eppn" || header.name == "HTTP_EPPN"

  private def extractUserData: Directive1[User] = optionalHeaderValue( header =>
    if (headerSelector(header))
      Some(Eppn(header.value))
    else
      None
  )

  private def hasPermission: User => PermissionTest = {
    case u @ Some(user) => (wrap: RequestWrapper) => wrap.unwrapped flatMap {

      case r : PermissiveRequest =>

        val result = whitelistedUsers contains user

        if (result) {
          log.info(s"Authorized user: $u for ${r.toString.take(80)}...")
          Success(r)
        } else {
          log.warn(s"Unauthorized user: $u")
          Failure(UnauthorizedEx())
        }

      case _ => Failure(UnauthorizedEx())
    }
    case _ =>
      {_ =>  Failure(UnauthorizedEx())}
  }

  abstract override def makePermissionTestFunction: CombinedTest =
    combineWithPrevious(
      super.makePermissionTestFunction,
      extractUserData map hasPermission)

}
