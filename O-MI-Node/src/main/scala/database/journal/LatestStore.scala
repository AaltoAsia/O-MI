package database.journal
import akka.persistence._
import database.journal.Models.{Event, SingleReadCommand, SingleWriteCommand, WriteCommand}
import types.Path
import types.odf.ODFValue


//Event and Commands are separate in case there is need to develop further and add Event and Command handlers


class LatestStore extends PersistentActor {
  def persistenceId = "lateststore-id"
  var state: Map[String, PersistentValue] = Map()


  def updateState(evnt: Event): Unit = evnt match {
    case WriteLatest(values) => state = state ++ values
    //case SingleWriteEvent(p, v) => state = state.updated(Path(p),v)
    //case WriteEvent(paths) => state = state ++ paths
  }
  def receiveRecover: Receive = {
    case evnt: Event => updateState(evnt)
    //case e: SingleWriteEvent => updateState(e)
    case SnapshotOffer(_,snapshot: Map[String,PersistentValue]) => state = snapshot
  }
  val snapshotInterval = 100
  def receiveCommand: Receive = {

    case SingleWriteCommand(p, v) =>{
      persist(WriteLatest()){ event =>
        updateState(event)
        if(lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0){
          saveSnapshot(state)
        }
      }
    }
    case WriteCommand(paths) => {
      persist(WriteLatest()){ event =>
        updateState(event)
        //TODO:snapshots

      }
    }
    case SingleReadCommand(p) => {

    }
  }

}
