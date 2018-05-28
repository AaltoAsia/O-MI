package database.journal
import akka.persistence._
import types.Path
import types.odf.{ODFValue, Value}

trait Event
trait Command
trait PersistentCommand extends Command
//Event and Commands are separate in case there is need to develop further and add Event and Command handlers
case class SingleWriteEvent(path: Path, value: ODFValue) extends Event
case class WriteEvent(paths: Map[Path,ODFValue]) extends Event
case class SingleReadCommand(path: Path) extends Command
case class SingleWriteCommand(path: Path, value: ODFValue) extends PersistentCommand
case class WriteCommand(paths: Map[Path,ODFValue]) extends PersistentCommand

class LatestStore extends PersistentActor {
  def persistenceId = "lateststore-id"
  var state: Map[Path, ODFValue] = Map()


  def updateState(evnt: Event): Unit = evnt match {
    case SingleWriteEvent(p, v) => state = state.updated(Path(p),v)
    case WriteEvent(paths) => state = state ++ paths
  }

  def receiveRecover: Receive = {
    case e: SingleWriteEvent => updateState(e)
    case SnapshotOffer(_,snapshot: Map[Path,ODFValue]) => state = snapshot
  }
  val snapshotInterval = 100
  def receiveCommand: Receive = {

    case SingleWriteCommand(p, v) =>{
      persist(SingleWriteEvent(p,v)){ event =>
        updateState(event)
        if(lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0){
          saveSnapshot(state)
        }
      }
    }
    case WriteCommand(paths) => {
      persist(WriteEvent(paths)){ event =>
        updateState(event)
        //TODO:snapshots

      }
    }
    case SingleReadCommand(p) => {

    }
  }

}
