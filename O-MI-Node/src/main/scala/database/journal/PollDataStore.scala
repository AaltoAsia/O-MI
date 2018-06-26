package database.journal

import akka.actor.ActorLogging
import akka.persistence.{PersistentActor, SnapshotOffer}
import database.journal.Models._
import types.Path
import types.odf.Value

class PollDataStore extends PersistentActor with ActorLogging {
  def persistenceId: String = "polldatastore-id"

  var state: Map[Long,Map[String,Seq[PPersistentValue]]] = Map()

  def mergeValues(a: Map[String, Seq[PPersistentValue]], b: Map[String,Seq[PPersistentValue]]):Map[String,Seq[PPersistentValue]] = {
    merge(a,b){ case (v1,v2o) =>
      v2o.map(v2 => v1 ++ v2).getOrElse(v1)
    }
  }
  def updateState(event: Event) = event match {
    case PAddPollData(id,path, Some(value)) =>
      val newValue = Map(path -> Seq(value))
      state += state.get(id).map(pv => (id -> mergeValues(pv,newValue))).getOrElse((id -> newValue))
    case PPollEventSubscription(id) =>
      state -= id
    case PPollIntervalSubscription(id) =>
      val oldVal = state.get(id)
      state -= id

      oldVal.foreach(old => {
          //add the empty sub as placeholder
          //p.idToData += (subId -> mutable.HashMap.empty)
          val newValues = old.mapValues(v => Seq(v.maxBy(_.timeStamp)))
          //add latest value back to db
          state += (id -> newValues)
        })
    case PRemovePollSubData(id) =>
      state -= id
    case other => log.warning(s"Unknown Event $other")
  }

  def pollSubscription(event: PPollIntervalSubscription):Map[Path,Seq[Value[Any]]] = {
    val resp: Map[Path, Seq[Value[Any]]] =
      state.getOrElse(event.id, Map.empty).map{case (key, values) => Path(key) -> values.flatMap(asValue(_))}
    updateState(event)
    resp
  }
  def pollSubscription(event: PPollEventSubscription):Map[Path,Seq[Value[Any]]] = {
    val resp: Map[Path, Seq[Value[Any]]] =
      state.getOrElse(event.id, Map.empty).map{case (key, values) => Path(key) -> values.flatMap(asValue(_))}
    updateState(event)
    resp
  }

  def receiveRecover: Receive = {
    case event: Event => updateState(event)
    case SnapshotOffer(_, snapshot: PPollData) => state = snapshot.subs.mapValues(_.paths.mapValues(_.values))
  }

  def receiveCommand: Receive = {
    case AddPollData(subId,path,value) =>
      persist(PAddPollData(subId,path.toString, Some(value.persist))){ event =>
        updateState(event)
      }
    case PollEventSubscription(id) =>
      persist(PPollEventSubscription(id)){event =>
        sender() ! pollSubscription(event)
      }
    case PollIntervalSubscription(id) =>
      persist(PPollIntervalSubscription(id)){event =>
        sender() ! pollSubscription(event)
      }
    case RemovePollSubData(id) =>
      persist(PRemovePollSubData(id)){ event =>
        updateState(event)
      }
    case CheckSubscriptionData(id) =>
      sender() ! state.getOrElse(id,Map.empty[String,Seq[PPersistentValue]])
  }

}
