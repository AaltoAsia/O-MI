package types
package omi

import java.lang.{Iterable => JIterable}
import scala.collection.JavaConversions._
import scala.concurrent.duration._
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.{omiDefaultScope, scalaxb, xmlTypes}
import types.odf._
import parsing.xmlGen.xmlTypes._
import scala.reflect.ClassTag
import scala.util.Try

trait JavaOmiResult{
  def requestIDsAsJava(): JIterable[RequestID]
  def odfAsJava(): JIterable[ImmutableODF] 
}
/** 
 * Result of a O-MI request
 **/
class OmiResult(
  val returnValue : OmiReturn,
  val requestIDs: Vector[RequestID] = Vector.empty,
  val odf: Option[ImmutableODF] = None
) extends JavaOmiResult {

  def requestIDsAsJava(): JIterable[RequestID] = asJavaIterable(requestIDs) 
  def odfAsJava(): JIterable[ImmutableODF] = asJavaIterable(odf)
  def copy(
    returnValue : OmiReturn = this.returnValue,
    requestIDs: Vector[RequestID] = this.requestIDs,
    odf: Option[ImmutableODF] = this.odf
  ): OmiResult = OmiResult(returnValue, requestIDs, odf)

  implicit def asRequestResultType : xmlTypes.RequestResultType ={
    xmlTypes.RequestResultType(
      returnValue.toReturnType,
      requestIDs.map{
        id => xmlTypes.IdType(id.toString)
      },
      odf.map{ 
        objects =>
          MsgType(
            Seq(
              DataRecord(None, Some("Objects"), objects.asXML
                /*
                Some("omi.xsd"),
                Some("msg"),
                odfMsg( 
                  scalaxb.toXML[ObjectsType]( objects.asObjectsType, None, Some("Objects"), 
                    omiDefaultScope))
                */
            )
          )
        )

      },
      None,
      None,
      Map(
        ("@targetType" -> DataRecord(TargetTypeType.fromString("node", omiDefaultScope )))
      ) ++ odf.map{
        objects =>
        ("@msgformat" -> DataRecord("odf"))

      }
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
  def unionableWith(a: UnionableResult) : Boolean = {
    println( s"Checking equality for ${this.getClass} and ${a.getClass}" )
    a.getClass == this.getClass
  }
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
    requestIDs: Vector[RequestID] = Vector.empty,
    odf: Option[ImmutableODF] = None
  ): OmiResult = new OmiResult(returnValue,requestIDs,odf) 
}

object Results{
  def unionReduce(results: Vector[OmiResult]): Vector[OmiResult] ={
    results.groupBy( _.getClass ).map{ 
      case (a: Any, rs : Seq[OmiResult]) => 
        if( rs.size == 1){
          rs.head
        } else { 
          rs.collect{
            case res : UnionableResult => res
            }.reduce{
              (l: UnionableResult, r: UnionableResult) => l.union(r)
            }
        }
    }.map{ case r: OmiResult => r }.toVector
  }


    case class Success(
      override val requestIDs: Vector[RequestID] = Vector.empty,
      override val odf: Option[ImmutableODF] = None,
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
                  case objects: ImmutableODF => 
                    odf.map{
                      case objs: ImmutableODF =>
                        objects.union(objs).immutable
                    }
                }.orElse(odf),
                other.description.flatMap{
                  case str1 : String =>
                    description.map{
                      case str2: String =>
                        if( str1 == str2 ) str1
                        else s"$str1.\n$str2"
                    }
                }.orElse(description)
                )
          }
        }
      }

  case class SubscribedPathsNotFound(
    paths: Vector[Path]
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

  case class NotImplemented(description: Option[String] = None) 
  extends OmiResult(
    Returns.NotImplemented(description)
  ) with UnionableResult{
    def union(o: UnionableResult): UnionableResult={
      o match {
        case other : NotImplemented => 
          Results.NotImplemented( 
            description.flatMap{ 
              f1 => 
                other.description.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1\nNot implemented: $f2"
                    else f1
                }
            }.orElse( other.description )
            )
      }
    }
  }

  case class NotFound(description: Option[String] = None) 
  extends OmiResult(
    Returns.NotFound(description)
  ) with UnionableResult{
    def union(o: UnionableResult): UnionableResult={
      o match {
        case other : NotFound=> 
          Results.NotFound( 
            description.flatMap{ 
              f1 => 
                other.description.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1\nNot found: $f2"
                    else f1
                }
            }.orElse( other.description)
            )
      }
    }
  }

  case class Unauthorized(
    description: Option[String]= None
    ) extends OmiResult(
      Returns.Unauthorized(description) 
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult={
        o match {
          case other : Unauthorized => 
            Results.Unauthorized( 
              description.flatMap{ 
                f1 => 
                  other.description.map{
                    f2 =>
                      if( f1 != f2 ) s"$f1\nUnauthorized: \n$f2"
                      else f1
                  }
              }.orElse(other.description)
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
                    if( f1 != f2 ) s"$f1\n Invalid request: $f2"
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
      objects: ImmutableODF 
      ) extends OmiResult (
        Returns.NotFoundPaths(),
        odf = Some(objects)
      ) with UnionableResult{
        def union(o: UnionableResult): UnionableResult ={
          o match {
            case other : NotFoundPaths => Results.NotFoundPaths( objects.union(other.objects).immutable )
          }
        }
      }

  case class NotFoundRequestIDs( 
    override val requestIDs: Vector[RequestID] 
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
    description: Option[String] = None 
    ) extends OmiResult(
      Returns.InternalError(description)
    ) with UnionableResult{
      def union(o: UnionableResult): UnionableResult ={
        o match {
          case other : InternalError=> 
            Results.InternalError( 
              description.flatMap{ 
                f1 => 
                  other.description.map{
                    f2 =>
                      if( f1 != f2 ) s"$f1\nInternal error: $f2"
                      else f1
                  }
              }.orElse(other.description)
              )
        }
      }
    }

  case class TTLTimeout(description: Option[String] = None) extends OmiResult(
    Returns.TTLTimeout(description)
  ) with UnionableResult{
    def union(o: UnionableResult): UnionableResult ={
      o match {
        case other : TTLTimeout => 
          Results.TTLTimeout( 
            description.map{ 
              f1 => 
                other.description.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1\n$f2"
                    else f1
                }.getOrElse(f1)
            }.orElse(other.description)
            )
      }
    }
  }

  case class Poll( 
    requestID: RequestID, 
    objects: ImmutableODF
    )  extends OmiResult( 
      Returns.Success(),
      Vector(requestID),
      Some(objects)
    ) with UnionableResult{
      override def unionableWith(other: UnionableResult): Boolean = other match{
        case p: Poll => requestID == p.requestID
        case _ => false
      }
      def union(other: UnionableResult): UnionableResult={
        other match {
          case poll : Poll =>  Poll( requestID, objects.union( poll.objects).immutable)
        }
      }
    }

  case class Read( objects: ImmutableODF) extends OmiResult(
    Returns.Success(),
    odf = Some(objects)
  ) with UnionableResult{
    def union(other: UnionableResult): UnionableResult={
      other match {
        case read : Read => 
          Results.Read( 
            objects.union( read.objects ).immutable
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
    Vector(requestID)
  ){
  }

  case class Timeout(
    val description: Option[String] = None
  ) extends OmiResult(Returns.Timeout(description)) with UnionableResult{
    def union(o: UnionableResult): UnionableResult ={
      o match {
        case other : Timeout => 
          Results.Timeout( 
            description.map{ 
              f1 => 
                other.description.map{
                  f2 =>
                    if( f1 != f2 ) s"$f1\nTimeout: $f2"
                    else f1
                }.getOrElse(f1)
            }.orElse(other.description)
            )
      }
    }
  }
}
