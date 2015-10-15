package database

import org.prevayler._
import java.util.Date

import 

// import database.DBValue

case class LatestStore(var allData: Map[Path, DBValue])

object LatestStore {
  def empty = LatestStore(Map.empty)
}

case class LookupSensorData(sensor: Path) extends Query[LatestStore] {
  def query(ls: LatestStore, d: Date): Option[DBValue] = ls.allData.get(sensor)
}

case class LookupSensorDatas(setsors: Seq[Path]) extends Query[LatestStore] {
  def query(ls: LatestStore, d: Date): Seq[DBValue] = {
    sensors.map(ls.allData get _).map(_.toList).flatten
  }
}

case class SetSensorData(sensor: Path, value: DBValue) extends Transaction[LatestStore] {
  def executeOn(ls: LatestStore, d: Date) = ls.allData = ls.allData + sensor -> value
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestStore] {
  def executeOn(ls: LatestStore, d: Date) = ls.allData = ls.allData - sensor
}
