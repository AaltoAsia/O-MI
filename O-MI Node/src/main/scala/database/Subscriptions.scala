package database

import java.sql.Timestamp
import java.util.Date

import org.prevayler._
import types.OdfTypes.OdfValue
import types._

import scala.collection.immutable.{HashMap, SortedSet}
import scala.concurrent.duration.Duration


object IntervalSubOrdering extends Ordering[IntervalSub] {
  def compare(a: IntervalSub, b: IntervalSub) =
    a.nextRunTime.getTime compare b.nextRunTime.getTime
}

sealed trait SavedSub {
  val id: Long
  val endTime: Date
  val paths: Seq[Path]
  //va: Duration
}
sealed trait PolledSub extends SavedSub {
  val lastPolled: Timestamp
}


case class SubIds(var id: Long)
case class PollEventSub(
  id: Long,
  endTime: Timestamp,
  lastPolled: Timestamp,
  paths: Seq[Path]
  ) extends PolledSub

case class PollIntervalSub(
  id: Long,
  endTime: Timestamp,
  interval: Duration,
  lastPolled: Timestamp,
  paths: Seq[Path]
) extends PolledSub

case class IntervalSub(
  id: Long,
  paths: Seq[Path],
  endTime: Timestamp,
  callback: String,
  interval: Duration,
  nextRunTime: Timestamp,
  startTime: Timestamp
  ) extends SavedSub//, startTime: Duration) extends SavedSub

case class EventSub(
  id: Long,
  paths: Seq[Path],
  endTime: Timestamp,
  callback: String
  ) extends SavedSub//startTime: Duration) extends SavedSub

/** from Path string to event subs for that path */
case class EventSubs(var eventSubs: HashMap[Path, Seq[EventSub]])
object EventSubs {
  //type EventSubsStore = Prevayler[EventSubs]
  def empty = EventSubs(HashMap.empty)
}

case class PolledSubs(var idToSub: HashMap[Long, PolledSub], var pathToSubs: HashMap[Path, Set[Long]])
object PolledSubs {
  def empty = PolledSubs(HashMap.empty, HashMap.empty)
}

case class IntervalSubs(var intervalSubs: SortedSet[IntervalSub])
object IntervalSubs {
  // type IntervalSubs = Prevayler[IntervalSubs]
  def empty = IntervalSubs(SortedSet.empty(IntervalSubOrdering.reverse))
}

case class LookupEventSubs(path: Path) extends Query[EventSubs, Seq[EventSub]] {
  def query(es: EventSubs, d: Date): Seq[EventSub] =
    (path.getParentsAndSelf flatMap (p => es.eventSubs.get(p))).flatten // get for Map returns Option (safe)
}

// Other transactions are in responses/SubPrevayler.scala or Subscription Handler
