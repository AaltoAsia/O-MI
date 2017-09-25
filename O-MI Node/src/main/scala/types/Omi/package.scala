package types

import java.lang.{Iterable => JavaIterable}
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory
import java.sql.Timestamp

import scala.language.existentials
import scala.xml.NodeSeq
import scala.collection.JavaConversions
import javax.xml.datatype.XMLGregorianCalendar

import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen._
import types.odf._
/**
 * Package containing classes presenting O-MI request interanlly. 
 *
 */
package object omi  {
  type  OmiParseResult = Either[Iterable[ParseError], Iterable[OmiRequest]]
  type RequestID = Long
  def getLeafPaths(request: OdfRequest): Seq[Path] = request.odf.getLeafPaths.toSeq
  //def getPaths(request: OdfRequest): Seq[Path] = request.odf.getPaths.toSeq
  def requestToEnvelope(request: OmiEnvelopeTypeOption, ttl : Long): xmlTypes.OmiEnvelopeType ={
    val namespace = Some("omi.xsd")
    val version = "1.0"
    val datarecord = request match{
      case read: xmlTypes.ReadRequestType =>
      scalaxb.DataRecord[xmlTypes.ReadRequestType](namespace, Some("read"), read)
      case write: xmlTypes.WriteRequestType =>
      scalaxb.DataRecord[xmlTypes.WriteRequestType](namespace, Some("write"), write)
      case cancel: xmlTypes.CancelRequestType =>
      scalaxb.DataRecord[xmlTypes.CancelRequestType](namespace, Some("cancel"), cancel)
      case response: xmlTypes.ResponseListType => 
      scalaxb.DataRecord[xmlTypes.ResponseListType](namespace, Some("response"), response)
    }
    xmlTypes.OmiEnvelopeType( datarecord, Map(("@version" -> DataRecord("1.0")), ("@ttl" -> DataRecord(ttl))))
  }
 def omiEnvelopeToXML(omiEnvelope: OmiEnvelopeType) : NodeSeq ={
    scalaxb.toXML[OmiEnvelopeType](omiEnvelope, Some("omi"), Some("omiEnvelope"), omiDefaultScope)
  }
 def timestampToXML(timestamp: Timestamp) : XMLGregorianCalendar ={
   val cal = new GregorianCalendar()
   cal.setTime(timestamp)
   DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
 }
 def requestIDsFromJava( requestIDs : java.lang.Iterable[java.lang.Long] ) : Vector[Long ]= {
   JavaConversions.iterableAsScalaIterable(requestIDs).map(Long2long).toVector
 }
  /** Wraps O-DF format to O-MI msg tag.
    * @param odf O-DF Structure.
    * @return O-MI msg tag.
    */
  def odfMsg( odf: NodeSeq ):  NodeSeq ={
    <omi:msg xmlns="odf.xsd">
      {odf}
    </omi:msg>
  }

}
