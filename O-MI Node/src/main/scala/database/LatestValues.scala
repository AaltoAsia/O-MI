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

import scala.collection.immutable.HashMap

import org.prevayler._
import types.OdfTypes._
import types.Path

// TODO: save the whole InfoItem
/*case class LatestInfoItemData(
  val responsibleAgent: String,
  //val hierarchyID: Int,
  val metadataStr: Option[OdfMetaData] = None,
  val description: Option[OdfDescription] = None
  ) {
  def toOdfInfoItem(path: Path, value: OdfValue[Any]) = 
    OdfInfoItem(path, Iterable(value), description, metadataStr)
}
 */ 

/**
 * The latest values should be stored here. Contains only the latest value for each path.
 */
case class LatestValues(var allData: Map[Path, OdfValue[Any]])
object LatestValues {
  type LatestStore = Prevayler[LatestValues]
  def empty = LatestValues(Map.empty)
}


case class LookupSensorData(sensor: Path) extends Query[LatestValues, Option[OdfValue[Any]]] {
  def query(ls: LatestValues, d: Date) = ls.allData.get(sensor)
}

case class LookupSensorDatas(sensors: Vector[Path]) extends Query[LatestValues, Vector[(Path, OdfValue[Any])]] {
  def query(ls: LatestValues, d: Date) = {
    (for (sensorPath <- sensors) yield {
      val dataOpt = ls.allData get sensorPath
      (dataOpt map {data => (sensorPath, data)}).toList
    }).flatten
  }
}
case class LookupAllDatas() extends Query[LatestValues, Map[Path, OdfValue[Any]]] {
  def query(ls: LatestValues, d: Date) = ls.allData
}

case class SetSensorData(sensor: Path, value: OdfValue[Any]) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData + (sensor -> value)
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData - sensor
}


/**
 * Stores hierarchy of the odf, so it removes all values if they end up in this tree
 */
case class OdfTree(var root: OdfObjects)
object OdfTree {
  type OdfTreeStore = Prevayler[OdfTree]
  def empty = OdfTree(OdfObjects())
}

case class GetTree() extends Query[OdfTree, OdfObjects] {
  def query(t: OdfTree, d: Date) = t.root
}

/**
 * This is used for updating also
 */
case class Union(anotherRoot: OdfObjects) extends Transaction[OdfTree] {
  def executeOn(t: OdfTree, d: Date) = t.root = t.root union anotherRoot.valuesRemoved  // Remove values so they don't pile up
}

case class TreeRemovePath(path: Path) extends Transaction[OdfTree] {

  def executeOn(t: OdfTree, d: Date) = {
    val nodeOption = t.root.get(path)
    nodeOption match {
      case Some(ob: OdfObject)   => {
        t.root = (t.root -- createAncestors(ob)).valuesRemoved
      }
      case Some(ii: OdfInfoItem) => {
        t.root = (t.root -- createAncestors(ii)).valuesRemoved
      }
      case Some(objs: OdfObjects)  => {
        t.root = OdfObjects()
      } //remove whole tree
      case _ => //noop
    }
  }
}
case class RemoveIntervalSub(id: Long) extends TransactionWithQuery[Subs, Boolean] {
    def executeAndQuery(store: Subs, d: Date): Boolean = {
      val target = store.intervalSubs.get(id)
      target.fold(false){ sub =>
        store.intervalSubs = store.intervalSubs - id
        true
      }

    }
  }

  /**
   * Transaction to remove subscription from event subscriptions
   * @param id id of the subscription to remove
   */
  case class RemoveEventSub(id: Long) extends  TransactionWithQuery[Subs, Boolean] {
    def executeAndQuery(store:Subs, d: Date): Boolean = {
      if(store.eventSubs.values.exists(_.exists(_.id == id))){
        val newStore: HashMap[Path, Vector[EventSub]] =
          store.eventSubs
            .mapValues(subs => subs.filterNot(_.id == id)) //remove values that contain id
            .filterNot{case (_, subs) => subs.isEmpty } //remove keys with empty values
            .map(identity)(collection.breakOut) //map to HashMap //TODO create helper method for matching
        store.eventSubs = newStore
        true
      } else{
        false
      }
    }
  }

  case class RemovePollSub(id: Long) extends TransactionWithQuery[Subs, Boolean] {
    def executeAndQuery(store: Subs, d: Date): Boolean = {
      store.idToSub.get(id) match {
        case Some(pSub) => {
          store.idToSub = store.idToSub - id
          pSub.paths.foreach{ path =>
            store.pathToSubs(path) match {
              case ids if ids.size <= 1 => store.pathToSubs = store.pathToSubs - path
              case ids => store.pathToSubs = store.pathToSubs.updated(path, ids - id)
            }
          }
          true
        }
        case None => false
      }
    }
  }

  /*case class NewPollDataEvent(paths: Vector[(Path,OdfValue[Any])]) extends Query[Subs, Seq[((Path, OdfValue[Any]), Set[Long])]] {
    def query(store: Subs, d: Date): Vector[((Path, OdfValue[Any]), Set[Long])] = {
      paths.map(path => (path, store.pathToSubs(path._1)))
    }
  }*/

  case class PollSub(id: Long) extends TransactionWithQuery[Subs, Option[PolledSub]] {
    def executeAndQuery(store: Subs, d: Date): Option[PolledSub] = {
      val sub = store.idToSub.get(id)
      sub.foreach {
        //Update the lastPolled timestamp
        case polledEvent: PollNormalEventSub =>
          store.idToSub = store.idToSub + (id -> polledEvent.copy(lastPolled = new Timestamp(d.getTime())))
        case polledNewEvent: PollNewEventSub =>
          store.idToSub = store.idToSub + (id -> polledNewEvent.copy(lastPolled = new Timestamp(d.getTime())))
        case pollInterval: PollIntervalSub =>
          store.idToSub = store.idToSub + (id -> pollInterval.copy(lastPolled = new Timestamp(d.getTime())))
      }
      sub
    }
  }

  case object GetAndUpdateId extends TransactionWithQuery[SubIds, Long] {
    override def executeAndQuery(p: SubIds, date: Date): Long = {
      p.id = p.id + 1
      p.id
    }
  }


//TODO EventSub
  case class AddEventSub(eventSub: EventSub) extends Transaction[Subs] {
    def executeOn(store: Subs, d: Date) = {
      //val sId = subIDCounter.single.getAndTransform(_+1)
      //val currentTime: Long = System.currentTimeMillis()

      val scheduleTime: Long = eventSub.endTime.getTime - d.getTime // eventSub.ttl match

      if(scheduleTime > 0L){
        val newSubs: HashMap[Path, Vector[EventSub]] = HashMap(eventSub.paths.map(n => (n -> Vector(eventSub))): _*)
        store.eventSubs = store.eventSubs.merged[Vector[EventSub]](newSubs){
          case ((path, subsA), (_, subsB)) => (path, subsA ++ subsB)
        }
      }
    }
  }

  /*
  re schedule when starting in new subscription transactions
  */

  case class AddIntervalSub(intervalSub: IntervalSub) extends Transaction[Subs] {
    def executeOn(store: Subs, d: Date) = {
      val scheduleTime: Long = intervalSub.endTime.getTime - d.getTime
      if(scheduleTime > 0){
        store.intervalSubs = store.intervalSubs.updated(intervalSub.id, intervalSub)//) intervalSub//TODO check this
      }
    }
  }

    case class AddPollSub(polledSub: PolledSub) extends Transaction[Subs] {
      def executeOn(store: Subs, d: Date) = {
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

  case class GetAllEventSubs() extends Query[Subs, Set[EventSub]] {
    def query(store: Subs, d: Date): Set[EventSub] = {
      store.eventSubs.values.flatten.toSet
    }
  }

  case class GetAllIntervalSubs() extends Query[Subs, Set[IntervalSub]] {
    def query(store: Subs, d: Date): Set[IntervalSub] = {
      store.intervalSubs.values.toSet
    }
  }

  case class GetIntervalSub(id: Long) extends Query[Subs, Option[IntervalSub]] {
    def  query(store: Subs, d: Date): Option[IntervalSub] = {
      store.intervalSubs.get(id)
    }
  }

  case class GetAllPollSubs() extends Query[Subs, Set[PolledSub]] {
    def query(store: Subs, d: Date): Set[PolledSub] = {
      store.idToSub.values.toSet
    }
  }

  //case class RemovePathFromIntervalSubs(path: Path) extends Transaction[Subs] {
  //  def executeOn(store:Subs, d: Date): Unit = {
  //    store.intervalSubs = store.intervalSubs.
  //  }
  //}

  case class GetSubsForPath(path: Path) extends Query[Subs, Set[PollNormalEventSub]] {
    def query(store: Subs, d: Date): Set[PollNormalEventSub] = {
      val ids = path.inits.flatMap(path => store.pathToSubs.get(path)).toSet.flatten
      //val ids = store.pathToSubs.get(path).toSet.flatten
      ids.map(store.idToSub(_)).collect{case norms: PollNormalEventSub => norms}
    }
  }

  case class GetNewEventSubsForPath(path: Path) extends Query[Subs, Set[PollNewEventSub]] {
    def query(store: Subs, d: Date): Set[PollNewEventSub] = {
      val ids = path.inits.flatMap(path => store.pathToSubs.get(path)).toSet.flatten
      ids.map(store.idToSub(_)).collect{case news: PollNewEventSub => news}
    }
  }
