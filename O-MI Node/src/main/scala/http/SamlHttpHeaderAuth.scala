package http

import spray.routing.Directives.optionalHeaderValue
import scala.collection.JavaConversions.collectionAsScalaIterable

import types.OmiTypes._
import Boot.settings
import Boot.system.log

case class Eppn(user: String)

trait SamlHttpHeaderAuth extends AuthorizationExtension[Option[Eppn]] {
  private[this] val whitelistedUsers: Vector[String] = settings.inputWhiteListUsers.toVector

  def extractUserData = optionalHeaderValue( header =>
     // Is it uppercase? Docs say it depends on tool.
    if (header.name == "HTTP_eppn" || header.name == "HTTP_EPPN")
      Some(Eppn(header.value))
    else
      None
  )

  def hasPermission = {
    case u @ Some(Eppn(user)) => {

      // all requests are allowed
      case r : PermissiveRequest =>

        val result = whitelistedUsers contains user

        if (result) {
          log.info(s"Authorized user: ${userToString(u)} for ${r.toString.take(80)}...")
        } else {
          log.info(s"Unauthorized user: ${userToString(u)}")
        }

        result


      case _ => false
    }
    case _ =>
      {_ =>  false}
  }

}
