package database.journal
import java.sql.Timestamp

import akka.actor.ActorLogging
import akka.persistence._
import database.journal.Models._
import types.Path
import types.odf._

import scala.util.Try


//Event and Commands are separate in case there is need to develop further and add Event and Command handlers


class LatestStore extends PersistentActor with ActorLogging {
  def persistenceId = "lateststore-id"
  var state: Map[String, PPersistentValue] = Map()

  //implicit def pathToString(path: Path): String = path.toString

  def updateState(event: Event): Unit = event match {
    case PWriteLatest(values) => state = state ++ values
    case PErasePath(path) => state = state - path

    //case SingleWriteEvent(p, v) => state = state.updated(Path(p),v)
    //case WriteEvent(paths) => state = state ++ paths
  }
  def receiveRecover: Receive = {
    case evnt: Event => updateState(evnt)
    //case e: SingleWriteEvent => updateState(e)
    case SnapshotOffer(_,snapshot: PWriteLatest) => state = snapshot.values
  }
  val snapshotInterval = 100
  def receiveCommand: Receive = {
    case SaveSnapshot(msg) => saveSnapshot(PWriteLatest(state))

    case SingleWriteCommand(p, v) =>{
      persist(PWriteLatest(Map(p.toString->v.persist))){ event =>
        sender() ! updateState(event)
       // if(lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0){
       //   saveSnapshot(state)
       // }
      }
    }

    case WriteCommand(paths) => {
      persist(PWriteLatest(paths.map{ case (path, value) => path.toString -> value.persist})){ event =>
      //  persist(WriteLatest(paths.mapValues(_.persist))){ event =>
        sender() ! updateState(event)
        //TODO:snapshots

      }
    }

    case ErasePathCommand(path) => {
      persist(PErasePath(path.toString)) { event =>
        sender() ! updateState(event)
      }
    }

    case SingleReadCommand(p) => {
      val resp: Option[Value[Any]] = state.get(p.toString).flatMap(asValue)
      sender() ! resp
    }
    case MultipleReadCommand(paths) => {
      val resp: Seq[(Path, Value[Any])] = paths.flatMap{ path =>
        (for {
          persistentValue <- state.get(path.toString)
          value <- asValue(persistentValue)
        } yield path -> value).toSeq
      }
     // val resp: Seq[(Path, Value[Any])] = paths.flatMap(path=> state.get(path.toString).map(value => path -> asValue(value)).toSeq)
      sender() ! resp
    }
    case ReadAllCommand => {
      val resp: Map[Path, Value[Any]] = state.map{case (path, pValue) => Path(path) -> asValue(pValue).get}
      sender() ! resp
    }

  }

}
