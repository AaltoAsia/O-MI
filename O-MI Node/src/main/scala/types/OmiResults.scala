package types
package OmiTypes

import java.lang.{Iterable => JIterable}
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import types.OdfTypes.{ OdfTreeCollection, OdfObjects}
import parsing.xmlGen.xmlTypes.{ObjectsType, OmiEnvelope}
import scala.reflect.ClassTag
import scala.util.Try

trait JavaOmiResult{
  def requestIDsAsJava(): JIterable[RequestID]
  def odfAsJava(): JIterable[OdfObjects] 
}
/** 
 * Result of a O-MI request
 **/
class OmiResult(
  val returnValue : OmiReturn,
  val requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty,
  val odf: Option[OdfTypes.OdfObjects] = None
) extends JavaOmiResult {

  def requestIDsAsJava(): JIterable[RequestID] = asJavaIterable(requestIDs) 
  def odfAsJava(): JIterable[OdfObjects] = asJavaIterable(odf)
  def copy(
    returnValue : OmiReturn = this.returnValue,
    requestIDs: OdfTreeCollection[RequestID] = this.requestIDs,
    odf: Option[OdfTypes.OdfObjects] = this.odf
  ): OmiResult = OmiResult(returnValue, requestIDs, odf)

  implicit def asRequestResultType : xmlTypes.RequestResultType ={
    xmlTypes.RequestResultType(
      returnValue.toReturnType,
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
  override def equals( other: Any ): Boolean ={
    other match {
      case result: OmiResult => 
        result.returnValue == returnValue && 
        result.requestIDs.toSet == requestIDs.toSet && 
        result.odf == odf 
      case any: Any => any == this
    }
  
  }

}

trait UnionableResult{ this: OmiResult =>
  def union(t: UnionableResult): UnionableResult
  def unionableWith(a: UnionableResult) : Boolean = a.getClass == this.getClass
  def tryUnion(o: UnionableResult) = Try{
    require(unionableWith(o))
    o match {
      case t: UnionableResult => union(t)
    }
  }
}
object OmiResult{
  def apply(
    returnValue : OmiReturn,
    requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty,
    odf: Option[OdfTypes.OdfObjects] = None
  ): OmiResult = new OmiResult(returnValue,requestIDs,odf) 
}

object Results{
  def unionReduce(results: OdfTreeCollection[OmiResult]): OdfTreeCollection[OmiResult] ={
    results.groupBy( _.getClass ).mapValues{ 
      case rs : Seq[OmiResult] => 
        rs.collect{
          case res : UnionableResult => res
          }.reduce{
            (l: UnionableResult, r: UnionableResult) => l.union(r)
          }
    }.values.map{ case r: OmiResult => r }.toVector
  }


    case class Success(
      override val requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty,
      override val odf: Option[OdfObjects] = None,
      description: Option[String] = None
      ) extends OmiResult(
        Returns.Success(description),
        requestIDs,
        odf
      ) with UnionableResult{
        def union(o: UnionableResult): UnionableResult={
          o match {
            case other : Success => 
              Results.Success( 
                requestIDs ++ other.requestIDs,
                other.odf.flatMap{
                  case objects: OdfObjects => 
                    odf.map{
                      case objs: OdfObjects =>
                        objects.union(objs)
                    }
                }.orElse(odf),
                other.description.flatMap{
                  case str1 : String =>
                    description.map{
                      case str2: String =>
                        if( str1 == str2 ) str1
                        else s"$str1 and $str2"
                    }
                }.orElse(description)
                )
          }
        }
      }

  case class SubscribedPathsNotFound(
    paths: OdfTreeCollection[Path]
    ) extends OmiResult(
      Returns.SubscribedPathsNotFound(paths)
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult={
        o match {
          case other : SubscribedPathsNotFound => 
            Results.SubscribedPathsNotFound( 
              paths ++ other.paths
            )
        }
      }
    }

  case class NotImplemented(feature: Option[String] = None) 
  extends OmiResult(
    Returns.NotImplemented(feature)
  ) with UnionableResult{
    def union(o: UnionableResult): UnionableResult={
      o match {
        case other : NotImplemented => 
          Results.NotImplemented( 
            feature.flatMap{ 
              f1 => 
                other.feature.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1 and $f2"
                    else f1
                }
            }.orElse( other.feature )
            )
      }
    }
  }

  case class Unauthorized(
    what: Option[String]= None
    ) extends OmiResult(
      Returns.Unauthorized(what) 
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult={
        o match {
          case other : Unauthorized => 
            Results.Unauthorized( 
              what.flatMap{ 
                f1 => 
                  other.what.map{
                    f2 =>
                      if( f1 != f2 ) s"$f1 and $f2"
                      else f1
                  }
              }.orElse(other.what)
              )
        }
      }
    }

  case class InvalidRequest(msg: Option[String] = None) 
  extends OmiResult(
    Returns.InvalidRequest(msg) 
  ) with UnionableResult{
    def union(o: UnionableResult): UnionableResult ={
      o match {
        case other : InvalidRequest => 
          Results.InvalidRequest( 
            msg.flatMap{ 
              f1 => 
                other.msg.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1 and $f2"
                    else f1
                }
            }.orElse(other.msg)
            )
      }
    }
  }
  case class InvalidCallback(
    callback: Callback, 
    reason: Option[String] = None 
    ) extends OmiResult(
      Returns.InvalidCallback(callback, reason)
    ) with UnionableResult{
      override def unionableWith(other: UnionableResult) : Boolean = other match{
        case ic: InvalidCallback => ic.callback == callback
        case _ => false
      } //There can not be multiple callbacks.
      def union(other: UnionableResult): UnionableResult= this
    }

    case class NotFoundPaths(
      objects: OdfObjects 
      ) extends OmiResult (
        Returns.NotFoundPaths(),
        odf = Some(objects)
      ) with UnionableResult{
        def union(o: UnionableResult): UnionableResult ={
          o match {
            case other : NotFoundPaths => Results.NotFoundPaths( objects.union(other.objects) )
          }
        }
      }

  case class NotFoundRequestIDs( 
    override val requestIDs: OdfTreeCollection[RequestID] 
    ) extends OmiResult(
      Returns.NotFoundRequestIDs(),
      requestIDs
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult ={
        o match {
          case other : NotFoundRequestIDs => 
            Results.NotFoundRequestIDs( requestIDs ++ other.requestIDs )
        }
      }
    }

  case class ParseErrors( 
    errors: Vector[ParseError] 
    ) extends OmiResult(
      Returns.ParseErrors(errors)
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult ={
        o match {
          case other : ParseErrors => Results.ParseErrors( errors ++ other.errors )
        }
      }
    }

  object InternalError{
    def apply(e: Throwable) : InternalError = new InternalError(Some(e.getMessage()))
    def apply(msg: String) : InternalError = new InternalError(Some(msg))
  }

  case class InternalError( 
    message: Option[String] = None 
    ) extends OmiResult(
      Returns.InternalError(message)
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult ={
        o match {
          case other : InternalError=> 
            Results.InternalError( 
              message.flatMap{ 
                f1 => 
                  other.message.map{
                    f2 =>
                      if( f1 != f2 ) s"$f1 and $f2"
                      else f1
                  }
              }.orElse(other.message)
              )
        }
      }
    }

  case class TimeOutError(message: Option[String] = None) extends OmiResult(
    Returns.TimeOutError(message)
  ) with UnionableResult{
    def union(o: UnionableResult): UnionableResult ={
      o match {
        case other : TimeOutError => 
          Results.TimeOutError( 
            message.map{ 
              f1 => 
                other.message.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1 and $f2"
                    else f1
                }.getOrElse(f1)
            }.orElse(other.message)
            )
      }
    }
  }

  case class Poll( 
    requestID: RequestID, 
    objects: OdfObjects
    )  extends OmiResult( 
      Returns.Success(),
      OdfTreeCollection(requestID),
      Some(objects)
    ) with UnionableResult{
      override def unionableWith(other: UnionableResult): Boolean = other match{
        case p: Poll => requestID == p.requestID
        case _ => false
      }
      def union(other: UnionableResult): UnionableResult={
        other match {
          case poll : Poll =>  Poll( requestID, objects.union( poll.objects))
        }
      }
    }

  case class Read( objects: OdfObjects) extends OmiResult(
    Returns.Success(),
    odf = Some(objects)
  ) with UnionableResult{
    def union(other: UnionableResult): UnionableResult={
      other match {
        case read : Read => 
          Results.Read( 
            objects.union( read.objects )
          )
      }
    }
  }

  case class Subscription( 
    requestID: RequestID,
    interval: Option[Duration] = None
    )  extends OmiResult(
      Returns.Success( 
        interval.map{ 
          dur => 
            s"Successfully started subscription. Interval was set to $dur"
        }.orElse(
          Some("Successfully started subscription")
        )
      ),
    OdfTreeCollection(requestID)
  ){
  }
}
