package types
package OmiTypes

import java.net.InetAddress
import scala.util.Try
import akka.http.scaladsl.model.Uri

import Callback._
/**
 * Contains information for sending callbacks for a request or subscription
 */
sealed trait Callback{ 
  def address: String 
    override def equals( any: Any) : Boolean ={
      any match {
        case other: Callback => other.address == address//for testing
        case _ => this == any
      }
    }
  val defined: Boolean = false
}

final case class RawCallback( val address: String ) extends Callback
sealed trait DefinedCallback extends Callback{
  final override val defined: Boolean = true
}

final case class CurrentConnectionCallback(val identifier: ConnectionIdentifier) extends DefinedCallback{
  val address: String = "0"
}

final case class HTTPCallback(val uri: Uri) extends DefinedCallback{
  val address: String = uri.toString
}

final case class RawCallbackFound(msg: String) extends Exception(msg)
object Callback {

  type ConnectionIdentifier = Int
  def tryHTTPUri(address: String): Try[Uri] = {
    Try{
      val uri = Uri(address)
      val hostAddress = uri.authority.host.address
      // Test address validity (throws exceptions when invalid)
      val ipAddress = InetAddress.getByName(hostAddress)
      val scheme = uri.scheme
      val httpSchemas = Vector("http", "https")
      if( httpSchemas.contains(scheme))
        uri
      else
        throw new Exception(s"$scheme is not supported. Only http and https are supported schemas for callback.")
    }  
  }
  /*
  def toDefined( rcb: RawCallback, connection: ConnectionIdintifier): DefinedCallback = {
    val httpSchemas = Vector("http", "https")
    rcb.address match{
      case "0" => CurrentConnectionCallback(connection) 
      case addr if isValidUri(uri) => 
        val uri = Uri(addr) 
        uri.schema match {
          case schema if httpSchema.contains(schema) =>
            HTTPCallback(uri)
        }
      case
    
    }
  }
  */

}
