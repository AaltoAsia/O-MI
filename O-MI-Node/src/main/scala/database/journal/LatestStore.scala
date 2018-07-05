package database.journal

import java.sql.Timestamp

import akka.actor.ActorLogging
import akka.persistence._
import database.journal.Models._
import types.Path
import types.odf._

import scala.util.{Failure, Success, Try}


//Event and Commands are separate in case there is need to develop further and add Event and Command handlers


class LatestStore extends PersistentActor with ActorLogging {
  def persistenceId = "lateststore"

  var state: Map[Path, Value[Any]] = Map()

  //implicit def pathToString(path: Path): String = path.toString

  def updateState(event: Event): Unit = event match {
    case PWriteLatest(values) => state = state ++ values.map{case (path, value) => Path(path) -> asValue(value)}
    case PErasePath(path) => state = state - Path(path)

    //case SingleWriteEvent(p, v) => state = state.updated(Path(p),v)
    //case WriteEvent(paths) => state = state ++ paths
  }

  def receiveRecover: Receive = {
    case evnt: Event => updateState(evnt)
    //case e: SingleWriteEvent => updateState(e)
    case SnapshotOffer(_, snapshot: PWriteLatest) => {
      Try(state = snapshot.values.map{case (path, value) => Path(path) -> asValue(value)}) match{
        case Success(s) =>
        case Failure(ex) => log.error(ex, "Failure while writing snapshot")
      }
    }
  }

  val snapshotInterval = 100

  def receiveCommand: Receive = {
    case SaveSnapshot(msg) => sender() ! saveSnapshot(
      PWriteLatest(
        state.map{
          case (key,value) => key.toString -> value.persist
        }
      )
    )
    case SaveSnapshotSuccess(metadata)         ⇒ log.debug(metadata.toString)
    case SaveSnapshotFailure(metadata, reason) ⇒ log.error(reason,  s"Save snapshot failure with: ${metadata.toString}")

    case SingleWriteCommand(p, v) => {
      persist(PWriteLatest(Map(p.toString -> v.persist))) { event =>
        sender() ! updateState(event)
        // if(lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0){
        //   saveSnapshot(state)
        // }
      }
    }

    case WriteCommand(paths) => {
      persist(PWriteLatest(paths.map { case (path, value) => path.toString -> value.persist })) { event =>
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
      val resp: Option[Value[Any]] = state.get(p)
      sender() ! resp
    }
    case MultipleReadCommand(paths) => {
      val resp: Seq[(Path, Value[Any])] = paths.flatMap { path =>
        (for {
          value <- state.get(path)
        } yield path -> value)
      }
      // val resp: Seq[(Path, Value[Any])] = paths.flatMap(path=> state.get(path.toString).map(value => path -> asValue(value)).toSeq)
      sender() ! resp
    }
    case ReadAllCommand => {
      val resp: Map[Path, Value[Any]] = state
      sender() ! resp
    }

  }

}
