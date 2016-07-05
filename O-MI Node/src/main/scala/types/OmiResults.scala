package types
package OmiTypes

import scala.concurrent.duration._
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}
import parsing.xmlGen.xmlTypes.{ObjectsType, OmiEnvelope}

/** 
 * Result of a O-MI request
 **/
trait OmiResult{
  val returnValue : OmiReturn
  val requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty
  val odf: Option[OdfTypes.OdfObjects] = None

  def copy(
    returnValue : OmiReturn = this.returnValue,
    requestIDs: OdfTreeCollection[Long ] = this.requestIDs,
    odf: Option[OdfTypes.OdfObjects] = this.odf
  ) : OmiResult = new OmiResultBase(returnValue, requestIDs, odf)
  implicit def asRequestResultType : xmlTypes.RequestResultType = xmlTypes.RequestResultType(
    xmlTypes.ReturnType(
      "",
      returnValue.returnCode,
      returnValue.description,
      Map.empty
    ),
  requestIDs.headOption.map{
    id => xmlTypes.IdType(id.toString)
  },
  odf.map{ 
    objects =>
      scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( objects.asObjectsType , None, Some("Objects"), defaultScope ) ) ) 
  },
  None,
  None,
  odf.map{ objs => "odf" }
)
} 
case class OmiResultBase(
  val returnValue : OmiReturn,
  override val requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty,
  override val odf: Option[OdfTypes.OdfObjects] = None
) extends OmiResult
object OmiResult{
  def apply(
    returnValue : OmiReturn,
    requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty,
    odf: Option[OdfTypes.OdfObjects] = None
  ) : OmiResult = OmiResultBase(returnValue, requestIDs, odf)
}
object Results{
  case class Success(requestID: Option[Long] = None, objects: Option[OdfObjects] = None, description: Option[String] = None) extends OmiResult{
    val returnValue: OmiReturn = Returns.Success(description)
  }
  case class NotImplemented() extends OmiResult{
    val returnValue: OmiReturn = Returns.NotImplemented()
  }
  case class Unauthorized() extends OmiResult{
    val returnValue: OmiReturn = Returns.Unauthorized() 
  }
  case class InvalidRequest(msg: String = "") extends OmiResult{
    val returnValue: OmiReturn = Returns.InvalidRequest(msg) 
  }
  case class InvalidCallback(callback: String ) extends OmiResult{
    val returnValue: OmiReturn = Returns.InvalidCallback(callback)
  }
  case class NotFoundPaths( paths: Vector[Path] ) extends OmiResult{
    val returnValue: OmiReturn = Returns.NotFoundPaths(paths)
  }

  case class NotFoundRequestIDs( requestIds: Vector[Long] ) extends OmiResult{
    val returnValue: OmiReturn =Returns.NotFoundRequestIDs(requestIds)
  }
  case class ParseErrors( errors: Vector[ParseError] ) extends OmiResult{ 
    val returnValue: OmiReturn = Returns.ParseErrors(errors)
  }

  object InternalError{
    def apply(e: Throwable): InternalError = InternalError(e.getMessage())
  }

  case class InternalError( message: String ) extends OmiResult{
    val returnValue: OmiReturn = Returns.InternalError(message)
  }
  case class TimeOutError(message: String = "") extends OmiResult{
    val returnValue: OmiReturn = Returns.TimeOutError(message)
  }
  case class Poll( requestID: Long, objects: OdfObjects) extends OmiResult{ 
    val returnValue: OmiReturn = Returns.Success()
    override val requestIDs = Vector(requestID)
    override val odf = Some(objects)
  }

  case class Read( objects: OdfObjects) extends OmiResult{
    val returnValue: OmiReturn = Returns.Success()
    override val requestIDs = Vector.empty
    override val odf = Some(objects)
  }
  case class Subscription( requestID: Long, interval: Option[Duration] = None) extends OmiResult{ 
    val returnValue: OmiReturn = Returns.Success(
      interval.map{ dur => s"Successfully started subscription. Interval was set to $dur"})
    override val requestIDs = Vector(requestID)
  }
}
