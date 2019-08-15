package database.journal

import akka.actor.Props
import akka.persistence._
import scala.util.{Failure, Success, Try}

import LatestStore._
import Models._
import types.Path
import types.odf._


//Event and Commands are separate in case there is need to develop further and add Event and Command handlers

object LatestStore {
  //id given as parameter to make testing possible
  def props(id: String = "lateststore"): Props = Props(new LatestStore(id))

  //Latest store protocol
  case class SingleWriteCommand(path: Path, value: Value[Any]) extends PersistentCommand

  case class WriteCommand(paths: Map[Path, Value[Any]]) extends PersistentCommand

  case class ErasePathCommand(path: Path) extends PersistentCommand

  case class SingleReadCommand(path: Path) extends Command

  case class MultipleReadCommand(paths: Seq[Path]) extends Command

  case object ReadAllCommand extends Command
}
class LatestStore(override val persistenceId: String) extends JournalStore {

  private var state: Map[Path, Value[Any]] = Map()


  def updateState(event: Event): Unit = event match {
    case PWriteLatest(values) => state = state ++ values.map{case (path, value) => Path(path) -> asValue(value)}
    case PErasePath(path) => state = state - Path(path)

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

  def receiveCommand: Receive = receiveBoilerplate orElse {
    case SaveSnapshot(msg) => sender() ! saveSnapshot(
      PWriteLatest(
        state.map{
          case (key,value) => key.toString -> value.persist
        }
      )
    )

    case SingleWriteCommand(p, v) => {
      persist(PWriteLatest(Map(p.toString -> v.persist))) { event =>
        sender() ! updateState(event)
      }
    }

    case WriteCommand(paths) => {
      persist(PWriteLatest(paths.map { case (path, value) => path.toString -> value.persist })) { event =>
        sender() ! updateState(event)

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
      sender() ! resp
    }
    case ReadAllCommand => {
      val resp: Map[Path, Value[Any]] = state
      sender() ! resp
    }

  }

}
