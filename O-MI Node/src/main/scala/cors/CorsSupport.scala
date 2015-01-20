package cors

import spray.http._
import spray.routing._
import spray.http.HttpHeaders._
 
trait CORSDirectives  { this: HttpService =>
  private def respondWithCORSHeaders(origin: String) =
    respondWithHeaders(     
      HttpHeaders.`Access-Control-Allow-Origin`(SomeOrigins(List(origin))),
      HttpHeaders.`Access-Control-Allow-Credentials`(true)
    )
  private def respondWithCORSHeadersAllOrigins =
    respondWithHeaders(     
      HttpHeaders.`Access-Control-Allow-Origin`(AllOrigins),
      HttpHeaders.`Access-Control-Allow-Credentials`(true)
    )
 
  def corsFilter(origins: List[String])(route: Route) =
    if (origins.contains("*"))
      respondWithCORSHeadersAllOrigins(route)
    else
      optionalHeaderValueByName("Origin") {
        case None =>
          route       
        case Some(clientOrigin) => {
          if (origins.contains(clientOrigin))
            respondWithCORSHeaders(clientOrigin)(route)
          else {
            // Maybe, a Rejection will fit better
            complete(StatusCodes.Forbidden, "Invalid origin")
          }     
        }
      }
}

trait DefaultCORSDirectives { this: Directives =>
  def defaultCORSHeaders = respondWithHeaders(
      `Access-Control-Allow-Origin`(AllOrigins),
      `Access-Control-Allow-Methods`(HttpMethods.GET, HttpMethods.POST, HttpMethods.OPTIONS, HttpMethods.DELETE,
      HttpMethods.CONNECT, HttpMethods.DELETE, HttpMethods.HEAD, HttpMethods.PATCH, HttpMethods.PUT, HttpMethods.TRACE),
      `Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host," +
    " Referer, User-Agent, Overwrite, Destination, Depth, X-Token, X-File-Size, If-Modified-Since, X-File-Name, Cache-Control"),
      `Access-Control-Allow-Credentials`(true),
      `Access-Control-Max-Age`(3600)
    )
}