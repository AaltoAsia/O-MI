package database.journal

import akka.persistence._
import akka.actor.ActorLogging
import scala.concurrent.duration.Duration

import akka.stream.{Materializer, ActorMaterializer}
//import Models.Event
//import org.slf4j.LoggerFactory
import akka.event.Logging


import http.OmiConfig

/**
  * Common operations on all journal store classes
  */
abstract class JournalStore extends PersistentActor with ActorLogging {
  val settings = OmiConfig(context.system)
  override val log = Logging(context.system, this)

  //override def persistenceId: String = id
  val oldestSavedSnapshot: Long =
    Duration(
      settings.oldestSavedSnapshot.toMillis,
      scala.concurrent.duration.MILLISECONDS).toMillis


  val receiveBoilerplate: Receive = {
    case SaveSnapshotSuccess(metadata @ SnapshotMetadata(snapshotPersistenceId, sequenceNr, timestamp)) => {
      log.debug(s"Save snapshot success: ${metadata.toString}")
      deleteSnapshots(SnapshotSelectionCriteria(maxTimestamp = timestamp - oldestSavedSnapshot ))
      if (settings.snapshotTrimJournal) {
        deleteMessages(sequenceNr - 1)
      }
    }
    case SaveSnapshotFailure(metadata, reason) =>
      log.error(reason,  s"Save snapshot failure with: ${metadata.toString}")
    case DeleteSnapshotsSuccess(crit) =>
      log.debug(s"Snapshots successfully deleted for $persistenceId with criteria: $crit")
    case DeleteSnapshotsFailure(crit, ex) =>
      log.error(ex, s"Failed to delete old snapshots for $persistenceId with criteria: $crit")
  }
}
