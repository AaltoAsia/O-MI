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
import scala.collection.mutable
import scala.concurrent.duration.Duration

import org.prevayler._
import types.OdfTypes.OdfValue
import types._


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
  callback: String,
  interval: Duration,
  nextRunTime: Timestamp,
  startTime: Timestamp
  ) extends SavedSub//, startTime: Duration) extends SavedSub

case class EventSub(
  id: Long,
  paths: Vector[Path],
  endTime: Timestamp,
  callback: String
  ) extends SavedSub//startTime: Duration) extends SavedSub

/** from Path string to event subs for that path */
case class EventSubs(var eventSubs: HashMap[Path, Vector[EventSub]])
object EventSubs {
  //type EventSubsStore = Prevayler[EventSubs]
  def empty : EventSubs = EventSubs(HashMap.empty)
}
case class PollSubValue(
                     timestamp: Timestamp,
                     value: String,
                     typeValue: String
                     )

case class PollSubData(
  val idToData: collection.mutable.HashMap[Long, collection.mutable.HashMap[Path, List[PollSubValue]]])

object PollSubData {
  def empty: PollSubData = PollSubData(collection.mutable.HashMap.empty)
}


/**
 * Transaction for adding data for polled subscriptions. Does not check if the sub actually exists
 * @param subId
 * @param path
 * @param value
 */
case class AddPollData(subId: Long, path: Path, value: OdfValue) extends Transaction[PollSubData] {
  def executeOn(p: PollSubData, date: Date): Unit = {
    p.idToData.get(subId) match {
      case Some(pathToValues) => pathToValues.get(path) match {
        case Some(sd) =>
          p.idToData(subId)(path) = PollSubValue(value.timestamp, value.value, value.typeValue) :: sd
        case None =>
          p.idToData(subId).update(path, List(PollSubValue(value.timestamp, value.value, value.typeValue)))
      }
      case None =>
        p.idToData
          .update(
            subId,
            collection.mutable.HashMap(path -> List(PollSubValue(value.timestamp, value.value, value.typeValue))))
    }
  }
}

/**
 * Used to Poll event subscription data from the prevayler. Can also used to remove data from subscription
 * @param subId
 */
case class PollEventSubscription(subId: Long) extends TransactionWithQuery[PollSubData, collection.mutable.HashMap[Path,List[PollSubValue]]] {
  def executeAndQuery(p: PollSubData, date: Date): collection.mutable.HashMap[Path, List[PollSubValue]] = {
    p.idToData.remove(subId).getOrElse(collection.mutable.HashMap.empty[Path,List[PollSubValue]])
  }
}

/**
 * Used to poll an interval subscription with the given ID.
 * Polling interval subscriptions leaves the newest value in the database
 * so this can't be used to remove subscriptions.
 * @param subId
 */
case class PollIntervalSubscription(subId:Long) extends TransactionWithQuery[PollSubData, collection.mutable.HashMap[Path, List[PollSubValue]]]{
  def executeAndQuery(p: PollSubData, date: Date): mutable.HashMap[Path, List[PollSubValue]] = {
    val removed = p.idToData.remove(subId)

    removed match {
      case Some(old) => {
        old.foreach {
          case (path, oldValues) if oldValues.nonEmpty => {
            val newest = oldValues.maxBy(_.timestamp.getTime)

            p.idToData(subId).get(path) match {
              case Some(oldV) => p.idToData(subId)(path) = newest :: oldV
              case None => p.idToData(subId).update(path, newest ::Nil)
            }
          }
          case _ =>
        }
        old
      }

      case None => mutable.HashMap.empty
    }

  }
}

/**
 * Used to remove data from interval and event based poll subscriptions.
 * @param subId ID of the sub to remove
 */
case class RemovePollSubData(subId: Long) extends Transaction[PollSubData] {
  def executeOn(p: PollSubData, date: Date): Unit = {
    p.idToData.remove(subId)
  }
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
