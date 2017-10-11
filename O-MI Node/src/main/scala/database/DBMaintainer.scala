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

object DBMaintainer{
  def props(
             dbConnection : TrimmableDB,
             singleStores : SingleStores,
             settings : OmiConfigExtension
  ) : Props = Props( new DBMaintainer(dbConnection, singleStores, settings) )
}
class DBMaintainer(
                    protected val dbConnection : TrimmableDB,
                    override protected val singleStores : SingleStores,
                    override protected val settings : OmiConfigExtension
)
extends SingleStoresMaintainer(singleStores, settings)
{

  case object TrimDB
  private val trimInterval: FiniteDuration = settings.trimInterval
  log.info(s"scheduling database trimming every $trimInterval")
  scheduler.schedule(trimInterval, trimInterval, self, TrimDB)
  /**
   * Function for handling InputPusherCmds.
   *
   */
  override def receive: Actor.Receive = {
    case TrimDB                         => {
      val numDel = dbConnection.trimDB()
    numDel.map(n=>log.debug(s"DELETE returned ${n.sum}"))}
    case TakeSnapshot                   => 
      val snapshotDur: FiniteDuration = takeSnapshot
      log.info(s"Taking Snapshot took $snapshotDur")
      cleanPrevayler
    
    case _ => log.warning("Unknown message received.")

  }
}
