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

import java.sql.Timestamp
import java.util.Date

import scala.collection.immutable.{HashMap, SortedSet}
import scala.concurrent.duration.Duration

import org.prevayler._
import types._
import types.OmiTypes.Callback


object IntervalSubOrdering extends Ordering[IntervalSub] {
  def compare(a: IntervalSub, b: IntervalSub) : Int =
    a.nextRunTime.getTime compare b.nextRunTime.getTime
}

sealed trait SavedSub {
  val id: Long
  val endTime: Date
  val paths: Vector[Path]
  //va: Duration
}
sealed trait PolledSub extends SavedSub {
  val lastPolled: Timestamp
  val startTime: Timestamp  //Used for preventing from saving duplicate values in database and debugging
}


case class SubIds(var id: Long)
case class PollEventSub(
  id: Long,
  endTime: Timestamp,
  lastPolled: Timestamp,
  startTime: Timestamp,
  paths: Vector[Path]
  ) extends PolledSub

case class PollIntervalSub(
  id: Long,
  endTime: Timestamp,
  interval: Duration,
  lastPolled: Timestamp,
  startTime: Timestamp,
  paths: Vector[Path]
) extends PolledSub

case class IntervalSub(
  id: Long,
  paths: Vector[Path],
  endTime: Timestamp,
  callback: Callback,
  interval: Duration,
  nextRunTime: Timestamp,
  startTime: Timestamp
  ) extends SavedSub//, startTime: Duration) extends SavedSub

case class EventSub(
  id: Long,
  paths: Vector[Path],
  endTime: Timestamp,
  callback: Callback
  ) extends SavedSub//startTime: Duration) extends SavedSub

/** from Path string to event subs for that path */
case class EventSubs(var eventSubs: HashMap[Path, Vector[EventSub]])
object EventSubs {
  //type EventSubsStore = Prevayler[EventSubs]
  def empty : EventSubs = EventSubs(HashMap.empty)
}

case class PolledSubs(var idToSub: HashMap[Long, PolledSub], var pathToSubs: HashMap[Path, Set[Long]])

object PolledSubs {
  def empty : PolledSubs = PolledSubs(HashMap.empty, HashMap.empty)
}

case class IntervalSubs(var intervalSubs: SortedSet[IntervalSub])
object IntervalSubs {
  // type IntervalSubs = Prevayler[IntervalSubs]
  def empty : IntervalSubs = IntervalSubs(SortedSet.empty(IntervalSubOrdering))
}

case class LookupEventSubs(path: Path) extends Query[EventSubs, Vector[EventSub]] {
  def query(es: EventSubs, d: Date): Vector[EventSub] =
    (path.getParentsAndSelf flatMap (p => es.eventSubs.get(p))).flatten.toVector // get for Map returns Option (safe)
}

// Other transactions are in responses/SubscriptionHandler.scala
