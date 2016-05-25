/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package types
package OmiTypes

import java.lang.Iterable
import java.sql.Timestamp

import types.OdfTypes._

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.concurrent.duration._
import scala.language.existentials

/**
 * Package containing classes presenting O-MI request interanlly. 
 *
 */
object `package` {
  type  OmiParseResult = Either[Iterable[ParseError], Iterable[OmiRequest]]
  def getPaths(request: OdfRequest): Seq[Path] = getLeafs(request.odf).map{ _.path }.toSeq
}

  /**
   * Trait that represents any Omi request. Provides some data that are common
   * for all omi requests.
   */
  sealed trait OmiRequest {
    def ttl: Duration
    def callback: Option[String]
    def hasCallback: Boolean = callback.isDefined && callback.getOrElse("").nonEmpty
  }
  sealed trait PermissiveRequest
  sealed trait OdfRequest {
    def odf : OdfObjects
  }

  /**
   * Trait for subscription like classes. Offers a common interface for subscription types.
   */
  trait SubLike extends OmiRequest {
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

/** Request for getting data for current interval.
  * Used for subscription callbacks.
  **/
  case class SubDataRequest(sub: database.DBSub) extends OmiRequest {
    def ttl: Duration = sub.ttl
    def callback: Option[String] = sub.callback
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
) extends OmiRequest with OdfRequest

/** Poll request
  *
  **/
case class PollRequest(
  ttl: Duration,
  callback: Option[String ] = None,
  requestIDs: Iterable[Long ] = asJavaIterable(Seq.empty[Long])
) extends OmiRequest

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
) extends OmiRequest with SubLike with OdfRequest

/** Write request
  *
  **/
case class WriteRequest(
  ttl: Duration,
  odf: OdfObjects,
  callback: Option[String ] = None
) extends OmiRequest with OdfRequest with PermissiveRequest


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
   } 

/** Cancel request, for cancelling subscription.
  *
  **/
case class CancelRequest(
  ttl: Duration,
  requestID: Iterable[Long ] = asJavaIterable(Seq.empty[Long])
) extends OmiRequest {
      def callback : Option[String] = None
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
) 

