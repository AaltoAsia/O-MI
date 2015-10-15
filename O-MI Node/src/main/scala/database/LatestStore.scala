package database

import org.prevayler._
import java.util.Date

import types.Path

// import database.DBValue

case class LatestStore(var allData: Map[Path, DBValue])

object LatestStore {
  def empty = LatestStore(Map.empty)
}

case class LookupSensorData(sensor: Path) extends Query[LatestStore, Option[DBValue]] {
  def query(ls: LatestStore, d: Date) = ls.allData.get(sensor)
}

case class LookupSensorDatas(sensors: Seq[Path]) extends Query[LatestStore, Seq[DBValue]] {
  def query(ls: LatestStore, d: Date) = {
    sensors.map(ls.allData get _).map(_.toList).flatten
  }
}

case class SetSensorData(sensor: Path, value: DBValue) extends Transaction[LatestStore] {
  def executeOn(ls: LatestStore, d: Date) = ls.allData = ls.allData + (sensor -> value)
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestStore] {
  def executeOn(ls: LatestStore, d: Date) = ls.allData = ls.allData - sensor
}
