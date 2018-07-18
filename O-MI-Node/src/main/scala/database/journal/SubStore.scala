package database.journal

import java.sql.Timestamp
import java.util.Date

import Models._
import akka.actor.ActorLogging
import akka.persistence._
import database._
import PAddSub.SubType._
import types.OmiTypes.HTTPCallback
import types.Path

import scala.concurrent.duration._
import scala.language.postfixOps

class SubStore extends PersistentActor with ActorLogging {

  def persistenceId: String = "substore"
  val oldestSavedSnapshot: Long =
    Duration(
      context.system.settings.config.getDuration("omi-service.snapshot-delete-older").toMillis,
      scala.concurrent.duration.MILLISECONDS).toMillis

  var eventSubs: Map[Path, Seq[EventSub]] = Map()
  var idToSub: Map[Long, PolledSub] = Map()
  var pathToSubs: Map[Path, Set[Long]] = Map()
  var intervalSubs: Map[Long, IntervalSub] = Map()

  def addPollSub(ps: PolledSub) = {
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

  def addIntervalSub(is: IntervalSub) = {
    if (is.endTime.after(new Date())) {
      intervalSubs = intervalSubs.updated(is.id, is) //) intervalSub//TODO check this
    }
  }


  def addEventSub(es: EventSub) = {
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
          case pSub: PolledSub => {
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
        }
      }

    }
    case p: PersistentCommand => p match {
      case Models.AddEventSub(es) => es match {
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
      }
      case Models.AddIntervalSub(is) => {
        val persisted = is.persist()
        if (persisted.callback.isEmpty) {
          addIntervalSub(is)
        } else {
          persist(PAddSub(PintervalSub(persisted))) { event =>
            addIntervalSub(is)
          }
        }
      }
      case Models.AddPollSub(pollSub: PolledSub) => pollSub match {
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

      }
    }
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
    }
    )
  }

  def persistIdToSub(pits: Map[Long, PolledSub]): Map[Long, PPolledSub] = {
    pits.mapValues {
      case pnes: PollNormalEventSub => PPolledSub(PPolledSub.Polledsub.Pnes(pnes.persist()))
      case pnews: PollNewEventSub => PPolledSub(PPolledSub.Polledsub.Pnews(pnews.persist()))
      case pints: PollIntervalSub => PPolledSub(PPolledSub.Polledsub.Pints(pints.persist()))
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

  def receiveCommand: Receive = {
    case SaveSnapshot(msg) => sender() ! saveSnapshot(
      PSubStoreState(
        persistEventsubs(eventSubs),
        persistIdToSub(idToSub),
        persistPathToSub(pathToSubs),
        persistIntervalSubs(intervalSubs)))
    case SaveSnapshotSuccess(metadata @ SnapshotMetadata(snapshotPersistenceId, sequenceNr, timestamp)) => {
      log.debug(metadata.toString)
      deleteSnapshots(SnapshotSelectionCriteria(maxTimestamp = timestamp - oldestSavedSnapshot ))
    }
    case SaveSnapshotFailure(metadata, reason) ⇒ log.error(reason,  s"Save snapshot failure with: ${metadata.toString}")
    case DeleteSnapshotsSuccess(crit) =>
      log.debug(s"Snapshots successfully deleted for $persistenceId with criteria: $crit")
    case DeleteSnapshotsFailure(crit, ex) =>
      log.error(ex, s"Failed to delete old snapshots for $persistenceId with criteria: $crit")

    case aEventS@Models.AddEventSub(eventSub: EventSub) =>
      val res = updateState(aEventS)
      sender() ! res
    case aIntervalS@Models.AddIntervalSub(intervalSub: IntervalSub) =>
      sender() ! updateState(aIntervalS)
    case aPollS@Models.AddPollSub(pollsub: PolledSub) =>
      sender() ! updateState(aPollS)
    case rIntervalS@Models.RemoveIntervalSub(id: Long) =>
      persist(PRemoveIntervalSub(id)) { event =>
        sender() ! removeSub(event)
      }
    case rEventS@Models.RemoveEventSub(id: Long) =>
      persist(PRemoveEventSub(id)) { event =>
        sender() ! removeSub(event)
      }
    case rPollS@Models.RemovePollSub(id: Long) =>
      persist(PRemovePollSub(id)) { event =>
        sender() ! removeSub(event)
      }
    case pollS@Models.PollSubCommand(id: Long) =>
      persist(PPollSub(id)) { event =>
        sender() ! pollSub(event)
      }
    case Models.LookupEventSubs(path: Path) =>
      val resp: Seq[NormalEventSub] = path.getParentsAndSelf
        .flatMap(p => eventSubs.get(p))
        .flatten.collect { case ns: NormalEventSub => ns }
        .toVector
      sender() ! resp
    case Models.LookupNewEventSubs(path: Path) =>
      val resp: Seq[NewEventSub] = path.getParentsAndSelf
        .flatMap(p => eventSubs.get(p))
        .flatten.collect { case ns: NewEventSub => ns }
        .toVector
      sender() ! resp
    case Models.GetAllEventSubs =>
      val result: Set[EventSub] = eventSubs.values.flatten.toSet
      sender() ! result
    case Models.GetAllIntervalSubs =>
      val result: Set[IntervalSub] = intervalSubs.values.toSet
      sender() ! result
    case Models.GetAllPollSubs =>
      val result: Set[PolledSub] = idToSub.values.toSet
      sender() ! result
    case Models.GetIntervalSub(id: Long) =>
      sender() ! intervalSubs.get(id)
    case Models.GetSubsForPath(path: Path) =>
      val ids: Set[NotNewEventSub] = path.inits.flatMap(path => pathToSubs.get(path)).toSet.flatten.map(idToSub(_))
        .collect {
          case events: PollNormalEventSub => events
          case intervals: PollIntervalSub => intervals
        }
      sender() ! ids
    case Models.GetNewEventSubsForPath(path: Path) =>
      val ids: Set[PollNewEventSub] = path.inits
        .flatMap(path => pathToSubs.get(path)).toSet
        .flatten
        .map(idToSub(_)).collect { case news: PollNewEventSub => news }
      sender() ! ids
  }

}
