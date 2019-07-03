package database.journal

import akka.actor.Props
import akka.persistence._
import LatestStore.ErasePathCommand
import Models._
import types.Path
import types.odf._


object HierarchyStore {
  //id given as parameter to make testing possible
  def props(id: String = "hierarchystore"): Props = Props(new HierarchyStore(id))
  //Hierarchy store protocol
  case class UnionCommand(other: ImmutableODF) extends PersistentCommand

  case object GetTree extends Command
}
import HierarchyStore._


class HierarchyStore(override val persistenceId: String) extends JournalStore {

  private var state: ImmutableODF = ImmutableODF() //: Map[String, PersistentNode] = Map()

  def updateState(event: PersistentMessage): Unit = event match {
    case e: Event => e match {
      case PUnion(another) => state = state.union(buildImmutableOdfFromProtobuf(another).valuesRemoved).toImmutable
      case PErasePath(path) => state = state.removePath(Path(path)).toImmutable
      case _ =>

    }
    case p: PersistentCommand => p match {
      case UnionCommand(other) => state = state.union(other.valuesRemoved).toImmutable
      case ErasePathCommand(path) => state = state.removePath(path).toImmutable
      case _ =>

    }
    case _ =>
  }

  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: PUnion) => state = buildImmutableOdfFromProtobuf(snapshot.another)
  }

  def receiveCommand: Receive = receiveBoilerplate orElse {
    case SaveSnapshot(msg) => sender() !
      saveSnapshot(PUnion(state.nodes.map { case (k, v) => k.toString -> PPersistentNode(v.persist) }))
    case union@UnionCommand(other) =>
      persist(PUnion(other.nodes.map { case (k, v) => k.toString -> PPersistentNode(v.persist) })) { event =>
        sender() ! updateState(union)
      }
    case erase@ErasePathCommand(path) =>
      persist(PErasePath(path.toString)) { event =>
        sender() ! updateState(erase)
      }
    case GetTree =>
      sender() ! state.toImmutable

  }

}
