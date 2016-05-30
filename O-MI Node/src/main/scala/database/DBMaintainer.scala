package database

import akka.actor._
import akka.dispatch.{BoundedMessageQueueSemantics, RequiresMessageQueue}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.XML
import java.io.{File, FilenameFilter}
import org.prevayler.Prevayler

object DBMaintainer{
  def props(dbobject: DB) : Props = Props( new DBMaintainer(dbobject) )  
}
class DBMaintainer(val dbobject: DB)
  extends Actor
  with ActorLogging
  with RequiresMessageQueue[BoundedMessageQueueSemantics]
  {

  case object TrimDB
  case object TakeSnapshot
  private val scheduler = context.system.scheduler
  private val trimInterval = http.Boot.settings.trimInterval
  private val snapshotInterval = http.Boot.settings.snapshotInterval
  log.info(s"scheduling databse trimming every $trimInterval")
  scheduler.schedule(trimInterval, trimInterval, self, TrimDB)
  if(http.Boot.settings.writeToDisk){
    log.info(s"scheduling prevayler snapshot every $snapshotInterval")
    scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshot)
  } else {
    log.info("using transient prevayler, taking snapshots is not in use.")
  }
  type Hole
  private def takeSnapshot: FiniteDuration = {
    def trySnapshot[T](p: Prevayler[T], errorName: String): Unit = {
      Try[Unit]{
        p.takeSnapshot() // returns snapshot File
      }.recover{case a : Throwable => log.error(a,s"Failed to take Snapshot of $errorName")}
    }

    log.info("Taking prevyaler snapshot")
    val start: FiniteDuration  = Duration(System.currentTimeMillis(),MILLISECONDS)

    trySnapshot(SingleStores.latestStore, "latestStore")
    trySnapshot(SingleStores.hierarchyStore, "hierarchySrtore")
    trySnapshot(SingleStores.eventPrevayler, "eventPrevayler")
    trySnapshot(SingleStores.intervalPrevayler, "intervalPrevayler")
    trySnapshot(SingleStores.pollPrevayler, "pollPrevayler")
    trySnapshot(SingleStores.idPrevayler, "idPrevayler")

    val end : FiniteDuration = Duration(System.currentTimeMillis(),MILLISECONDS)
    val duration : FiniteDuration = end - start
    duration
  }
  /**
   * Function for handling InputPusherCmds.
   *
   */
  override def receive: Actor.Receive = {
    case TrimDB                         => {val numDel = dbobject.trimDB(); log.info(s"DELETE returned $numDel")}
    case TakeSnapshot                   => {
      val snapshotDur: FiniteDuration = takeSnapshot
      log.info(s"Taking Snapshot took $snapshotDur milliseconds")
      // remove unnecessary files (might otherwise grow until disk is full)
      val dirs = SingleStores.prevaylerDirectories
      for (dir <- dirs) {
        val prevaylerDir = new org.prevayler.implementation.PrevaylerDirectory(dir)
        Try{prevaylerDir.necessaryFiles()} match {
          case Failure(e) =>
            log.warning(s"Exception reading directory $dir for prevayler cleaning: $e")
          case Success(necessaryFiles) =>
            val allFiles = dir.listFiles(new FilenameFilter {
              def accept(dir: File, name: String) = name endsWith ".journal" // TODO: better filter
            })
            
            val extraFiles = allFiles filterNot (necessaryFiles contains _)

            extraFiles foreach {file =>
              Try{file.delete} match {
                case Success(true) => // noop
                case Success(false) =>
                  log.warning(s"File $file was listed unnecessary but couldn't be deleted")
                case Failure(e) => 
                  log.warning(s"Exception when trying to delete unnecessary file $file: $e")
              }
            }
        }
        
      }
    }
    case _ => log.warning("Unknown message received.")
  }
}
