package responses

import parsing.Types.OmiTypes._
import parsing.Types.OdfTypes._
import parsing.xmlGen
import parsing.xmlGen.scalaxb

import database._

import scala.xml.NodeSeq
import scala.collection.JavaConversions.iterableAsScalaIterable

class RequestHandler(implicit val dbConnection: DB) {
  def handleRequest(request: OmiRequest) : NodeSeq = request match {
    case read : ReadRequest =>
      scalaxb.toXML[xmlGen.OmiEnvelope](
        OmiGenerator.omiEnvelope(
          1.0,
          OmiGenerator.omiResponse(
            Result.readResult(getSensors(read))
          )
        ),
        Some("omi"), Some("omiEnvelope"), scope
      )

    case poll : PollRequest =>
      scalaxb.toXML[xmlGen.OmiEnvelope](
        OmiGenerator.omiEnvelope(
          1.0,
          OmiGenerator.omiResponse(
            poll.requestIds.map{
              id => 
                val sensors = dbConnection.getSubData(id)
                Result.pollResult(id.toString,sensors) 
            }.toSeq : _*
          )
        ),
        Some("omi"), Some("omiEnvelope"), scope
      )

    case subscription : SubscriptionRequest =>
      scalaxb.toXML[xmlGen.OmiEnvelope](
        OmiGenerator.omiEnvelope(
          1.0, 
          OmiGenerator.omiResponse(
            Result.simpleResult("505", Some( "Not implemented." ) ) 
          )
        ),
        Some("omi"), Some("omiEnvelope"), scope
      )

    case write : WriteRequest =>
      scalaxb.toXML[xmlGen.OmiEnvelope](
        OmiGenerator.omiEnvelope(
          1.0, 
          OmiGenerator.omiResponse(
            Result.simpleResult("505", Some( "Not implemented." ) ) 
          )
        ),
        Some("omi"), Some("omiEnvelope"), scope
      )
    case response : ResponseRequest =>
      scalaxb.toXML[xmlGen.OmiEnvelope](
        OmiGenerator.omiEnvelope(
          1.0, 
          OmiGenerator.omiResponse(
            Result.simpleResult("505", Some( "Not implemented." ) ) 
          )
        ),
        Some("omi"), Some("omiEnvelope"), scope
      )
  } 
  private val scope = scalaxb.toScope(
    None -> "odf",
    Some("omi") -> "omi.xsd",
    Some("xs") -> "http://www.w3.org/2001/XMLSchema",
    Some("xsi") -> "http://www.w3.org/2001/XMLSchema-instance"
  )
  def getSensors(request: OdfRequest): Array[DBSensor] = {
    val paths = getInfoItems( request.odf.objects ).map( info => info.path )
    paths.map{path => dbConnection.get(path) }.collect{ case Some(sensor:DBSensor) => sensor }.toArray
  }
  def getInfoItems( objects: Iterable[OdfObject] ) : Iterable[OdfInfoItem] = {
    objects.flatten{
        obj => 
        obj.infoItems ++ getInfoItems(obj.objects)
    }
  }
}
