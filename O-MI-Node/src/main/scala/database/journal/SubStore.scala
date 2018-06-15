package database.journal

import java.sql.Timestamp
import java.util.Date

import PAddSub.SubType._
import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import database.journal.Models._
import database.{GetAllEventSubs => _, GetAllIntervalSubs => _, GetAllPollSubs => _, _}
import types.Path

class SubStore extends PersistentActor with ActorLogging {

  def persistenceId: String = "substore-id"

  var eventSubs:Map[Path,Seq[EventSub]] = Map()
  var idToSub: Map[Long, PolledSub] = Map()
  var pathToSubs: Map[Path,Set[Long]] = Map()
  var intervalSubs: Map[Long,IntervalSub] = Map()

  def addPollSub(ps: PolledSub) = {
    if(ps.endTime.before(new Date())){
      idToSub = idToSub + (ps.id -> ps)

      //update mapping from path to Ids
      ps.paths.foreach{ subPath =>
        pathToSubs.get(subPath) match {
          case Some(idSeq) => pathToSubs = pathToSubs.updated(subPath, idSeq.+(ps.id))
          case _ => pathToSubs = pathToSubs.updated(subPath, Set(ps.id))
        }
      }
    }
  }
  def addIntervalSub(is: IntervalSub) = {
    if(is.endTime.before(new Date())){
      intervalSubs = intervalSubs.updated(is.id, is)//) intervalSub//TODO check this
    }
  }


  def addEventSub(es: EventSub) = {
    if(es.endTime.before(new Date())) {
      val newSubs: Map[Path, Seq[EventSub]] = Map(es.paths.map(n => n -> Seq(es)): _*)
      eventSubs = mergeSubs(eventSubs,newSubs)
    }
  }


  def updateState(event: PersistentMessage) = event match {
    case e: Event => e match {
      case PAddSub(subType) =>
      case PRemoveSub(id) =>
      case PPollSub(id) =>
    }
    case p: PersistentCommand => p match {
      case Models.AddEventSub(es) => es match {
        case ne: NormalEventSub =>
          val persisted = ne.persist()
          if(persisted.callback.isEmpty){
            addEventSub(ne)
          }
        case ne: NewEventSub =>
          val persisted = ne.persist()
          if(persisted.callback.isEmpty){
            addEventSub(ne)
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
          persist(PAddSub(PpollEvent(ps.persist()))){ event =>
            addPollSub(ps)
          }
        case ps: PollNewEventSub =>
          persist(PAddSub(PpollNewEvent(ps.persist()))){ event =>
            addPollSub(ps)
          }
        case ps: PollIntervalSub =>
          persist(PAddSub(PpollInterval(ps.persist()))){ event =>
            addPollSub(ps)
          }

      }
      case Models.RemoveIntervalSub(id) => {
        val target = intervalSubs.get(id)
        target.fold(false){ sub =>
          intervalSubs = intervalSubs - id
          true
        }
      }
      case Models.RemoveEventSub(id: Long) => {
        if(eventSubs.values.exists(_.exists(_.id == id))){
          val newStore: Map[Path, Seq[EventSub]] =
            eventSubs
              .mapValues(subs => subs.filterNot(_.id == id)) //remove values that contain id
              .filterNot{case (_, subs) => subs.isEmpty } //remove keys with empty values
          eventSubs = newStore
          true
        } else{
          false
        }

      }
      case Models.RemovePollSub(id: Long) => {
        idToSub.get(id) match {
          case Some(pSub) => {
            idToSub = idToSub - id
            pSub.paths.foreach{ path =>
              pathToSubs(path) match {
                case ids if ids.size <= 1 => pathToSubs = pathToSubs - path
                case ids => pathToSubs = pathToSubs.updated(path, ids - id)
              }
            }
            true
          }
          case None => false
        }
      }
      case Models.PollSubCommand(id:Long) => {
        val sub = idToSub.get(id)
        sub.foreach {
          //Update the lastPolled timestamp
          case polledEvent: PollNormalEventSub =>
            idToSub = idToSub + (id -> polledEvent.copy(lastPolled = new Timestamp(new Date().getTime)))
          case polledNewEvent: PollNewEventSub =>
            idToSub = idToSub + (id -> polledNewEvent.copy(lastPolled = new Timestamp(new Date().getTime)))
          case pollInterval: PollIntervalSub =>
            idToSub = idToSub + (id -> pollInterval.copy(lastPolled = new Timestamp(new Date().getTime)))
        }
        sub
      }
    }
  }

  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: Any) => ???
  }
  def receiveCommand: Receive = {

    case aEventS @ Models.AddEventSub(eventSub: EventSub) =>
      sender() ! updateState(aEventS)
    case aIntervalS @ Models.AddIntervalSub(intervalSub: IntervalSub) =>
      sender() ! updateState(aIntervalS)
    case aPollS @ Models.AddPollSub(pollsub: PolledSub) =>
      sender() ! updateState(aPollS)
    case rIntervalS @ Models.RemoveIntervalSub(id:Long) =>
      persist(PRemoveSub(id)){event =>
        sender() ! updateState(rIntervalS)
      }
    case rEventS @ Models.RemoveEventSub(id:Long) =>
      persist(PRemoveSub(id)){event =>
        sender() ! updateState(rEventS)
      }
    case rPollS @ Models.RemovePollSub(id:Long) =>
      persist(PRemoveSub(id)){event =>
        sender() ! updateState(rPollS)
      }
    case pollS @ Models.PollSubCommand(id: Long) =>
      persist(PPollSub(id)){ event =>
        sender() ! updateState(pollS)
      }
    case Models.LookupEventSubs(path:Path) =>
      sender() ! path.getParentsAndSelf
        .flatMap(p => eventSubs.get(p))
        .flatten.collect{ case ns: NormalEventSub => ns }
        .toVector
    case Models.LookupNewEventSubs(path:Path) =>
      sender() ! path.getParentsAndSelf
        .flatMap(p => eventSubs.get(p))
        .flatten.collect{ case ns: NewEventSub => ns }
        .toVector
    case Models.GetAllEventSubs =>
      sender() ! eventSubs.values.flatten.toSet
    case Models.GetAllIntervalSubs =>
      sender() ! intervalSubs.values.toSet
    case Models.GetAllPollSubs =>
      sender() ! idToSub.values.toSet
    case Models.GetIntervalSub(id:Long) =>
      sender() ! intervalSubs.get(id)
    case Models.GetSubsForPath(path:Path) =>
      val ids = path.inits.flatMap(path => pathToSubs.get(path)).toSet.flatten
      sender() ! ids.map(idToSub(_)).collect{
        case events: PollNormalEventSub => events
        case intervals: PollIntervalSub => intervals
      }
    case Models.GetNewEventSubsForPath(path:Path) =>
      val ids = path.inits.flatMap(path => pathToSubs.get(path)).toSet.flatten
      sender() ! ids.map(idToSub(_)).collect{case news: PollNewEventSub => news}
  }

}
