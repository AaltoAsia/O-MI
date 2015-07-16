package database
import types._
import types.Path._
import types.OdfTypes._

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
import scala.collection.mutable.{ HashMap, PriorityQueue, HashSet}
import java.sql.Timestamp

object OdfStructure {
  private var numberOfHierarchyObjects = 0
    
  
  class PollSubInfo(subId: Int, timestamp: Timestamp, hIDs: Set[Int])
  protected var OdfTree : OdfObjects = OdfObjects()
  protected var PathMap : collection.mutable.Map[String, OdfNode] = HashMap.empty
  protected var PathToHierarchyId : collection.mutable.Map[String, Int] = HashMap.empty
  protected var NormalSubs : collection.mutable.HashMap[Int, Seq[Path]] = HashMap.empty
  protected var EventSubs : collection.mutable.HashMap[Path, Seq[Int]] = HashMap.empty
  protected var PollSubs : collection.mutable.HashMap[Int, PollSubInfo] = HashMap.empty

  /*
  def addOrUpdate( odfNodes: Seq[OdfNode] ) ={
    val tmp : (OdfObjects, Seq[(Path, OdfNode)])= odfNodes.map(fromPath(_)).fold((OdfObjects(), Seq[(Path,OdfNode)]())){
      (a, b) =>
      a._1.update(b).map{(c,d) => (c,d ++ a._2)} 
    }
    val (objects, tuples) = OdfTree.update(tmp._1)
    val updated = (tuples ++ tmp._2).toSet.toSeq
    PathMap ++= tuples.map{a => (a._1.toString,a._2)}

    //Todo DB update and adding, and hierarchy ids
    val mapIdToPaths = odfNodes.flatMap{
      hpath =>
      EventSubs.get(hpath.path) match{
        case Some(ids) =>
          ids.map{ id => (id, hpath.path) }
        case None => Seq.empty
      } 
    }.groupBy{ case (id, path) => id}.mapValues{ case seq => seq.map{ case (id, path) => path} }
    //TODO: Trigger event sub responsese  
  } 
*/
  def get(odfNodes: Seq[OdfNode]) : OdfObjects ={
    odfNodes.map{
      node => 
      (node, OdfTree.get(node.path)) match{
        case (requested: OdfObjects, found: OdfObjects) =>
          found
        case (requested: OdfObject, found: OdfObject) =>
          fromPath(found)
        case (requested: OdfInfoItem, found: OdfInfoItem) =>
          fromPath(found)
      }
    }.foldLeft(OdfObjects())(_.combine(_))
  }
  
  def getNBetween(
    odfNodes: Seq[OdfNode], 
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int] ) : OdfObjects  = {
    val hIds = getHierarchyIdsRecursively(odfNodes)
    //DATABASE METHOD
    def getNBetween( hIds: Set[Int], begin: Option[Timestamp], end: Option[Timestamp], newest: Option[Int], oldest: Option[Int] ) : OdfObjects  = ???

    getNBetween( hIds, begin, end, newest, oldest)
  }

  private def getHierarchyIdsRecursively( odfNodes: Seq[OdfNode] ) = {
    odfNodes.flatMap{
      node => 
        def recursive( node : OdfNode ) : Iterable[Int] = OdfTree.get(node.path) match {
            case Some( info : OdfInfoItem ) =>
              PathToHierarchyId.get(info.path.toString) match{
                case Some(id) => Seq(id)
                case None => throw new Exception("Couldn't find " + info.path.toString)
              }
            case Some( obj : OdfObject ) => 
              obj.objects.flatMap( recursive ) ++ obj.infoItems.flatMap( recursive )
            case Some( objs : OdfObjects ) => 
              objs.objects.flatMap( recursive )
      }
      recursive(node)
    }.toSet  }

  def getNormalSub(id: Int) : OdfObjects ={
    NormalSubs.get(id) match{
      case Some(paths) =>
      paths.map{ 
        path => OdfTree.get( path ) match {
          case Some( node ) => fromPath( node )
          case None => OdfObjects()
        }
      }.fold(OdfObjects())(_.combine(_))
      case None =>
        throw new Exception(s"Normal sub not found with id: $id.")       
    }
  }

}
