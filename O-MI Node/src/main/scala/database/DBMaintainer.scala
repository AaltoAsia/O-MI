package database

import akka.actor._
import akka.dispatch.{BoundedMessageQueueSemantics, RequiresMessageQueue}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import scala.xml.XML

object DBMaintainer{
  def props(dbobject: DB) = Props( new DBMaintainer(dbobject) )  
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
  log.info(s"scheduling databse trimming every $trimInterval seconds")
  scheduler.schedule(trimInterval.seconds, trimInterval.seconds, self, TrimDB)
  if(http.Boot.settings.writeToDisk){
    log.info(s"scheduling prevayler snapshot every $snapshotInterval seconds")
    scheduler.schedule(snapshotInterval.seconds, snapshotInterval.seconds, self, TakeSnapshot)
  } else {
    log.info("using transient prevayler, taking snapshots is not in use.")
  }
  private def takeSnapshot(): Long = {
    log.info("Taking prevyaler snapshot")
    val start = System.currentTimeMillis()
    Try(SingleStores.latestStore.takeSnapshot()).recover{case a => log.error(a,"Failed to take Snapshot of lateststore")}
    Try(SingleStores.hierarchyStore.takeSnapshot()).recover{case a => log.error(a,"Failed to take Snapshot of hierarchystore")}
    Try(SingleStores.eventPrevayler.takeSnapshot()).recover{case a => log.error(a,"Failed to take Snapshot of eventPrevayler")}
    Try(SingleStores.intervalPrevayler.takeSnapshot()).recover{case a => log.error(a,"Failed to take Snapshot of intervalPrevayler")}
    Try(SingleStores.pollPrevayler.takeSnapshot()).recover{case a => log.error(a,"Failed to take Snapshot of pollPrevayler")}
    Try(SingleStores.idPrevayler.takeSnapshot()).recover{case a => log.error(a,"Failed to take Snapshot of idPrevayler")}
    val end = System.currentTimeMillis()
    (end-start)
  }
  /**
   * Function for handling InputPusherCmds.
   *
   */
  override def receive = {
    case TrimDB                         => {val numDel = dbobject.trimDB(); numDel.map(nd => log.info(s"DELETE returned ${nd.sum}"))}
    case TakeSnapshot                   => {val snapshotDur = takeSnapshot(); log.info(s"Taking Snapshot took $snapshotDur milliseconds")}
    case u                              => log.warning("Unknown message received.")
  }
}
