package database
import types._
import types.Path._
import types.OdfTypes._

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
import scala.collection.mutable.{ HashMap, PriorityQueue, HashSet}
import java.sql.Timestamp

object OdfStructure {
  private var numberOfHierarchyObjects = 0
  class PathInfo(
    val hierarchyId : Int,
    var odfElement : HasPath,
    val eventSubs : Set[Int]
  ){
  def currentValue : String = odfElement match{
    case info : OdfInfoItem =>
    info.values.headOption.getOrElse(OdfValue("","")).value
    case _ => ""
  }
    
  }
  class PollSubInfo(subId: Int, timestamp: Timestamp, paths: Seq[PathInfo])
  protected var PathMap : collection.mutable.Map[String, PathInfo] = HashMap.empty()
  protected var OdfTree : OdfObjects = OdfObjects()
  protected var PollSubs : collection.mutable.HashMap[Int, PollSubInfo] = HashMap.empty()
  private var PathSpace : HashSet[Path] = HashSet()

  def addOrUpdate( hasPath: HasPath* ) ={
   val triggeredSubs = hasPath.map{ hpath =>
      def recursive( hasPath : HasPath ) : Set[Int]= {
        var pathInfoO = PathMap.get(hasPath.path.toString)
        pathInfoO match {
          case Some(pathInfo) => 
          (pathInfo.odfElement, hasPath) match{
              case (i: OdfInfoItem, u: OdfInfoItem) => pathInfo.odfElement = i.update(u)
              case (i: OdfObject, u: OdfObject) => pathInfo.odfElement = i.update(u)
              case (i: OdfObjects, u: OdfObjects) => pathInfo.odfElement = i.update(u)
              case (i, u ) => throw new Exception("Type missmatch: " + u.getClass + " isn't "+ u.getClass )
          }
          pathInfo.eventSubs ++ (hasPath match{
            case info : OdfInfoItem =>
              Set[Int]()
            case obj : OdfObject => 
              obj.objects.flatMap( recursive ) ++ obj.infoItems.flatMap( recursive )
            case objs : OdfObjects => 
              objs.objects.flatMap( recursive )
            case _ => 
              Set[Int]()
          })
          case None => 
            (hasPath match{
            case info : OdfInfoItem =>
              numberOfHierarchyObjects += 1
              PathMap += hasPath.path.toString  -> new PathInfo( numberOfHierarchyObjects, hasPath, Set())
              Set[Int]()
            case obj : OdfObject => 
              numberOfHierarchyObjects += 1
              PathMap += hasPath.path.toString  -> new PathInfo( numberOfHierarchyObjects, hasPath, Set())
              (obj.objects.flatMap( recursive ) ++ obj.infoItems.flatMap( recursive )).toSet
            case objs : OdfObjects => 
              numberOfHierarchyObjects += 1
              PathMap += hasPath.path.toString  -> new PathInfo( numberOfHierarchyObjects, hasPath, Set())
              objs.objects.flatMap( recursive ).toSet
            case _ => 
              Set[Int]()
          })
        }
      }
      val subs = recursive(hpath)
      (hpath, subs)
    }.toSet
      
  } 

  def get(hasPaths: Seq[HasPath]) : OdfObjects ={
    hasPaths.flatMap{
      hpath =>
      def recursive( hasPath: HasPath) : Seq[HasPath] = {
        PathMap.get(hasPath.path.toString) match {
          case None => Seq.empty
          case Some(pathInfo) =>
          pathInfo.odfElement match{
            case info : OdfInfoItem =>
              Seq(info.copy( values = info.values.take(1) ) ) 
            case obj : OdfObject => 
              (obj.objects.flatMap( recursive ) ++ obj.infoItems.flatMap( recursive )).toSeq
            case objs : OdfObjects => 
              objs.objects.flatMap( recursive ).toSeq
            case _ => 
              Seq()
          }   
        }
      }
      recursive(hpath)
    }.toSet.map{fromPath}.foldLeft(OdfObjects())(_.combine(_))

    
  }
  
  def getNBetween(
    hasPaths: Seq[HasPath], 
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int] ) : OdfObjects  = {
    val hIds =getHierarchyIdsRecursively( hasPaths )
    //DATABASE METHOD
    def getNBetween( hIds: Set[Int], begin: Option[Timestamp], end: Option[Timestamp], newest: Option[Int], oldest: Option[Int] ) : OdfObjects  = ???

    getNBetween( hIds, begin, end, newest, oldest)
  }

  private def getHierarchyIdsRecursively( hasPaths: Seq[HasPath] ) = {
    hasPaths.flatMap{
      hpath =>
      def recursive( hasPath: HasPath) : Seq[Int] = {
        PathMap.get(hasPath.path.toString) match {
          case None => Seq.empty
          case Some(pathInfo) =>
          pathInfo.odfElement match{
            case info : OdfInfoItem =>
              Seq[Int](pathInfo.hierarchyId)
            case obj : OdfObject => 
              (obj.objects.flatMap( recursive ) ++ obj.infoItems.flatMap( recursive )).toSeq
            case objs : OdfObjects => 
              objs.objects.flatMap( recursive ).toSeq
            case _ => 
              Seq[Int]()
          }   
        }
      }
      recursive(hpath)
    }.toSet 
  }


}
