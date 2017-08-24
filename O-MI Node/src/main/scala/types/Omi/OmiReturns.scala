package types
package omi

import parsing.xmlGen.scalaxb.DataRecord
import types.odf._
import parsing.xmlGen.xmlTypes

object ReturnCode extends Enumeration{
  type ReturnCode = String
  val Success = "200"
  
  val Invalid = "400"
  val Unauthorized = "401"
  val NotFound = "404"
  
  val InternalError = "500"
  val NotImplemented = "501"
  val Timeout = "503"
}
import ReturnCode._

trait JavaOmiReturn{
  def descriptionAsJava : String 
}

class OmiReturn(
  val returnCode: ReturnCode,
  val description: Option[String] = None
) extends JavaOmiReturn{
  override def equals(other: Any): Boolean ={
    other match{
      case ret: OmiReturn => ret.returnCode == returnCode && ret.description == description
      case any: Any => any == this
    }
  }
  def descriptionAsJava: String = description.map{ str => s"Success: $str"}.getOrElse("Success.")
  def unionableWith(other: OmiReturn) : Boolean = {
    println( s"Checking equality for ${this.getClass} and ${other.getClass}" )
    this.getClass == other.getClass
  }
  def toReturnType: xmlTypes.ReturnType ={
    xmlTypes.ReturnType(
      "",
      Map(("@returnCode" -> DataRecord(returnCode)),("@description" -> DataRecord(description)))
    )
  }
}

object OmiReturn{
  def apply(returnCode: ReturnCode, description: Option[String] = None) : OmiReturn ={
    new OmiReturn(returnCode,description)
  }
}



object Returns{

  object ReturnTypes{
    trait Invalid { parent: OmiReturn =>
      override final val  returnCode = ReturnCode.Invalid
    }

    trait Successful { parent: OmiReturn =>
      override final val returnCode = ReturnCode.Success
    }
    trait NotFound { parent: OmiReturn =>
      override final val returnCode = ReturnCode.NotFound
    }
    trait NotImplemented { parent: OmiReturn =>
      override final val returnCode = ReturnCode.NotImplemented 
    }
    trait Unauthorized { parent: OmiReturn =>
      override final val returnCode = ReturnCode.Unauthorized
    }
    trait Timeout { parent: OmiReturn =>
      override final val returnCode = ReturnCode.Timeout
    }
    trait InternalError { parent: OmiReturn =>
      override final val returnCode = ReturnCode.InternalError
    }
  }

  case class SubscribedPathsNotFound( 
    paths: Vector[Path] 
  ) extends  OmiReturn(ReturnCode.NotFound) with ReturnTypes.NotFound {
    override val description : Option[String] = Some(s"Following paths not found but are subscribed:"+paths.mkString("\n"))
  }

  case class NotFoundPaths() extends  OmiReturn(ReturnCode.NotFound) with ReturnTypes.NotFound {
    override val description : Option[String] = Some(s"Some parts of O-DF not found. msg element contains missing O-DF structure.")
  }
  
  case class NotFoundRequestIDs() extends  OmiReturn(ReturnCode.NotFound) with ReturnTypes.NotFound {
    override val description : Option[String] = Some(s"Some requestIDs were not found.")
  }

  case class NotFound(override val description: Option[String]) extends OmiReturn(ReturnCode.NotFound, description) with ReturnTypes.NotFound
  
  case class Success( 
    override val description: Option[String] = None
  ) extends OmiReturn(ReturnCode.Success) with ReturnTypes.Successful{}
  
  case class NotImplemented(
    val feature: Option[String] = None
  ) extends OmiReturn(ReturnCode.NotImplemented) with ReturnTypes.NotImplemented {
    override val description: Option[String] = feature.map{ 
      str => s"Not implemented: $str "
    }.orElse(Some("Not implemented.")) 
  }
  
  case class Unauthorized(
    val feature: Option[String] = None
  ) extends OmiReturn(ReturnCode.Unauthorized) with ReturnTypes.Unauthorized {
    override val description: Option[String] = feature.map{ 
      str => s"Unauthorized: $str"
    }.orElse(Some("Unauthorized.")) 
  }

  case class InvalidRequest(
    val message: Option[String] = None
  ) extends OmiReturn(ReturnCode.Invalid) with ReturnTypes.Invalid {
    override val description: Option[String] = message.map{ 
      str => s"Invalid request: $str"
    }.orElse(Some("Invalid request.")) 
  }

  case class InvalidCallback(
    val callback: Callback, 
    val reason: Option[String] = None 
  ) extends OmiReturn(ReturnCode.Invalid)  with ReturnTypes.Invalid { 
    override val description: Option[String] = Some(
      "Invalid callback address: " + callback + reason.map{ str => ", reason: " + str}.getOrElse("")
    )
  }

  case class ParseErrors(
    val errors: Vector[ParseError]
  ) extends OmiReturn(ReturnCode.Invalid) with ReturnTypes.Invalid {
    override val description: Option[String] = Some(
      errors.map{
        error => error.getMessage
      }.mkString(",\n"))
  }

  case class InternalError(
    val message: Option[String] = None
  ) extends OmiReturn(ReturnCode.InternalError) with ReturnTypes.InternalError {
    override val description: Option[String] = message.map{ 
      msg => s"Internal error: $msg"
    }.orElse(Some("Internal error."))
  }
  object InternalError {
    def apply( t: Throwable ) : InternalError = new InternalError( Some(t.getMessage) )
    def apply( msg: String ) : InternalError = new InternalError( Some(msg) )
  }


  case class TTLTimeout(
    val message: Option[String] = None
  ) extends OmiReturn(ReturnCode.Timeout) with ReturnTypes.Timeout {
    override val description: Option[String] = message.map{ msg =>
      s"TTL timeout, consider increasing TTL or is the server overloaded? $msg"
    }.orElse( Some(s"TTL timeout, consider increasing TTL or is the server overloaded?") )
  }

  case class Timeout(
    val message: Option[String] = None
  ) extends OmiReturn(ReturnCode.Timeout) with ReturnTypes.Timeout {
    override val description: Option[String] = message.map{ msg =>
      s"Timeout: $msg"
    }.orElse(Some("Timeout."))
  }
}
