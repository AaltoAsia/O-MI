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

package responses

import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.{Executors, ScheduledFuture}

import akka.actor.ActorRef

import scala.concurrent.duration.Duration

class SubscriptionScheduler {
  val timeunit = SECONDS
  private val scheduler = Executors.newSingleThreadScheduledExecutor()

  private def createRunnable(message: Any, sender: ActorRef): Runnable = () => sender ! message

  def scheduleOnce(timeout: Duration, sender: ActorRef, message: Any): ScheduledFuture[_] = {
    val task: Runnable = createRunnable(message, sender)
    scheduler.schedule(task, timeout.toSeconds, SECONDS)
  }
}

