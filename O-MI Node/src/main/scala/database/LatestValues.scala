package database

import java.sql.Timestamp
import java.util.Date

import org.prevayler._
import types.OdfTypes._
import types.Path

import scala.collection.immutable.HashMap

// TODO: save the whole InfoItem
/*case class LatestInfoItemData(
  val responsibleAgent: String,
  //val hierarchyID: Int,
  val metadataStr: Option[OdfMetaData] = None,
  val description: Option[OdfDescription] = None
  ) {
  def toOdfInfoItem(path: Path, value: OdfValue) = 
    OdfInfoItem(path, Iterable(value), description, metadataStr)
}
 */ 

case class LatestValues(var allData: Map[Path, OdfValue])
object LatestValues {
  type LatestStore = Prevayler[LatestValues]
  def empty = LatestValues(Map.empty)
}


case class LookupSensorData(sensor: Path) extends Query[LatestValues, Option[OdfValue]] {
  def query(ls: LatestValues, d: Date) = ls.allData.get(sensor)
}

case class LookupSensorDatas(sensors: Seq[Path]) extends Query[LatestValues, Seq[(Path, OdfValue)]] {
  def query(ls: LatestValues, d: Date) = {
    (for (sensorPath <- sensors) yield {
      val dataOpt = ls.allData get sensorPath
      (dataOpt map {data => (sensorPath, data)}).toList
    }).flatten
  }
}
case class LookupAllDatas() extends Query[LatestValues, Map[Path, OdfValue]] {
  def query(ls: LatestValues, d: Date) = ls.allData
}

case class SetSensorData(sensor: Path, value: OdfValue) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData + (sensor -> value)
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData - sensor
}


case class OdfTree(var root: OdfObjects)
object OdfTree {
  type OdfTreeStore = Prevayler[OdfTree]
  def empty = OdfTree(OdfObjects())
}

case class GetTree() extends Query[OdfTree, OdfObjects] {
  def query(t: OdfTree, d: Date) = t.root
}

/* This can be used for updating also */
case class Union(anotherRoot: OdfObjects) extends Transaction[OdfTree] {
  def executeOn(t: OdfTree, d: Date) = t.root = t.root combine anotherRoot
}

case class TreeRemovePath(path: Path) extends Transaction[OdfTree] {
  private def removeRecursion(elem: OdfNode) = {
    ???
  }
  def executeOn(t: OdfTree, d: Date) = {
    ???
  }
}
case class RemoveIntervalSub(id: Long) extends TransactionWithQuery[IntervalSubs, Boolean] {
    def executeAndQuery(store: IntervalSubs, d: Date): Boolean={
      val target = store.intervalSubs.find( _.id == id)
      target.fold(false){ sub =>
        store.intervalSubs = store.intervalSubs - sub
        true
      }

    }
  }

  /**
   * Transaction to remove subscription from event subscriptions
   * @param id id of the subscription to remove
   */
  case class RemoveEventSub(id: Long) extends  TransactionWithQuery[EventSubs, Boolean] {
    def executeAndQuery(store:EventSubs, d: Date): Boolean = {
      if(store.eventSubs.values.exists(_.exists(_.id == id))){
        val newStore: HashMap[Path, Seq[EventSub]] =
          store.eventSubs
            .mapValues(subs => subs.filterNot(_.id == id)) //remove values that contain id
            .filterNot( kv => kv._2.isEmpty ) //remove keys with empty values
            .map(identity)(collection.breakOut) //map to HashMap //TODO create helper method for matching
        store.eventSubs = newStore
        true
      } else{
        false
      }
    }
  }

  case class RemovePollSub(id: Long) extends TransactionWithQuery[PolledSubs, Boolean] {
    def executeAndQuery(store: PolledSubs, d: Date): Boolean = {
      store.idToSub.get(id) match {
        case Some(pSub) => {
          store.idToSub = store.idToSub - id
          pSub.paths.foreach{ path =>
            store.pathToSubs(path) match {
              case ids if ids.size <= 1 => store.pathToSubs = store.pathToSubs - path
              case ids                  => store.pathToSubs = store.pathToSubs.updated(path, ids - id)
            }
          }
          true
        }
        case None => false
      }
    }
  }

  case class PollSub(id: Long) extends TransactionWithQuery[PolledSubs, Option[PolledSub]] {
    def executeAndQuery(store: PolledSubs, d: Date): Option[PolledSub] = {
      val sub = store.idToSub.get(id)
      //sub.foreach(a => store.polledSubs = store.polledSubs.updated(id)) TODO update store and return value
      sub
    }
  }

  case object getAndUpdateId extends TransactionWithQuery[SubIds, Long] {
    override def executeAndQuery(p: SubIds, date: Date): Long = {
      p.id = p.id + 1
      p.id
    }
  }

  /**
   * Transaction to get the intervalSub with the earliest interval
   */

  case object GetIntervals extends TransactionWithQuery[IntervalSubs, (Set[IntervalSub], Option[Timestamp])] {
    def executeAndQuery(store: IntervalSubs, d: Date): (Set[IntervalSub], Option[Timestamp]) = {
      val (passedIntervals, rest) = store.intervalSubs.span(_.nextRunTime.before(d))// match { case (a,b) => (a, b.headOption)}
      val newIntervals = passedIntervals.map{a =>
          val numOfCalls = (d.getTime() - a.startTime.getTime) / a.interval.toMillis
          val newTime = new Timestamp(a.startTime.getTime + a.interval.toMillis * (numOfCalls + 1))
          a.copy(nextRunTime = newTime)}
      store.intervalSubs = rest ++ newIntervals
      (newIntervals, store.intervalSubs.headOption.map(_.nextRunTime))
    }

  }

//TODO EventSub
  case class AddEventSub(eventSub: EventSub) extends Transaction[EventSubs] {
    def executeOn(store: EventSubs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      //val currentTime: Long = System.currentTimeMillis()

      val scheduleTime: Long = eventSub.endTime.getTime - d.getTime // eventSub.ttl match

      if(scheduleTime > 0L){
        val newSubs: HashMap[Path, Seq[EventSub]] = HashMap(eventSub.paths.map(n => (n -> Seq(eventSub))): _*)
        store.eventSubs = store.eventSubs.merged[Seq[EventSub]](newSubs)((a, b) => (a._1, a._2 ++ b._2))
      }
    }
  }

  /*
  re schedule when starting in new subscription transactions
  */

  case class AddIntervalSub(intervalSub: IntervalSub) extends Transaction[IntervalSubs] {
    def executeOn(store: IntervalSubs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      val scheduleTime: Long = intervalSub.endTime.getTime - d.getTime
      if(scheduleTime > 0){
        store.intervalSubs = store.intervalSubs + intervalSub//TODO check this
      }

    }
  }

    case class AddPollSub(polledSub: PolledSub) extends Transaction[PolledSubs] {
      def executeOn(store: PolledSubs, d: Date) = {
        val scheduleTime: Long = polledSub.endTime.getTime - d.getTime
        if (scheduleTime > 0){
          store.idToSub = store.idToSub + (polledSub.id -> polledSub)

          //update mapping from path to Ids
          polledSub.paths.foreach{ subPath =>
            store.pathToSubs.get(subPath) match {
              case Some(idSeq) => store.pathToSubs = store.pathToSubs.updated(subPath, idSeq.+(polledSub.id))
              case _ => store.pathToSubs = store.pathToSubs.updated(subPath, Set(polledSub.id))
            }
          }
        }
      }
    }

  case class GetAllEventSubs() extends Query[EventSubs, Set[EventSub]] {
    def query(store: EventSubs, d: Date): Set[EventSub] = {
      store.eventSubs.values.flatten.toSet
    }
  }

  case class GetAllIntervalSubs() extends Query[IntervalSubs, Set[IntervalSub]] {
    def query(store: IntervalSubs, d: Date): Set[IntervalSub] = {
      store.intervalSubs.toSet
    }
  }

  case class GetAllPollSubs() extends Query[PolledSubs, Set[PolledSub]] {
    def query(store: PolledSubs, d: Date): Set[PolledSub] = {
      store.idToSub.values.toSet
    }
  }
