package database.journal

import java.sql.Timestamp

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import database.journal.Models._
import database.journal.PPersistentNode.NodeType.{Ii, Obj, Objs}
import types.Path
import types.odf._
class HierarchyStore extends PersistentActor with ActorLogging {
  def persistenceId = "hierarchystore"

  var state: ImmutableODF = ImmutableODF() //: Map[String, PersistentNode] = Map()

  /*def merge[A,B](a:Map[A,B], b:Map[A,B])(mergef:(B,Option[B]) => B): Map[A,B] = {
    //First parameter is the second in mergef (Option[B])

    //val (bigger, smaller) = if(a.size > b.size) (a,b) else (b,a)
    b.foldLeft(a) { case (z,(k,v)) => z + (k -> mergef(v,z.get(k)))}
  }
//HIGHER PRIORITY TO SECOND PARAMETER
  def mergeAttributes(a:Map[String,String], b: Map[String,String]): Map[String,String] =
    merge(a,b){
      case (v1,v2) =>
        v1//v2.map(nodeatt => v1).getOrElse(v1)
    }

  def mergeNodes[A](a:Map[A,PersistentNode], b:Map[A,PersistentNode]):Map[A,PersistentNode] =
    merge(a,b){ case (v1, v2) =>
      //                                                REVERSED RIGHT HERE vvvvvvvvvv
        v2.map(node => node.withAttributes(mergeAttributes(node.attributes,v1.attributes).filter(_._2.nonEmpty))).getOrElse(v1)
    }
*/

  def updateState(event: PersistentMessage): Unit = event match {
    case e: Event => e match {
      case PUnion(another) => state = state.union(buildImmutableOdfFromProtobuf(another)).valuesRemoved.immutable
      case PErasePath(path) => state = state.removePath(Path(path)).immutable

    }
    case p: PersistentCommand => p match {
      case UnionCommand(other) => state = state.union(other).valuesRemoved.immutable
      case ErasePathCommand(path) => state = state.removePath(path).immutable

    }
  }
  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_,snapshot:PUnion) => state = buildImmutableOdfFromProtobuf(snapshot.another)
  }

  def receiveCommand: Receive = {
    case SaveSnapshot(msg) => sender() ! saveSnapshot(PUnion(state.nodes.map{case(k,v) => k.toString -> PPersistentNode(v.persist)}))
    case union @ UnionCommand(other) =>
      persist(PUnion(other.nodes.map{case (k,v)=> k.toString -> PPersistentNode(v.persist)})){ event =>
        sender() ! updateState(union)
      }
    case erase @ ErasePathCommand(path) =>
      persist(PErasePath(path.toString)){event =>
        sender() ! updateState(erase)
      }
    case GetTree =>
      sender() ! state.immutable

  }

}
