package types
package OmiTypes

object Returns{
case class Success(description: Option[String] = None) extends OmiReturn("200", description)
case class NotImplemented() extends OmiReturn( "501", Some("Not implemented") )
case class Unauthorized() extends OmiReturn( "401", Some("Unauthorized") )
case class InvalidRequest(msg: String = "") extends OmiReturn( "400", Some(s"Bad request: $msg") )
case class InvalidCallback(callback: String ) extends OmiReturn( "400",Some("Invalid callback address: " + callback))
case class NotFoundPaths( paths: Vector[Path] ) extends OmiReturn(
  "404",
  Some(s"Following O-DF paths not found: ${paths.mkString("\n")}")
)

case class NotFoundRequestIDs( requestIDs: Vector[Long] ) extends OmiReturn(
  "404",
  Some(s"Following requestIDs not found: ${requestIDs.mkString(", ")}.")
)
case class ParseErrors( errors: Vector[ParseError] ) extends OmiReturn(
  "400",
  Some(errors.mkString("\n"))
)

object InternalError{
  def apply(e: Throwable): InternalError = InternalError(e.getMessage())
}

case class InternalError( message: String ) extends OmiReturn("500", Some(s"Internal server error: $message"))

case class TimeOutError(message: String = "") extends OmiReturn(
  "503",
  Some(s"TTL timeout, consider increasing TTL or is the server overloaded? $message")
)
}
