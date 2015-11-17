package database

import org.prevayler._

import java.sql.Timestamp
import java.util.Date

import types.OdfTypes.OdfValue
import types._

import scala.collection.immutable.SortedSet
import scala.collection.immutable.HashMap
import scala.concurrent.duration.Duration

case class TimedSub(id: Long,
  ttl: Duration,
  endTime: Date,
  callback: String,
  paths: Seq[Path],
  interval: Duration,
  startTime: Duration,
  nextRunTime: Timestamp
  ) extends SavedSub

object IntervalSubOrdering extends Ordering[IntervalSub] {
  def compare(a: IntervalSub, b: IntervalSub) =
    a.nextRunTime.getTime compare b.nextRunTime.getTime
}

sealed trait SavedSub {
  val id: Long
  val endTime: Date
  val callback: String
  val paths: Seq[Path]
  //va: Duration
}


case class SubIds(var id: Long)
case class IntervalSub(
  id: Long,
  paths: Seq[Path],
  endTime: Timestamp,
  callback: String,
  interval: Duration,
  nextRunTime: Timestamp
  ) extends SavedSub//, startTime: Duration) extends SavedSub

case class EventSub(
  id: Long,
  paths: Seq[Path],
  endTime: Timestamp,
  callback: String,
  lastValue: OdfValue
  ) extends SavedSub //startTime: Duration) extends SavedSub

/** from Path string to event subs for that path */
case class EventSubs(var eventSubs: HashMap[Path, Seq[EventSub]])
object EventSubs {
  //type EventSubsStore = Prevayler[EventSubs]
  def empty = EventSubs(HashMap.empty)
}

case class IntervalSubs(var intervalSubs: SortedSet[IntervalSub])
object IntervalSubs {
  // type IntervalSubs = Prevayler[IntervalSubs]
  def empty = IntervalSubs(SortedSet.empty(IntervalSubOrdering.reverse))
}

case class LookupEventSubs(path: Path) extends Query[EventSubs, Seq[EventSub]] {
  def query(es: EventSubs, d: Date): Seq[EventSub] = es.eventSubs(path)
}

// Other transactions are in responses/SubPrevayler.scala or Subscription Handler
