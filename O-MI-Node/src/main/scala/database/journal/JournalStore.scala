package database.journal

import akka.persistence._
import akka.actor.ActorLogging
import scala.concurrent.duration.Duration

//import Models.Event

/**
  * Common operations on all journal store classes
  */
abstract class JournalStore extends PersistentActor with ActorLogging {
  //override def persistenceId: String = id
  
  val oldestSavedSnapshot: Long =
    Duration(
      context.system.settings.config.getDuration("omi-service.snapshot-delete-older").toMillis,
      scala.concurrent.duration.MILLISECONDS).toMillis


  val receiveBoilerplate: Receive = {
    case SaveSnapshotSuccess(metadata @ SnapshotMetadata(snapshotPersistenceId, sequenceNr, timestamp)) => {
      log.debug(s"Save snapshot success: ${metadata.toString}")
      deleteSnapshots(SnapshotSelectionCriteria(maxTimestamp = timestamp - oldestSavedSnapshot ))
    }
    case SaveSnapshotFailure(metadata, reason) =>
      log.error(reason,  s"Save snapshot failure with: ${metadata.toString}")
    case DeleteSnapshotsSuccess(crit) =>
      log.debug(s"Snapshots successfully deleted for $persistenceId with criteria: $crit")
    case DeleteSnapshotsFailure(crit, ex) =>
      log.error(ex, s"Failed to delete old snapshots for $persistenceId with criteria: $crit")
  }
}
