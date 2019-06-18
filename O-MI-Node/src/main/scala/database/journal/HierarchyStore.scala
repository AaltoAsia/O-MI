package database.journal

import akka.actor.{ActorLogging, Props}
import akka.persistence._
import database.journal.HierarchyStore.{GetTree, UnionCommand}
import database.journal.LatestStore.ErasePathCommand
import database.journal.Models._
import types.Path
import types.odf._

import scala.concurrent.duration.Duration

object HierarchyStore {
  //id given as parameter to make testing possible
  def props(id: String = "hierarchystore"): Props = Props(new HierarchyStore(id))
  //Hierarchy store protocol
  case class UnionCommand(other: ImmutableODF) extends PersistentCommand

  case object GetTree extends Command
}
class HierarchyStore(id: String) extends PersistentActor with ActorLogging {
  override def persistenceId: String = id
  val oldestSavedSnapshot: Long =
    Duration(
      context.system.settings.config.getDuration("omi-service.snapshot-delete-older").toMillis,
      scala.concurrent.duration.MILLISECONDS).toMillis

  var state: ImmutableODF = ImmutableODF() //: Map[String, PersistentNode] = Map()

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

  def receiveCommand: Receive = {
    case SaveSnapshot(msg) => sender() !
      saveSnapshot(PUnion(state.nodes.map { case (k, v) => k.toString -> PPersistentNode(v.persist) }))
    case SaveSnapshotSuccess(metadata @ SnapshotMetadata(snapshotPersistenceId, sequenceNr, timestamp)) => {
      log.debug(metadata.toString)
      deleteSnapshots(SnapshotSelectionCriteria(maxTimestamp = timestamp - oldestSavedSnapshot ))
    }
    case DeleteSnapshotsSuccess(crit) =>
      log.debug(s"Snapshots successfully deleted for $persistenceId with criteria: $crit")
    case DeleteSnapshotsFailure(crit, ex) =>
      log.error(ex, s"Failed to delete old snapshots for $persistenceId with criteria: $crit")
    case SaveSnapshotFailure(metadata, reason) ⇒ log.error(reason,  s"Save snapshot failure with: ${metadata.toString}")
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
