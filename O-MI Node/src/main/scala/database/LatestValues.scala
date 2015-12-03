package database

import collection.JavaConversions.asJavaIterable
import org.prevayler._
import java.util.Date

import types.Path
import types.OdfTypes._

// TODO: save the whole InfoItem!!!!
case class LatestInfoItemData(
  val value: OdfValue,
  val responsibleAgent: String,
  val hierarchyID: Int,
  val metadataStr: Option[OdfMetaData] = None,
  val description: Option[OdfDescription] = None
  ) {
  def toOdfInfoItem(path: Path) = 
    OdfInfoItem(path, Iterable(value), description, metadataStr)
}

case class LatestValues(var allData: Map[Path, LatestInfoItemData])
object LatestValues {
  type LatestStore = Prevayler[LatestValues]
  def empty = LatestValues(Map.empty)
}

case class LookupSensorData(sensor: Path) extends Query[LatestValues, Option[LatestInfoItemData]] {
  def query(ls: LatestValues, d: Date) = ls.allData.get(sensor)
}

case class LookupSensorDatas(sensors: Seq[Path]) extends Query[LatestValues, Seq[LatestInfoItemData]] {
  def query(ls: LatestValues, d: Date) = {
    sensors.map(ls.allData get _).map(_.toList).flatten
  }
}

case class SetSensorData(sensor: Path, value: LatestInfoItemData) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData + (sensor -> value)
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData - sensor
}
