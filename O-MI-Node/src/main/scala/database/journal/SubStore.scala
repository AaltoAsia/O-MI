package database.journal

import java.sql.Timestamp
import java.util.Date

import Models._
import akka.actor.Props
import akka.persistence._
import PAddSub.SubType._
import database._
import types.omi.HTTPCallback
import types.Path
import SubStore._

import scala.concurrent.duration._
import scala.language.postfixOps

object SubStore {

  def props(id: String = "substore"):Props = Props(new SubStore(id))

  //Sub store protocol
  case class AddEventSub(eventSub: EventSub) extends PersistentCommand

  case class AddIntervalSub(intervalSub: IntervalSub) extends PersistentCommand

  case class AddPollSub(pollsub: PolledSub) extends PersistentCommand

  case class LookupEventSubs(path: Path) extends Command

  case class LookupNewEventSubs(path: Path) extends Command

  case class RemoveIntervalSub(id: Long) extends PersistentCommand

  case class RemoveEventSub(id: Long) extends PersistentCommand

  case class RemovePollSub(id: Long) extends PersistentCommand

  case class PollSubCommand(id: Long) extends PersistentCommand

  case object GetAllEventSubs extends Command

  case object GetAllIntervalSubs extends Command

  case object GetAllPollSubs extends Command

  case class GetPolledSub(id: Long) extends Command

  case class GetIntervalSub(id: Long) extends Command

  case class GetSubsForPath(path: Path) extends Command

  case class GetNewEventSubsForPath(path: Path) extends Command
}

class SubStore(override val persistenceId: String) extends JournalStore {

  private var eventSubs: Map[Path, Seq[EventSub]] = Map()
  private var idToSub: Map[Long, PolledSub] = Map()
  private var pathToSubs: Map[Path, Set[Long]] = Map()
  private var intervalSubs: Map[Long, IntervalSub] = Map()

  def addPollSub(ps: PolledSub): Unit = {
    if (ps.endTime.after(new Date())) {
      idToSub = idToSub + (ps.id -> ps)

      //update mapping from path to Ids
      ps.paths.foreach { subPath =>
        pathToSubs.get(subPath) match {
          case Some(idSeq) => pathToSubs = pathToSubs.updated(subPath, idSeq.+(ps.id))
          case _ => pathToSubs = pathToSubs.updated(subPath, Set(ps.id))
        }
      }
    }
  }

  def addIntervalSub(is: IntervalSub): Unit = {
    if (is.endTime.after(new Date())) {
      intervalSubs = intervalSubs.updated(is.id, is) //) intervalSub//TODO check this
    }
  }


  def addEventSub(es: EventSub): Unit = {
    if (es.endTime.after(new Date())) {
      val newSubs: Map[Path, Seq[EventSub]] = Map(es.paths.map(n => n -> Seq(es)): _*)
      eventSubs = mergeSubs(eventSubs, newSubs)
    }
  }


  def updateState(event: PersistentMessage): Any = event match {
    case e: Event => e match {
      case PAddSub(subType) => subType match {
        case PpollEvent(PPollNormalEventSub(id, endTime, lastPolled, startTime, paths)) =>
          addPollSub(
            PollNormalEventSub(
              id,
              new Timestamp(endTime),
              new Timestamp(lastPolled),
              new Timestamp(startTime),
              paths.map(p => Path(p)).toVector
            )
          )
        case PpollNewEvent(PPollNewEventSub(id, endTime, lastPolled, startTime, paths)) =>
          addPollSub(
            PollNewEventSub(
              id,
              new Timestamp(endTime),
              new Timestamp(lastPolled),
              new Timestamp(startTime),
              paths.map(p => Path(p)).toVector
            )
          )
        case PpollInterval(PPollIntervalSub(id, endTime, intervalSeconds, lastPolled, startTime, paths)) =>
          addPollSub(
            PollIntervalSub(
              id,
              new Timestamp(endTime),
              intervalSeconds seconds,
              new Timestamp(lastPolled),
              new Timestamp(startTime),
              paths.map(p => Path(p)).toVector
            )
          )
        case PeventSub(PNormalEventSub(id, paths, endTime, callback)) =>
          callback.foreach { cb =>
            addEventSub(
              NormalEventSub(
                id,
                paths.map(p => Path(p)).toVector,
                new Timestamp(endTime),
                HTTPCallback(cb.uri)
              )
            )
          }
        case PnewEventSub(PNewEventSub(id, paths, endTime, callback)) =>
          callback.foreach { cb =>
            addEventSub(
              NewEventSub(
                id,
                paths.map(p => Path(p)).toVector,
                new Timestamp(endTime),
                HTTPCallback(cb.uri)
              )
            )
          }
        case PintervalSub(PIntervalSub(id, paths, endTime, callback, intervalSeconds, startTime)) =>
          callback.foreach { cb =>
            addIntervalSub(
              IntervalSub(
                id,
                paths.map(p => Path(p)).toVector,
                new Timestamp(endTime),
                HTTPCallback(cb.uri),
                intervalSeconds seconds,
                new Timestamp(startTime)
              )
            )
          }
        case _ =>
      }
      case PRemoveIntervalSub(id) => {
        intervalSubs = intervalSubs - id
      }
      case PRemoveEventSub(id) => {
        val newStore: Map[Path, Seq[EventSub]] =
          eventSubs
            .mapValues(subs => subs.filterNot(_.id == id)) //remove values that contain id
            .filterNot { case (_, subs) => subs.isEmpty } //remove keys with empty values
        eventSubs = newStore
      }
      case PRemovePollSub(id) => {
        idToSub.get(id).foreach {
          pSub: PolledSub => {
            idToSub = idToSub - id
            pSub.paths.foreach { path =>
              pathToSubs(path) match {
                case ids if ids.size <= 1 => pathToSubs = pathToSubs - path
                case ids => pathToSubs = pathToSubs.updated(path, ids - id)
              }
            }
          }
        }
      }
      case PPollSub(id) => {
        idToSub.get(id).foreach {
          //Update the lastPolled timestamp
          case polledEvent: PollNormalEventSub =>
            idToSub = idToSub + (id -> polledEvent.copy(lastPolled = new Timestamp(new Date().getTime)))
          case polledNewEvent: PollNewEventSub =>
            idToSub = idToSub + (id -> polledNewEvent.copy(lastPolled = new Timestamp(new Date().getTime)))
          case pollInterval: PollIntervalSub =>
            idToSub = idToSub + (id -> pollInterval.copy(lastPolled = new Timestamp(new Date().getTime)))
          case other => log.warning(s"Unknown poll sub type received: $other")
        }
      }
      case other => log.warning(s"Found unknown subType event message: $other")

    }
    case p: PersistentCommand => p match {
      case AddEventSub(es) => es match {
        case ne: NormalEventSub =>
          val persisted = ne.persist()
          if (persisted.callback.isEmpty) {
            addEventSub(ne)
          } else {
            persist(PAddSub(PeventSub(persisted))) { event =>
              addEventSub(ne)
            }
          }
        case ne: NewEventSub =>
          val persisted = ne.persist()
          if (persisted.callback.isEmpty) {
            addEventSub(ne)
          } else {
            persist(PAddSub(PnewEventSub(persisted))) { event =>
              addEventSub(ne)
            }
          }
        case _ =>
      }
      case AddIntervalSub(is) => {
        val persisted = is.persist()
        if (persisted.callback.isEmpty) {
          addIntervalSub(is)
        } else {
          persist(PAddSub(PintervalSub(persisted))) { event =>
            addIntervalSub(is)
          }
        }
      }
      case AddPollSub(pollSub: PolledSub) => pollSub match {
        case ps: PollNormalEventSub =>
          persist(PAddSub(PpollEvent(ps.persist()))) { event =>
            addPollSub(ps)
          }
        case ps: PollNewEventSub =>
          persist(PAddSub(PpollNewEvent(ps.persist()))) { event =>
            addPollSub(ps)
          }
        case ps: PollIntervalSub =>
          persist(PAddSub(PpollInterval(ps.persist()))) { event =>
            addPollSub(ps)
          }
        case other => log.warning(s"Unknown poll sub type: $other")
      }
      case _ =>
    }
    case _ =>
  }


  def recoverEventSubs(eSubs: Map[String, PEventSubs]): Map[Path, Seq[EventSub]] = {
    eSubs.map { case (key, value) => Path(key) -> value.esubs.collect {
      case PEventSub(PEventSub.Eventsub.Nes(PNormalEventSub(id, paths, endTime, Some(cb)))) =>
        NormalEventSub(id, paths.map(Path(_)).toVector, new Timestamp(endTime), HTTPCallback(cb.uri))
      case PEventSub(PEventSub.Eventsub.News(PNewEventSub(id, paths, endTime, Some(cb)))) =>
        NewEventSub(id, paths.map(Path(_)).toVector, new Timestamp(endTime), HTTPCallback(cb.uri))
    }
    }
  }

  def persistEventsubs(peSubs: Map[Path, Seq[EventSub]]): Map[String, PEventSubs] = {
    peSubs.map {
      case (key, values) => key.toString -> PEventSubs(values.map {
        case nes: NormalEventSub => PEventSub(PEventSub.Eventsub.Nes(nes.persist()))
        case news: NewEventSub => PEventSub(PEventSub.Eventsub.News(news.persist()))
      })
    }
  }

  def recoverIdToSub(its: Map[Long, PPolledSub]): Map[Long, PolledSub] = {
    its.mapValues(ps => ps.polledsub match {
      case PPolledSub.Polledsub.Pnes(PPollNormalEventSub(id, endTime, lastPolled, startTime, paths)) =>
        PollNormalEventSub(id,
          new Timestamp(endTime),
          new Timestamp(lastPolled),
          new Timestamp(startTime),
          paths.map(Path(_)).toVector)
      case PPolledSub.Polledsub.Pnews(PPollNewEventSub(id, endTime, lastPolled, startTime, paths)) =>
        PollNewEventSub(id,
          new Timestamp(endTime),
          new Timestamp(lastPolled),
          new Timestamp(startTime),
          paths.map(Path(_)).toVector)
      case PPolledSub.Polledsub.Pints(PPollIntervalSub(id, endTime, interval, lastPolled, starTime, paths)) =>
        PollIntervalSub(id,
          new Timestamp(endTime),
          interval seconds,
          new Timestamp(lastPolled),
          new Timestamp(starTime),
          paths.map(Path(_)).toVector)
      case other => throw new Exception(s"Error while recovering subs: $other")
    }
    )
  }

  def persistIdToSub(pits: Map[Long, PolledSub]): Map[Long, PPolledSub] = {
    pits.mapValues {
      case pnes: PollNormalEventSub => PPolledSub(PPolledSub.Polledsub.Pnes(pnes.persist()))
      case pnews: PollNewEventSub => PPolledSub(PPolledSub.Polledsub.Pnews(pnews.persist()))
      case pints: PollIntervalSub => PPolledSub(PPolledSub.Polledsub.Pints(pints.persist()))
      case other => throw new Exception(s"Error while serializing subs: $other")
    }
  }

  def recoverPathToSub(pts: Map[String, PSubIds]): Map[Path, Set[Long]] = {
    pts.map { case (key, value) => Path(key) -> value.ids.toSet }
  }

  def persistPathToSub(ppts: Map[Path, Set[Long]]): Map[String, PSubIds] = {
    ppts.map { case (key, values) => key.toString -> PSubIds(values.toSeq) }
  }

  def recoverIntervalSubs(ints: Map[Long, PIntervalSub]): Map[Long, IntervalSub] = {
    ints.collect {
      case (id, PIntervalSub(subId, paths, endTime, Some(cb), interval, starttime)) =>
        id ->
          IntervalSub(subId,
            paths.map(Path(_)).toVector,
            new Timestamp(endTime),
            HTTPCallback(cb.uri),
            interval seconds,
            new Timestamp(starttime))
    }
  }

  def persistIntervalSubs(pints: Map[Long, IntervalSub]): Map[Long, PIntervalSub] = {
    pints.mapValues(_.persist())
  }

  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: PSubStoreState) => {
      eventSubs = recoverEventSubs(snapshot.eventSubs)
      idToSub = recoverIdToSub(snapshot.idToSub)
      pathToSubs = recoverPathToSub(snapshot.pathToSubs)
      intervalSubs = recoverIntervalSubs(snapshot.intervalSubs)
    }
  }

  def pollSub(pps: PPollSub): Option[PolledSub] = {
    val sub = idToSub.get(pps.id)
    updateState(pps)
    sub
  }


  def removeSub(rsub: PRemovePollSub): Boolean = {
    val result = idToSub.contains(rsub.id)
    if (result)
      updateState(rsub)
    result
  }

  def removeSub(rsub: PRemoveEventSub): Boolean = {
    val result = eventSubs.values.exists(_.exists(_.id == rsub.id))
    if (result)
      updateState(rsub)
    result
  }

  def removeSub(rsub: PRemoveIntervalSub): Boolean = {
    val result = intervalSubs.contains(rsub.id)
    if (result)
      updateState(rsub)
    result

  }

  def receiveCommand: Receive = receiveBoilerplate orElse {
    case SaveSnapshot(msg) => sender() ! saveSnapshot(
      PSubStoreState(
        persistEventsubs(eventSubs),
        persistIdToSub(idToSub),
        persistPathToSub(pathToSubs),
        persistIntervalSubs(intervalSubs)))

    case aEventS@AddEventSub(eventSub: EventSub) =>
      val res = updateState(aEventS)
      sender() ! res
    case aIntervalS@AddIntervalSub(intervalSub: IntervalSub) =>
      sender() ! updateState(aIntervalS)
    case aPollS@AddPollSub(pollsub: PolledSub) =>
      sender() ! updateState(aPollS)
    case rIntervalS@RemoveIntervalSub(id: Long) =>
      persist(PRemoveIntervalSub(id)) { event =>
        sender() ! removeSub(event)
      }
    case rEventS@RemoveEventSub(id: Long) =>
      persist(PRemoveEventSub(id)) { event =>
        sender() ! removeSub(event)
      }
    case rPollS@RemovePollSub(id: Long) =>
      persist(PRemovePollSub(id)) { event =>
        sender() ! removeSub(event)
      }
    case pollS@PollSubCommand(id: Long) =>
      persist(PPollSub(id)) { event =>
        sender() ! pollSub(event)
      }
    case LookupEventSubs(path: Path) =>
      val resp: Seq[NormalEventSub] = path.getParentsAndSelf
        .flatMap(p => eventSubs.get(p))
        .flatten.collect { case ns: NormalEventSub => ns }
        .toVector
      sender() ! resp
    case LookupNewEventSubs(path: Path) =>
      val resp: Seq[NewEventSub] = path.getParentsAndSelf
        .flatMap(p => eventSubs.get(p))
        .flatten.collect { case ns: NewEventSub => ns }
        .toVector
      sender() ! resp
    case GetAllEventSubs =>
      val result: Set[EventSub] = eventSubs.values.flatten.toSet
      sender() ! result
    case GetAllIntervalSubs =>
      val result: Set[IntervalSub] = intervalSubs.values.toSet
      sender() ! result
    case GetAllPollSubs =>
      val result: Set[PolledSub] = idToSub.values.toSet
      sender() ! result
    case GetIntervalSub(id: Long) =>
      sender() ! intervalSubs.get(id)
    case GetPolledSub(id: Long) =>
      sender() ! idToSub.get(id)
    case GetSubsForPath(path: Path) =>
      val ids: Set[NotNewEventSub] = path.inits.flatMap(path => pathToSubs.get(path)).toSet.flatten.map(idToSub(_))
        .collect {
          case events: PollNormalEventSub => events
          case intervals: PollIntervalSub => intervals
        }
      sender() ! ids
    case GetNewEventSubsForPath(path: Path) =>
      val ids: Set[PollNewEventSub] = path.inits
        .flatMap(path => pathToSubs.get(path)).toSet
        .flatten
        .map(idToSub(_)).collect { case news: PollNewEventSub => news }
      sender() ! ids
  }

}
