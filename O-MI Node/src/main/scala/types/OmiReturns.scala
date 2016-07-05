package types
package OmiTypes
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}

sealed trait OmiReturn{
  val returnCode: String
  val description: Option[String]  
}
object OmiReturn{
  def apply( returnCode: String, description: Option[String] = None ): OmiReturn = {
    OmiReturnBase( returnCode, description)
  }
}
case class  OmiReturnBase( returnCode: String, description: Option[String] = None ) extends OmiReturn
object Returns{
  case class Success( description: Option[String] = None) extends OmiReturn{
    val returnCode: String = "200"
  }
  case class NotImplemented() extends OmiReturn{ 
    val returnCode: String  = "501"                    
    val description: Option[String] = Some("Not implemented.") 
  }
  case class Unauthorized() extends OmiReturn{
    val returnCode: String  = "401"
    val description: Option[String] = Some("Unauthorized.") 
  }
  case class InvalidRequest(msg: String = "") extends OmiReturn{
    val returnCode: String = "400"
    val description: Option[String] = Some(s"Bad request: $msg") 
  }
  case class InvalidCallback(callback: String ) extends OmiReturn{ 
    val returnCode: String = "400"
    val description: Option[String] = Some("Invalid callback address: " + callback)
  }
  case class NotFoundPaths( paths: Vector[Path] ) extends OmiReturn{
    val returnCode: String  = "404"
    val description: Option[String] = Some(s"Following O-DF paths not found: ${paths.mkString("\n")}")
  }

  case class NotFoundRequestIDs( requestIDs: Vector[Long] ) extends OmiReturn{
    val returnCode: String  = "404"
    val description: Option[String] = Some(s"Following requestIDs not found: ${requestIDs.mkString(", ")}.")
  }
  case class ParseErrors( errors: Vector[ParseError] ) extends OmiReturn{
    val returnCode: String  = "400"
    val description: Option[String] = Some(errors.mkString("\n"))
  }

  object InternalError{
    def apply(e: Throwable): InternalError = InternalError(e.getMessage())
  }

  case class InternalError( message: String ) extends OmiReturn{
    val returnCode: String  = "500"
    val description: Option[String] = Some(s"Internal server error: $message")
  }

  case class TimeOutError(message: String = "") extends OmiReturn{
    val returnCode: String  = "503"
    val description: Option[String] = Some(s"TTL timeout, consider increasing TTL or is the server overloaded? $message")
  }
}
