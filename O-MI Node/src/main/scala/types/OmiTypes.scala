/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package types
package OmiTypes

import java.lang.Iterable
import java.sql.Timestamp
import java.util.GregorianCalendar
import javax.xml.datatype.DatatypeFactory

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.concurrent.duration._
import scala.language.existentials
import scala.xml.NodeSeq

import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import parsing.xmlGen.xmlTypes.{ObjectsType, OmiEnvelope}
import responses.OmiGenerator.odfMsg
import types.OdfTypes._


/**
  * Trait that represents any Omi request. Provides some data that are common
  * for all omi requests.
  */
sealed trait OmiRequest {
  def ttl: Duration
  def callback: Option[String]
  def hasCallback: Boolean = callback.isDefined && callback.getOrElse("").nonEmpty
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope 
  implicit def asXML : NodeSeq
}
sealed trait PermissiveRequest
sealed trait OdfRequest {
  def odf : OdfObjects
}

/**
  * Trait for subscription like classes. Offers a common interface for subscription types.
  */
trait SubLike {
  // Note: defs can be implemented also as val and lazy val
  def interval: Duration
  def ttl: Duration
  def isIntervalBased : Boolean  = interval >= 0.milliseconds
  def isEventBased: Boolean = interval == -1.seconds
  def ttlToMillis: Long = ttl.toMillis
  def intervalToMillis: Long = interval.toMillis
  def isImmortal: Boolean  = ! ttl.isFinite
  require(interval == -1.seconds || interval >= 0.seconds, s"Invalid interval: $interval")
  require(ttl >= 0.seconds, s"Invalid ttl, should be positive (or +infinite): $interval")
}

/** One-time-read request
  *
  **/
case class ReadRequest(
  ttl: Duration,
  odf: OdfObjects ,
  begin: Option[Timestamp ] = None,
  end: Option[Timestamp ] = None,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[String ] = None
) extends OmiRequest with OdfRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = {
    xmlTypes.ReadRequest(
      None,
      Nil,
      Some(
        scalaxb.DataRecord(
          Some("omi.xsd"),
          Some("msg"),
          odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope))
        )
      ),
      callback.map{ 
        addr => 
          new java.net.URI(addr)
      },
      Some("odf.xsd"),
      xmlTypes.Node,
      None,
      oldest,
      begin.map{
        timestamp => 
        val cal = new GregorianCalendar();
        cal.setTime(timestamp)
        DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
      },
      end.map{
        timestamp => 
        val cal = new GregorianCalendar();
        cal.setTime(timestamp)
        DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
      },
      newest
    )
  }
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope={ 
    xmlTypes.OmiEnvelope( scalaxb.DataRecord[xmlTypes.ReadRequest](Some("omi.xsd"), Some("read"), asReadRequest), "1.0", ttl.toSeconds)
  }
  implicit def asXML : NodeSeq = scalaxb.toXML[OmiEnvelope](asOmiEnvelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
}

/** Poll request
  *
  **/
case class PollRequest(
  ttl: Duration,
  callback: Option[String ] = None,
  requestIDs: Iterable[Long ] = asJavaIterable(Seq.empty[Long])
) extends OmiRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    requestIDs.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq,
    None,
    callback.map{ addr => new java.net.URI(addr)},
    None,
    xmlTypes.Node,
    None
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope={ 
    xmlTypes.OmiEnvelope( scalaxb.DataRecord[xmlTypes.ReadRequest](Some("omi.xsd"), Some("read"), asReadRequest), "1.0", ttl.toSeconds)
  }
  implicit def asXML : NodeSeq= scalaxb.toXML[OmiEnvelope](asOmiEnvelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
}

/** Subscription request for starting subscription
  *
  **/
case class SubscriptionRequest(
  ttl: Duration,
  interval: Duration,
  odf: OdfObjects,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[String ] = None
) extends OmiRequest with SubLike with OdfRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callback.map{ addr => new java.net.URI(addr)},
    Some("odf.xsd"),
    xmlTypes.Node,
    Some(interval.toSeconds)
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope={ 
    xmlTypes.OmiEnvelope( scalaxb.DataRecord[xmlTypes.ReadRequest](Some("omi.xsd"), Some("read"), asReadRequest), "1.0", ttl.toSeconds)
  }
  implicit def asXML : NodeSeq= scalaxb.toXML[OmiEnvelope](asOmiEnvelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
}


/** Write request
  *
  **/
case class WriteRequest(
  ttl: Duration,
  odf: OdfObjects,
  callback: Option[String ] = None
) extends OmiRequest with OdfRequest with PermissiveRequest{
  implicit def asWriteRequest : xmlTypes.WriteRequest = xmlTypes.WriteRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callback.map{ addr => new java.net.URI(addr)},
    Some("odf.xsd")
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope={ 
    xmlTypes.OmiEnvelope( scalaxb.DataRecord[xmlTypes.WriteRequest](Some("omi.xsd"), Some("write"), asWriteRequest), "1.0", ttl.toSeconds)
  }
  implicit def asXML : NodeSeq= scalaxb.toXML[OmiEnvelope](asOmiEnvelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
}


/** Response request, contains result for other requests
  *
  **/
case class ResponseRequest(
  results: Iterable[OmiResult],
  ttl: Duration = Duration.Inf
) extends OmiRequest with OdfRequest with PermissiveRequest{
  val callback : Option[String] = None
  def odf : OdfObjects= results.foldLeft(OdfObjects()){
    _ union _.odf.getOrElse(OdfObjects())
  }
  implicit def asResponseListType : xmlTypes.ResponseListType = xmlTypes.ResponseListType(results.map{ result => result.asRequestResultType}.toSeq:_*)
   
  implicit def asOmiEnvelope: xmlTypes.OmiEnvelope ={ 
    xmlTypes.OmiEnvelope( scalaxb.DataRecord[xmlTypes.ResponseListType](Some("omi.xsd"), Some("cancel"), asResponseListType), "1.0", ttl.toSeconds)
  }
  implicit def asXML : NodeSeq= scalaxb.toXML[OmiEnvelope](asOmiEnvelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
} 

/** Cancel request, for cancelling subscription.
  *
  **/
case class CancelRequest(
  ttl: Duration,
  requestID: Iterable[Long ] = asJavaIterable(Seq.empty[Long])
) extends OmiRequest {
  implicit def asCancelRequest : xmlTypes.CancelRequest = xmlTypes.CancelRequest(
    None,
    requestID.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq
  )
  def callback : Option[String] = None
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope={ 
    xmlTypes.OmiEnvelope( scalaxb.DataRecord[xmlTypes.CancelRequest](Some("omi.xsd"), Some("cancel"), asCancelRequest), "1.0", ttl.toSeconds)
  }
  implicit def asXML : NodeSeq= scalaxb.toXML[OmiEnvelope](asOmiEnvelope, Some("omi.xsd"), Some("omiEnvelope"), defaultScope)
}

/** Result of a O-MI request
  *
  **/
case class OmiResult(
  value: String,
  returnCode: String,
  description: Option[String] = None,
  requestID: Iterable[Long ] = asJavaIterable(Seq.empty[Long]),
  odf: Option[OdfTypes.OdfObjects] = None
){
    
  implicit def asRequestResultType : xmlTypes.RequestResultType = xmlTypes.RequestResultType(
    xmlTypes.ReturnType(
      value,
      returnCode,
      description,
      Map.empty
    ),
    requestID.headOption.map{
      id => xmlTypes.IdType(id.toString)
    },
    odf.map{ 
      objects =>
        scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( objects.asObjectsType , None, Some("Objects"), defaultScope ) ) ) 
    },
    None,
    None,
    odf.map{ objs => "odf.xsd" }
  )
} 

