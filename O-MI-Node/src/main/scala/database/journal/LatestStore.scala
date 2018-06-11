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
  var state: Map[String, PersistentValue] = Map()

  //implicit def pathToString(path: Path): String = path.toString
  def asValue(pv: PersistentValue): Option[Value[Any]] ={
    Try(pv.typeName match {
      case "xs:float" if pv.valueType.isProtoDoubleValue =>
        FloatValue(pv.getProtoDoubleValue.toFloat, new Timestamp(pv.timeStamp))
      case "xs:double" if pv.valueType.isProtoDoubleValue =>
        DoubleValue(pv.getProtoDoubleValue, new Timestamp(pv.timeStamp))
      case "xs:short" if pv.valueType.isProtoLongValue =>
        ShortValue(pv.getProtoLongValue.toShort, new Timestamp(pv.timeStamp))
      case "xs:int" if pv.valueType.isProtoLongValue =>
        IntValue(pv.getProtoLongValue.toInt, new Timestamp(pv.timeStamp))
      case "xs:long" if pv.valueType.isProtoLongValue =>
        LongValue(pv.getProtoLongValue, new Timestamp(pv.timeStamp))
      case "xs:boolean" if pv.valueType.isProtoBoolValue =>
        BooleanValue(pv.getProtoBoolValue, new Timestamp(pv.timeStamp))
      case "odf" if pv.valueType.isProtoStringValue =>
        ODFValue(ODFParser.parse(pv.getProtoStringValue).right.get, new Timestamp(pv.timeStamp))
      case str: String if pv.valueType.isProtoStringValue =>
        StringValue(pv.getProtoStringValue, new Timestamp(pv.timeStamp))
      case other => throw new Exception(s"Error while deserializing value: $other")
    }).toOption
  }
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
      persist(WriteLatest(Map(p.toString->v.persist))){ event =>
        updateState(event)
        if(lastSequenceNr % snapshotInterval == 0 && lastSequenceNr != 0){
          saveSnapshot(state)
        }
      }
    }
    case WriteCommand(paths) => {
      persist(WriteLatest(paths.map{ case (path, value) => path.toString -> value.persist})){ event =>
      //  persist(WriteLatest(paths.mapValues(_.persist))){ event =>
        updateState(event)
        //TODO:snapshots

      }
    }

    case SingleReadCommand(p) => {
      val resp: Option[Value[Any]] = state.get(p.toString).flatMap(asValue)
      sender() ! resp
    }
    case MultipleReadCommand(paths) => {
      val resp: Seq[(Path, Option[Value[Any]])] = paths.map(path=> path -> state.get(path.toString).flatMap(asValue))
      sender() ! resp
    }
    case ReadAllCommand => {
      val resp: Map[Path, Value[Any]] = state.map{case (path, pValue) => Path(path) -> asValue(pValue).get}
      sender() ! resp
    }

  }

}
