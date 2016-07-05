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
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}
import scala.language.existentials
import scala.xml.NodeSeq

import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import parsing.xmlGen.xmlTypes.{ObjectsType, OmiEnvelope}
import responses.CallbackHandlers
import types.OdfTypes._


/**
  * Trait that represents any Omi request. Provides some data that are common
  * for all omi requests.
  */
sealed trait OmiRequest {

  def ttl: Duration

  def callback: Option[Callback]

  def hasCallback: Boolean = 
    callback.isDefined && callback.map(_.uri).getOrElse("").nonEmpty

  def callbackAsUri: Option[java.net.URI] =
    callback.map{ call => new java.net.URI(call.uri)}

  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope 

  implicit def asXML : NodeSeq= omiEnvelopeToXML(asOmiEnvelope)
}


/**
 * This means request that is writing values
 */
sealed trait PermissiveRequest

/**
 * Request that contains O-DF, (read, write, response)
 */
sealed trait OdfRequest {
  def odf : OdfObjects
}

/**
 * Request that contains requestID(s) (read, cancel) 
 */
sealed trait RequestIDRequest {
  def requestIDs : OdfTreeCollection[Long ]
}


/**
 * Contains information for sending callbacks for a request or subscription
 */
class Callback(
  val uri: String,
  @volatile
  var sendHandler: ExecutionContext => OmiRequest => Future[Unit]
  ) extends Serializable {
    def send(response: OmiRequest)(implicit ec: ExecutionContext): Future[Unit] = sendHandler(ec)(response)
}
object Callback {
  //def apply(uri: Uri): Callback = apply(uri.toString)

  def apply(callbackHandle: ExecutionContext => OmiRequest => Future[Unit]): Callback =
    new Callback("0", callbackHandle)

  def apply(callbackHandle: OmiRequest => Future[Unit]): Callback =
    new Callback("0", {_ => callbackHandle})

  def apply(uri: java.net.URI): Callback = apply(uri.toString)

  def apply(uri: String): Callback = new Callback(uri, {implicit ec: ExecutionContext =>
    CallbackHandlers.sendCallback(uri, _: OmiRequest) 
  })

  case class UndefinedCallbackCallException(msg: String) extends RuntimeException(msg)
  def apply(): Callback = new Callback("0", {a =>
    throw UndefinedCallbackCallException(s"No callbackHandle on $this")
  })

  import scala.language.implicitConversions
  implicit def StringAsCallbackUri: String => Callback = apply
  implicit def UriAsCallback: java.net.URI => Callback = apply
  implicit def OptionCallbackFunctor[A](opt: Option[A])(implicit toCallback: A => Callback): Option[Callback] =
    opt map toCallback
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

/** 
 * One-time-read request
 **/
case class ReadRequest(
  ttl: Duration,
  odf: OdfObjects ,
  begin: Option[Timestamp ] = None,
  end: Option[Timestamp ] = None,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[Callback] = None
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
      callbackAsUri,
      Some("odf"),
      xmlTypes.Node,
      None,
      oldest,
      begin.map{
        timestamp: Timestamp => 
        timestampToXML(timestamp)
      },
      end.map{
        timestamp : Timestamp => 
        timestampToXML(timestamp)
      },
      newest
    )
  }
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttl.toSeconds)
}

/**
 * Poll request
 **/
case class PollRequest(
  ttl: Duration,
  callback: Option[Callback] = None,
  requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty
) extends OmiRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    requestIDs.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq,
    None,
    callbackAsUri,
    None,
    xmlTypes.Node,
    None
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttl.toSeconds)
}

/**
 * Subscription request for starting subscription
 **/
case class SubscriptionRequest(
  ttl: Duration,
  interval: Duration,
  odf: OdfObjects,
  newest: Option[Int ] = None,
  oldest: Option[Int ] = None,
  callback: Option[Callback] = None
) extends OmiRequest with SubLike with OdfRequest{
  
  implicit def asReadRequest : xmlTypes.ReadRequest = xmlTypes.ReadRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callbackAsUri,
    Some("odf"),
    xmlTypes.Node,
    Some(interval.toSeconds)
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asReadRequest, ttl.toSeconds)
}


/**
 * Write request
 **/
case class WriteRequest(
  ttl: Duration,
  odf: OdfObjects,
  callback: Option[Callback] = None
) extends OmiRequest with OdfRequest with PermissiveRequest{
  implicit def asWriteRequest : xmlTypes.WriteRequest = xmlTypes.WriteRequest(
    None,
    Nil,
      Some( scalaxb.DataRecord( Some("omi.xsd"), Some("msg"), odfMsg( scalaxb.toXML[ObjectsType]( odf.asObjectsType , None, Some("Objects"), defaultScope ) ) ) ), 
    callbackAsUri,
    Some("odf")
  )
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asWriteRequest, ttl.toSeconds)
}
/**
 * Cancel request, for cancelling subscription.
 **/
case class CancelRequest(
  ttl: Duration,
  requestIDs: OdfTreeCollection[Long ] = OdfTreeCollection.empty
) extends OmiRequest {
  implicit def asCancelRequest : xmlTypes.CancelRequest = xmlTypes.CancelRequest(
    None,
    requestIDs.map{ 
      id =>
      xmlTypes.IdType(id.toString)
    }.toSeq
  )
  def callback : Option[Callback] = None
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asCancelRequest, ttl.toSeconds)
}

/**
 * Response request, contains result for other requests
 **/
trait ResponseRequest extends OmiRequest with OdfRequest with PermissiveRequest{
  val results: OdfTreeCollection[OmiResult]
  val ttl: Duration 
  val callback : Option[Callback] = None
  def copy( results: OdfTreeCollection[OmiResult] = this.results, ttl: Duration = this.ttl) : ResponseRequest = ResponseRequest( results, ttl)
  def odf : OdfObjects= results.foldLeft(OdfObjects()){
    _ union _.odf.getOrElse(OdfObjects())
  }
  implicit def asResponseListType : xmlTypes.ResponseListType = xmlTypes.ResponseListType(results.map{ result => result.asRequestResultType}.toVector.toSeq: _*)
   
  implicit def asOmiEnvelope : xmlTypes.OmiEnvelope= requestToEnvelope(asResponseListType, ttl.toSeconds)
} 
object ResponseRequest{
  def apply( results: OdfTreeCollection[OmiResult], ttl: Duration = Duration.Inf) : ResponseRequest = ResponseRequestBase( results, ttl)
}
