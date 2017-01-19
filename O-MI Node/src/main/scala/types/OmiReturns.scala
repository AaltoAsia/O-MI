package types
package OmiTypes
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}

object ReturnCode extends Enumeration{
  type ReturnCode = String
  val NotFound = "404"
  val Invalid = "400"
  val Success = "200"
  val NotImplemented = "501"
  val Unauthorized = "401"
  val Timeout = "503"
  val InternalError = "500"
}
import ReturnCode._

sealed trait OmiReturn {
  def returnCode: ReturnCode
  def description: Option[String] 
  
  /*
  def overrides(other: OmiReturn) : Boolean
  def isOverridedBy(other: OmiReturn) : Boolean
  */

  def unionableWith(other: OmiReturn) : Boolean = {this.getClass == other.getClass}
}

class BaseReturn( val returnCode: String, val description: Option[String]) extends OmiReturn{
  def union(other: OmiReturn): OmiReturn = this
}

object OmiReturn{
  def apply(returnCode: ReturnCode, description: Option[String]) : OmiReturn ={
    returnCode match {
      case Success => Returns.Success(description)
      /*
      case Invalid =>
      case NotFound =>
      case NotImplemented =>
      case Unauthorized =>
      case Timeout =>
      case InternalError => 
        */
      case base => new BaseReturn( returnCode,description)
    }
  }
}



object Returns{

sealed trait Invalid extends OmiReturn {
  override def returnCode = ReturnCode.Invalid
}

sealed trait Successful extends OmiReturn {
  override def  returnCode = ReturnCode.Success
}
sealed trait NotFound extends OmiReturn{
  override val returnCode = ReturnCode.NotFound
}

case class SubscribedPathsNotFound( paths: OdfTreeCollection[Path] ) extends NotFound{
  val description : Option[String] = Some(s"Following paths not found but are subscribed:"+paths.mkString("\n"))
}
  case class NotFoundPaths() extends NotFound{
    val description : Option[String] = Some(s"Some parts of O-DF not found. msg element contains missing O-DF structure.")
  }
  
  case class NotFoundRequestIDs() extends NotFound{
    val description : Option[String] = Some(s"Some requestIDs were not found.")
  }
  
  case class Success(description: Option[String] = None ) extends OmiReturn with Successful{}
  
  case class NotImplemented(feature: Option[String] = None) extends OmiReturn {
    def returnCode: ReturnCode  = ReturnCode.NotImplemented       
    def description: Option[String] = feature.map{ 
      str => s"$str is not implemented."
    }.orElse(Some("Not implemented.")) 
  }
  
  case class Unauthorized(feature: Option[String] = None) extends OmiReturn {
    def returnCode: ReturnCode  = ReturnCode.Unauthorized
    def description: Option[String] = feature.map{ 
      str => s"Unauthorized use of $str"
    }.orElse(Some("Unauthorized.")) 
  }

  case class InvalidRequest(message: Option[String] = None)  extends OmiReturn with Invalid {
    def description: Option[String] = message.map{ 
      str => s"Bad request: $str"
    }.orElse(Some("Bad request.")) 
  }

  case class InvalidCallback(callback: Callback, reason: Option[String] = None ) extends OmiReturn  with Invalid {
    def description: Option[String] = Some(
      "Invalid callback address: " + callback + reason.map{ str => ", reason: " + str}.getOrElse("")
    )
  }

  case class ParseErrors(errors: Vector[ParseError] ) extends OmiReturn with Invalid {
    def description: Option[String] = Some(errors.mkString(",\n"))
  }

  case class InternalError(message: Option[String] = None) extends OmiReturn {
    def returnCode: ReturnCode  = ReturnCode.InternalError
    def description: Option[String] = message.map{ 
      msg => s"Internal server error: $msg"
    }.orElse(Some("Internal server error."))
  }


  case class TimeOutError(message: Option[String] = None) extends OmiReturn {
    def returnCode: ReturnCode  = Timeout
    def description: Option[String] = message.map{ msg =>
      s"TTL timeout, consider increasing TTL or is the server overloaded? $msg"
    }.orElse( Some(s"TTL timeout, consider increasing TTL or is the server overloaded?") )
  }
}
