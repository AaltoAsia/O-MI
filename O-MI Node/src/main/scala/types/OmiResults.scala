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
  def returnValue : OmiReturn
  def requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty
  def odf: Option[OdfTypes.OdfObjects] = None

  def unionableWith(other: OmiResult): Boolean = {
    if(returnValue.unionableWith(other.returnValue)) true 
    else other match{
        case t: Results.ResultList => true
        case o: OmiResult => false
      }
  }
  def union(other: OmiResult): OmiResult /*={
    if( unionableWith(other) ){

    } else ResultList( Seq( this, other ))
  }*/
  def copy(
    returnValue : OmiReturn = this.returnValue,
    requestIDs: OdfTreeCollection[RequestID] = this.requestIDs,
    odf: Option[OdfTypes.OdfObjects] = this.odf
  ): OmiResult = OmiResult(returnValue, requestIDs, odf)

  implicit def asRequestResultType : xmlTypes.RequestResultType ={
    xmlTypes.RequestResultType(
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

}

object OmiResult{
  def apply(
    returnValue : OmiReturn,
    requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty,
    odf: Option[OdfTypes.OdfObjects] = None
  ): OmiResult = (returnValue, odf) match{
    case (s: Returns.Success,objects) => Results.Success(requestIDs,objects)
    case (nf: Returns.NotFoundPaths, Some(objects)) if requestIDs.isEmpty => Results.NotFoundPaths(objects) 
    case (nf: Returns.NotFoundRequestIDs,None) => Results.NotFoundRequestIDs(requestIDs) 
  }
}


object Results{
  implicit class ResultList(results: Seq[OmiResult]) extends OmiResult{
    val returnValue: OmiReturn = Returns.Success()
    override def unionableWith(other: OmiResult): Boolean = true
    def union(other: OmiResult): OmiResult ={
      unionReduce(results ++ Vector(other))
    }
    implicit def toSeq: Seq[OmiResult] = results
  }

  def unionReduce(results: Seq[OmiResult]): Seq[OmiResult] ={
    results.foldLeft( Seq.empty[OmiResult] ){
      case ( newResults: Seq[OmiResult], element: OmiResult ) => 
        val index: Int = newResults.indexWhere{
          case result : OmiResult => result.unionableWith( element ) 
        }
        if( index > -1 ){
          val unioned =  newResults(index).union(element)
          newResults.updated( index, unioned)
        }else{
          newResults ++ Seq( element )
        }
    }
  }

  case class Success(
    override val requestIDs: OdfTreeCollection[RequestID] = OdfTreeCollection.empty,
    override val odf: Option[OdfObjects] = None,
    description: Option[String] = None
  ) extends OmiResult{
    override val returnValue: OmiReturn = Returns.Success(description)
    def union(other: OmiResult): OmiResult ={
      other match {
        case s: Results.Success => 
          Results.Success( 
            requestIDs ++ s.requestIDs,
            s.odf.flatMap{
              case objects: OdfObjects => 
                odf.map{
                  case objs: OdfObjects =>
                    objects.union(objs)
                }
            }.orElse(odf),
            s.description.flatMap{
              case str1 : String =>
                description.map{
                  case str2: String =>
                    if( str1 == str2 ) str1
                    else s"$str1 and $str2"
                }
            }.orElse(description)
            )
          case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  
  case class SubscribedPathsNotFound(paths: OdfTreeCollection[Path]) extends OmiResult{
    override val returnValue: OmiReturn = Returns.SubscribedPathsNotFound(paths)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.SubscribedPathsNotFound => 
          Results.SubscribedPathsNotFound( 
            paths ++ o.paths
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  case class NotImplemented(feature: Option[String] = None) extends OmiResult{
    override val returnValue: OmiReturn = Returns.NotImplemented(feature)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.NotImplemented => 
          Results.NotImplemented( 
            feature.flatMap{ 
              f1 => 
              o.feature.map{
                f2 =>
                  if( f1 != f2 ) s"$f1 and $f2"
                  else f1
              }
            }.orElse( o.feature )
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  
  case class Unauthorized(what: Option[String]= None) extends  OmiResult{
    override val returnValue: OmiReturn = Returns.Unauthorized(what) 
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.Unauthorized => 
          Results.Unauthorized( 
            what.flatMap{ 
              f1 => 
              o.what.map{
                f2 =>
                  if( f1 != f2 ) s"$f1 and $f2"
                  else f1
              }
            }.orElse(o.what)
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  
  case class InvalidRequest(msg: Option[String] = None) extends OmiResult{
    override val returnValue: OmiReturn = Returns.InvalidRequest(msg) 
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.InvalidRequest=> 
          Results.InvalidRequest( 
            msg.flatMap{ 
              f1 => 
              o.msg.map{
                f2 =>
                  if( f1 != f2 ) s"$f1 and $f2"
                  else f1
              }
            }.orElse(o.msg)
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  case class InvalidCallback(callback: Callback, reason: Option[String] = None ) extends OmiResult{
    override val returnValue: OmiReturn = Returns.InvalidCallback(callback, reason)
    override def unionableWith(other: OmiResult) : Boolean = false //There can not be multiple callbacks.
    def union(other: OmiResult): OmiResult ={
      other match {
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  
  case class NotFoundPaths( objects: OdfObjects ) extends OmiResult {
    val returnValue: OmiReturn = Returns.NotFoundPaths()
    override val odf: Option[OdfObjects] = Some(objects)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.NotFoundPaths => Results.NotFoundPaths( objects.union(o.objects) )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }

  case class NotFoundRequestIDs( 
    override val requestIDs: OdfTreeCollection[RequestID] 
  ) extends OmiResult {
    override val returnValue: OmiReturn =Returns.NotFoundRequestIDs()
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.NotFoundRequestIDs => Results.NotFoundRequestIDs( requestIDs ++ o.requestIDs )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
  case class ParseErrors( errors: Vector[ParseError] )  extends OmiResult { 
    override val returnValue: OmiReturn = Returns.ParseErrors(errors)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.ParseErrors=> Results.ParseErrors( errors ++ o.errors )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }

  object InternalError{
    def apply(e: Throwable) : InternalError = InternalError(Some(e.getMessage()))
  }

  case class InternalError( message: Option[String] = None )  extends OmiResult {
    override val returnValue: OmiReturn = Returns.InternalError(message)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.InternalError => 
          Results.InternalError( 
            message.flatMap{ 
              f1 => 
              o.message.map{
                f2 =>
                  if( f1 != f2 ) s"$f1 and $f2"
                  else f1
              }
            }.orElse(o.message)
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }

  case class TimeOutError(message: Option[String] = None) extends OmiResult {
    override val returnValue: OmiReturn = Returns.TimeOutError(message)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.InternalError => 
          Results.TimeOutError( 
            message.flatMap{ 
              f1 => 
              o.message.map{
                f2 =>
                  if( f1 != f2 ) s"$f1 and $f2"
                  else f1
              }
            }.orElse(o.message)
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }

  case class Poll( 
    requestID: RequestID, 
    objects: OdfObjects
  )  extends OmiResult { 
    override val requestIDs : OdfTreeCollection[RequestID] = OdfTreeCollection(requestID)
    override val returnValue: OmiReturn = Returns.Success()
    override val odf = Some(objects)
    override def unionableWith(other: OmiResult): Boolean = false
    def union(other: OmiResult): OmiResult ={
      other match {
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }

  case class Read( objects: OdfObjects) extends OmiResult {
    override val returnValue: OmiReturn = Returns.Success()
    override val requestIDs = Vector.empty
    override val odf = Some(objects)
    def union(other: OmiResult): OmiResult ={
      other match {
        case o : Results.Read => 
          Results.Read( 
            objects.union( o.objects )
          )
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }

  case class Subscription( requestID: RequestID, interval: Option[Duration] = None)  extends OmiResult { 
    override val returnValue: OmiReturn = Returns.Success(
      interval.map{ dur => s"Successfully started subscription. Interval was set to $dur"}.orElse(Some("Successfully started subscription")))
    override val requestIDs = Vector(requestID)
    override def unionableWith(other: OmiResult) : Boolean = false //There can not be multiple callbacks.
    def union(other: OmiResult): OmiResult ={
      other match {
        case _ : OmiResult => Results.ResultList( Vector[OmiResult](this,other) )
      }
    }
  }
}
