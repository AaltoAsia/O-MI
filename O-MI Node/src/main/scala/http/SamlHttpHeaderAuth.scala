/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  https://github.com/AaltoAsia/O-MI/blob/master/LICENSE.txt

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package http

import spray.routing.Directives.optionalHeaderValue
import spray.routing.Directive1
import spray.http.HttpHeader
import scala.collection.JavaConversions.collectionAsScalaIterable

import types.OmiTypes._
import Boot.settings
import Boot.system.log
import Authorization.AuthorizationExtension

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

  private[this] val whitelistedUsers: Vector[Eppn] =
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

  private def hasPermission: User => OmiRequest => Boolean = {
    case u @ Some(user) => {

      case r : PermissiveRequest =>

        val result = whitelistedUsers contains user

        if (result) {
          log.info(s"Authorized user: $u for ${r.toString.take(80)}...")
        } else {
          log.warning(s"Unauthorized user: $u")
        }

        result


      case _ => false
    }
    case _ =>
      {_ =>  false}
  }

  abstract override def makePermissionTestFunction =
    combineWithPrevious(
      super.makePermissionTestFunction,
      extractUserData map hasPermission)

}
