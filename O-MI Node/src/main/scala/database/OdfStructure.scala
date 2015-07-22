package database
import http.Boot.settings
import types._
import types.Path._
import types.OdfTypes._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
import scala.collection.mutable.{ Map ,HashMap, PriorityQueue, HashSet}
import java.sql.Timestamp
import java.util.Date

class OdfStructure(implicit val dbConnection: DB) { 
  protected var OdfTree : OdfObjects = OdfObjects()
  protected var PathMap : collection.mutable.Map[String, OdfNode] = HashMap.empty
  protected var PathToHierarchyId : collection.mutable.Map[String, (Int, Int)] = HashMap.empty
  protected var NormalSubs : collection.mutable.HashMap[Long, Seq[Path]] = HashMap.empty
  protected var PolledPaths : collection.mutable.HashMap[Long, Seq[Path]] = HashMap.empty
  case class Poll(subId: Long, timestamp: Timestamp)
  protected var HierarchyIdPollQueues : collection.mutable.Map[Int,collection.mutable.PriorityQueue[Poll]] = HashMap.empty
  def PollQueue =new  PriorityQueue[Poll]()(
    Ordering.by{poll : Poll => poll.timestamp.getTime}
  )
  protected var EventSubs : collection.mutable.HashMap[Path, Seq[Int]] = HashMap.empty

  def addOrUpdate( odfNodes: Seq[OdfNode] ) ={
    val tmpUpdates : (OdfObjects, Seq[(Path, OdfNode)])= odfNodes.map(fromPath(_)).foldLeft((OdfObjects(), Seq[(Path,OdfNode)]())){
      (a, b) =>
      val updated = a._1.update(b)
      (updated._1,updated._2 ++ a._2) 
    }
    val (objects, updateTuples) = OdfTree.update(tmpUpdates._1)
    val updated = (updateTuples ++ tmpUpdates._2).toSet.toSeq
    PathMap ++= updated.map{a => (a._1.toString,a._2)}

    val pathValueTuples = odfNodes.collect{ 
      case info : OdfInfoItem => info 
    }.flatMap{
      info => info.values.map{ value => (info.path, value ) } 
    }
    val newNodes = getOdfNodes(
      //Get all parent nodes too 
      odfNodes.map( fromPath(_) ):_*
    ).toSet.filterNot{ //Filter for paths without hierarchyId
      node => PathToHierarchyId.contains(node.path.toString)
    }.toSeq
    val newPathIdTuples = dbConnection.addNodes( newNodes ).map{ case (path: Path, id: Int) => (path.toString, (id, 0))}
    PathToHierarchyId ++= newPathIdTuples// Add ids for paths 
    val updatedIds = dbConnection.setMany(pathValueTuples.toList)//write values
    val removes : Seq[(Int, Int)]= updatedIds.map{ 
      case (path, id) =>//Update counts
      PathToHierarchyId.get(path.toString).map{
        case (id,count) =>
          PathToHierarchyId.update(path.toString,(id, count +1))
          if( count > settings.numLatestValues )
            Some((id, count - settings.numLatestValues ))
          else
            None
      }
    }.collect{case Some(tuple : (Int, Int) ) => tuple }
    dbConnection.removeN(removes)
    //TODO: Trigger event sub responsese  

  } 
  def get(odfNodes: Seq[OdfNode]) : OdfObjects ={
    odfNodes.map{
      node => 
      (node, OdfTree.get(node.path)) match{
        case (requested: OdfObjects, Some(found: OdfObjects)) =>
          found
        case (requested: OdfObject, Some(found: OdfObject)) =>
          fromPath(found)
        case (requested: OdfInfoItem, Some(found: OdfInfoItem)) =>
          fromPath(found)
        case (requested,found) => 
          throw new Exception(s"Mismatch ${requested.getClass} is not ${found.getClass}, path: ${requested.path}")       
      }
    }.foldLeft(OdfObjects())(_.combine(_))
  }
  
  def getNBetween(
    odfNodes: Seq[OdfNode], 
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int] ) : Option[OdfObjects] = {
    val infoIdTuples = getLeafs(get(odfNodes)).collect{
      case info : OdfInfoItem => (PathToHierarchyId.get(info.path.toString),info)
    }.collect{ case (Some((id,count)),info) => (id,info) }.toSet
    infoIdTuples.headOption.map{ head => 
      dbConnection.getNBetweenWithHierarchyIds( infoIdTuples.toSeq, begin, end, newest, oldest)
    }
  }

  private def getHierarchyIds( odfNodes: Seq[OdfNode] ) : Set[Int] = {
    getLeafs(get(odfNodes)).map{
      leaf => PathToHierarchyId.get(leaf.path.toString)
    }.collect{ case Some((id,count)) => id }.toSet
  }

  def getNormalSub(id: Long) : Option[OdfObjects] ={
    NormalSubs.get(id).map{
      paths =>
      paths.map(path => PathMap.get( path.toString )).collect{ 
        case Some(node) => fromPath( node  )
      }.foldLeft(OdfObjects()){
        (a, b) =>
        val (updated,_) = a.update(b)
        updated   
      }
    }
  }
  
  def getPolledPaths(subId: Long) ={
    PolledPaths.get(subId)
  }
  
  def getPolledHierarchyIds(subId: Long) ={
    PolledPaths.get(subId).map{
      paths => getHierarchyIds(
        paths.map{
          path => PathMap.get(path.toString)
        }.collect{ case Some(node) => node } 
      )
    }
  }

  def getPollData(pollId: Long) : Option[OdfObjects]= {
    val timestamp = new Timestamp(new Date().getTime)
    def getPollDataWithHiearachyIds(pollId:Long,hierarchyIds: Set[Int]) = ??? 
    getPolledHierarchyIds(pollId).map{
      hIds =>
      val result = getPollDataWithHiearachyIds(pollId,hIds) 
      val removes = hIds.map{
        hId =>
       HierarchyIdPollQueues.get(hId).map{
          pollQueue => 
          (
            pollQueue.headOption.collect{
              case headPoll if headPoll.subId != pollId => 
              pollQueue.dequeue
              pollQueue.headOption.map{ poll => poll.timestamp }
            }.collect{ case Some(time) => time},
            pollQueue.filter{ poll => poll.subId != pollId } += Poll(pollId, timestamp)
          )
        }.collect{
          case (Some(time), newQueue) =>
          HierarchyIdPollQueues.update(hId,newQueue)
          (hId, time)
        }
      }.collect{case Some(tuple) => tuple }.filter{
        case (id, timestamp) =>
        PathToHierarchyId.exists{case (path, (hId,count)) => id == hId && count > settings.numLatestValues  }
      }
      Future{ dbConnection.removeBefore(removes.toSeq) }
      result
    } 
  }

}
