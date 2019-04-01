package database.journal

import akka.actor.{ActorLogging, Props}
import akka.persistence._
import database.journal.Models._
import database.journal.PollDataStore._
import types.Path
import types.odf.Value
//import utils._

import scala.concurrent.duration.Duration
import scala.util.Try

object PollDataStore {
  def props(id: String = "polldatastore"): Props = Props(new PollDataStore(id))

  //PollData protocol
  case class AddPollData(subId: Long, path: Path, value: Value[Any]) extends PersistentCommand

  case class PollEventSubscription(subId: Long) extends PersistentCommand

  case class PollIntervalSubscription(subId: Long) extends PersistentCommand

  case class RemovePollSubData(subId: Long) extends PersistentCommand

  case class CheckSubscriptionData(subId: Long) extends Command

}

class PollDataStore(id: String) extends PersistentActor with ActorLogging {
  override def persistenceId: String = id
  //"polldatastore"
  val oldestSavedSnapshot: Long =
    Duration(
      context.system.settings.config.getDuration("omi-service.snapshot-delete-older").toMillis,
      scala.concurrent.duration.MILLISECONDS).toMillis

  var state: Map[Long, Map[String, Seq[PPersistentValue]]] = Map()
  private def merge[A, B](a: Map[A, B], b: Map[A, B])(mergef: (B, Option[B]) => B): Map[A, B] = {
    val (bigger, smaller) = if (a.size > b.size) (a, b) else (b, a)
    smaller.foldLeft(bigger) { case (z, (k, v)) => z + (k -> mergef(v, z.get(k))) }
  }

  def mergeValues(a: Map[String, Seq[PPersistentValue]],
                  b: Map[String, Seq[PPersistentValue]]): Map[String, Seq[PPersistentValue]] = {
    merge(a, b) { case (v1, v2o) =>
      v2o.map(v2 => v1 ++ v2).getOrElse(v1)
    }
  }

  def updateState(event: Event): Unit = event match {
    case PAddPollData(id, path, Some(value)) =>
      val newValue = Map(path -> Seq(value))
      state += state.get(id).map(pv => (id -> mergeValues(pv, newValue))).getOrElse((id -> newValue))
    case PPollEventSubscription(id) =>
      state -= id
    case PPollIntervalSubscription(id) =>
      val oldVal = state.get(id)
      state -= id

      oldVal.foreach(old => {
        //add the empty sub as placeholder
        //p.idToData += (subId -> mutable.HashMap.empty)
        val newValues = old.mapValues(v => Seq(v.maxBy(_.timeStamp)))
        //add latest value back to db
        state += (id -> newValues)
      })
    case PRemovePollSubData(id) =>
      state -= id
    case other => log.warning(s"Unknown Event $other")
  }

  def pollSubscription(event: PPollIntervalSubscription): Try[Map[Path, Seq[Value[Any]]]] = Try{
    val resp: Map[Path, Seq[Value[Any]]] =
      state.getOrElse(event.id, Map.empty).map { case (key, values) => Path(key) -> values.map(asValue(_)) }
    updateState(event)
    resp
  }

  def pollSubscription(event: PPollEventSubscription): Try[Map[Path, Seq[Value[Any]]]] = Try{
    val resp: Map[Path, Seq[Value[Any]]] =
      state.getOrElse(event.id, Map.empty).map { case (key, values) => Path(key) -> values.map(asValue(_)) }
    updateState(event)
    resp
  }

  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: PPollData) => state = snapshot.subs.mapValues(_.paths.mapValues(_.values))
  }

  def receiveCommand: Receive = {
    case SaveSnapshot(msg) => sender() ! saveSnapshot(
      PPollData(state.mapValues(pmap => PPathToData(pmap.mapValues(values => PValueList(values))))))
    case SaveSnapshotFailure(metadata, reason) ⇒ log.error(reason,  s"Save snapshot failure with: ${metadata.toString}")
    case SaveSnapshotSuccess(metadata @ SnapshotMetadata(snapshotPersistenceId, sequenceNr, timestamp)) => {
      log.debug(metadata.toString)
      deleteSnapshots(SnapshotSelectionCriteria(maxTimestamp = timestamp - oldestSavedSnapshot ))
    }
    case DeleteSnapshotsSuccess(crit) =>
      log.debug(s"Snapshots successfully deleted for $persistenceId with criteria: $crit")
    case DeleteSnapshotsFailure(crit, ex) =>
      log.error(ex, s"Failed to delete old snapshots for $persistenceId with criteria: $crit")

    case AddPollData(subId, path, value) =>
      persist(PAddPollData(subId, path.toString, Some(value.persist))) { event =>
        sender() ! updateState(event)
      }
    case PollEventSubscription(id) =>
      persist(PPollEventSubscription(id)) { event =>
        val resp: Try[Map[Path, Seq[Value[Any]]]] = pollSubscription(event)
        sender() ! resp.getOrElse(akka.actor.Status.Failure(resp.failed.get))
      }
    case PollIntervalSubscription(id) =>
      persist(PPollIntervalSubscription(id)) { event =>
        val resp: Try[Map[Path, Seq[Value[Any]]]] = pollSubscription(event)
        sender() ! resp.getOrElse(akka.actor.Status.Failure(resp.failed.get))
      }
    case RemovePollSubData(id) =>
      persist(PRemovePollSubData(id)) { event =>
        sender() ! updateState(event)
      }
    case CheckSubscriptionData(id) =>
      val response: Try[Map[Path, Seq[Value[Any]]]] = Try{
        state.get(id)
          .map(pv => pv.map { case (k, v) => Path(k) -> v.map(asValue(_)) })
          .getOrElse(Map.empty[Path, Seq[Value[Any]]])
      }
      sender() ! response.getOrElse(akka.actor.Status.Failure(response.failed.get))
  }

}
