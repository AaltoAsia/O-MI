/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package database

import java.io.{File, FilenameFilter}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.actor._
import akka.dispatch.{BoundedMessageQueueSemantics, RequiresMessageQueue}
import org.prevayler.Prevayler
import http.OmiConfigExtension
import journal.Models.SaveSnapshot
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{Await, Future}

object SingleStoresMaintainer{
  def props(
    singleStores : SingleStores,
    settings : OmiConfigExtension
  ) : Props = Props( new SingleStoresMaintainer( singleStores, settings) )
}
class SingleStoresMaintainer(
  protected val singleStores : SingleStores,
  protected val settings : OmiConfigExtension
)
extends Actor
with ActorLogging
with RequiresMessageQueue[BoundedMessageQueueSemantics]
{

  protected val scheduler: Scheduler = context.system.scheduler
  protected val snapshotInterval: FiniteDuration = settings.snapshotInterval
  implicit val timeout: Timeout = 2 minutes
  case object TakeSnapshot
  if( settings.writeToDisk){
    log.info(s"scheduling prevayler snapshot every $snapshotInterval")
    scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshot)
  } else {
    log.info("using transient prevayler, taking snapshots is not in use.")
  }
  protected def takeSnapshot: Future[Unit] = {
    val start: FiniteDuration  = Duration(System.currentTimeMillis(),MILLISECONDS)
    def trySnapshot(p: ActorRef, errorName: String): Future[Unit] = {
      val start: FiniteDuration  = Duration(System.currentTimeMillis(),MILLISECONDS)

        val snapshotF = (p ? SaveSnapshot()).mapTo[Unit]
        snapshotF.onComplete{
          case Success(s) =>{
            val end : FiniteDuration = Duration(System.currentTimeMillis(),MILLISECONDS)
            val duration : FiniteDuration = end - start
            log.debug(s"Taking Snapshot for $errorName took $duration")
          }
          case Failure(ex) => log.error(ex,s"Failed to take Snapshot of $errorName")}
      snapshotF
    }

    log.info("Taking prevayler snapshot")

    val latF = trySnapshot(singleStores.latestStore, "latestStore")
    val hieF = trySnapshot(singleStores.hierarchyStore, "hierarchyStore")
    val subF = trySnapshot(singleStores.subStore, "subStore")
    val datF = trySnapshot(singleStores.pollDataPrevayler, "pollData")
    val res = for{
     lat <- latF
     hie <- hieF
     sub <- subF
     dat <- datF
    } yield dat
    res.onComplete{
      case Success(s) => {
        val end : FiniteDuration = Duration(System.currentTimeMillis(),MILLISECONDS)
        val duration : FiniteDuration = end - start
        log.info(s"Taking Snapshot took $duration")
      }
      case Failure(ex) => log.error(ex, "Taking snapshot failed")
    }
    res
  }

  protected def cleanPrevayler(): Unit = {
    // remove unnecessary files (might otherwise grow until disk is full)
    val dirs = singleStores.prevaylerDirectories
    for (dir <- dirs) {
      val prevaylerDir = new org.prevayler.implementation.PrevaylerDirectory(dir)
      Try{prevaylerDir.necessaryFiles()} match {
        case Failure(e) =>
          log.warning(s"Exception reading directory $dir for prevayler cleaning: $e")
        case Success(necessaryFiles) =>
          val allFiles = dir.listFiles(new FilenameFilter {
            def accept(dir: File, name: String): Boolean = (name endsWith ".journal") || (name endsWith ".snapshot")
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
  /**
   * Function for handling InputPusherCmds.
   *
   */
  override def receive: Actor.Receive = {
    case TakeSnapshot                   => {
      takeSnapshot.map(ts => cleanPrevayler())
    }
  }
}
