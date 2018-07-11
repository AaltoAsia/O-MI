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
import http.OmiConfigExtension
import journal.Models.SaveSnapshot
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.{Future}

object SingleStoresMaintainer {
  def props(
             singleStores: SingleStores,
             settings: OmiConfigExtension
           ): Props = Props(new SingleStoresMaintainer(singleStores, settings))
}

class SingleStoresMaintainer(
                              protected val singleStores: SingleStores,
                              protected val settings: OmiConfigExtension
                            )
  extends Actor
    with ActorLogging
    with RequiresMessageQueue[BoundedMessageQueueSemantics] {

  protected val scheduler: Scheduler = context.system.scheduler
  protected val snapshotInterval: FiniteDuration = settings.snapshotInterval
  implicit val timeout: Timeout = settings.journalTimeout

  case object TakeSnapshot

  if (settings.writeToDisk) {
    log.info(s"scheduling journal snapshot every $snapshotInterval")
    scheduler.schedule(snapshotInterval, snapshotInterval, self, TakeSnapshot)
  } else {
    log.info("using transient journal, taking snapshots is not in use.")
  }

  protected def takeSnapshot: Future[Any] = {
    singleStores.takeSnapshot
  }


  /**
    * Function for handling InputPusherCmds.
    *
    */
  override def receive: Actor.Receive = {
    case TakeSnapshot => {
      takeSnapshot
    }
  }
}
