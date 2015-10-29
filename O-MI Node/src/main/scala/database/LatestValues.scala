package database

import org.prevayler._
import java.util.Date

import types.Path

// import database.DBValue

case class LatestValues(var allData: Map[Path, DBValue])
object LatestValues {
  type LatestStore = Prevayler[LatestValues]
  def empty = LatestValues(Map.empty)
}

case class LookupSensorData(sensor: Path) extends Query[LatestValues, Option[DBValue]] {
  def query(ls: LatestValues, d: Date) = ls.allData.get(sensor)
}

case class LookupSensorDatas(sensors: Seq[Path]) extends Query[LatestValues, Seq[DBValue]] {
  def query(ls: LatestValues, d: Date) = {
    sensors.map(ls.allData get _).map(_.toList).flatten
  }
}

case class SetSensorData(sensor: Path, value: DBValue) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData + (sensor -> value)
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData - sensor
}
