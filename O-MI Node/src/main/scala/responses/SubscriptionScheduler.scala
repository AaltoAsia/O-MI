/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/

package responses

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

import akka.actor.ActorRef

import scala.concurrent.duration.Duration

/**
 * Created by satsuma on 8.1.2016.
 */
class SubscriptionScheduler {
  val timeunit = SECONDS
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private def createRunnable(message: Any, sender: ActorRef): Runnable = new Runnable() {def run() = sender ! message}

  def scheduleOnce(timeout: Duration, sender: ActorRef, message: Any) = {
    val task: Runnable = createRunnable(message, sender)
    scheduler.schedule(task, timeout.toSeconds, SECONDS)
  }
}
/**
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

/**
 * Created by satsuma on 8.1.2016.
 */
class SubscriptionScheduler {
  val timeunit = SECONDS
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private def createRunnable(message: Any): Runnable = new Runnable() {def run() = println(message)}

  def scheduleOnce(timeout: Int, message: Any) : Cancelable= {
    val task: Runnable = createRunnable(message)
    scheduler.schedule(task, timeout, SECONDS)
  }
}
*/
