package database.journal

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import database.journal.Models._
import types.Path
import types.odf.Value

class PollDataStore extends PersistentActor with ActorLogging {
  def persistenceId: String = "polldatastore-id"

  var state: Map[Long,Map[String,Seq[PersistentValue]]] = Map()

  def mergeValues(a: Map[String, Seq[PersistentValue]], b: Map[String,Seq[PersistentValue]]):Map[String,Seq[PersistentValue]] = {
    merge(a,b){ case (v1,v2o) =>
      v2o.map(v2 => v1 ++ v2).getOrElse(v1)
    }
  }
  def updateState(event: Event) = event match {
    case PAddPollData(id,path, Some(value)) =>
      val newValue = Map(path -> Seq(value))
      state += state.get(id).map(pv => (id -> mergeValues(pv,newValue))).getOrElse((id -> newValue))
    case PPollEventSubscription(id) =>
      val data = state.get(id)
      state -= id
      data.getOrElse(Map.empty[String,Seq[PersistentValue]])
    case PPollIntervalSubscription(id) =>
      val oldVal = state.get(id)
      state -= id

      oldVal match {
        case Some(old) => {
          //add the empty sub as placeholder
          //p.idToData += (subId -> mutable.HashMap.empty)
          val newValues = old.mapValues(v => Seq(v.maxBy(_.timeStamp)))
          //add latest value back to db
          state += (id -> newValues)
          old
        }

        case None => Map.empty[String,Seq[PersistentValue]]
      }
    case PRemovePollSubData(id) =>
      state -= id
    case other => log.warning(s"Unknown Event $other")
  }

  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: Any) => ???
  }

  def receiveCommand: Receive = {
    case AddPollData(subId,path,value) =>
      persist(PAddPollData(subId,path.toString, Some(value.persist))){ event =>
        updateState(event)
      }
    case PollEventSubscription(id) =>
      persist(PPollEventSubscription(id)){event =>
        updateState(event)
      }
    case PollIntervalSubscription(id) =>
      persist(PPollIntervalSubscription(id)){event =>
        updateState(event)
      }
    case RemovePollSubData(id) =>
      persist(PRemovePollSubData(id)){ event =>
        updateState(event)
      }
    case CheckSubscriptionData(id) =>
      sender() ! state.getOrElse(id,Map.empty[String,Seq[PersistentValue]])
  }

}
