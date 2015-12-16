package database

import java.util.Date

import org.prevayler._
import types.OdfTypes._
import types.Path

// TODO: save the whole InfoItem
/*case class LatestInfoItemData(
  val responsibleAgent: String,
  //val hierarchyID: Int,
  val metadataStr: Option[OdfMetaData] = None,
  val description: Option[OdfDescription] = None
  ) {
  def toOdfInfoItem(path: Path, value: OdfValue) = 
    OdfInfoItem(path, Iterable(value), description, metadataStr)
}
 */ 

case class LatestValues(var allData: Map[Path, OdfValue])
object LatestValues {
  type LatestStore = Prevayler[LatestValues]
  def empty = LatestValues(Map.empty)
}


case class LookupSensorData(sensor: Path) extends Query[LatestValues, Option[OdfValue]] {
  def query(ls: LatestValues, d: Date) = ls.allData.get(sensor)
}

case class LookupSensorDatas(sensors: Seq[Path]) extends Query[LatestValues, Seq[OdfValue]] {
  def query(ls: LatestValues, d: Date) = {
    sensors.map(ls.allData get _).map(_.toList).flatten
  }
}
case class LookupAllDatas() extends Query[LatestValues, Map[Path, OdfValue]] {
  def query(ls: LatestValues, d: Date) = ls.allData
}

case class SetSensorData(sensor: Path, value: OdfValue) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData + (sensor -> value)
}

case class EraseSensorData(sensor: Path) extends Transaction[LatestValues] {
  def executeOn(ls: LatestValues, d: Date) = ls.allData = ls.allData - sensor
}


case class OdfTree(var root: OdfObjects)
object OdfTree {
  type OdfTreeStore = Prevayler[OdfTree]
  def empty = OdfTree(OdfObjects())
}

case class GetTree() extends Query[OdfTree, OdfObjects] {
  def query(t: OdfTree, d: Date) = t.root
}

case class Union(anotherRoot: OdfObjects) extends Transaction[OdfTree] {
  def executeOn(t: OdfTree, d: Date) = t.root = t.root combine anotherRoot
}

case class TreeRemovePath(path: Path) extends Transaction[OdfTree] {
  private def removeRecursion(elem: OdfNode) = {
    ???
  }
  def executeOn(t: OdfTree, d: Date) = {
    ???
  }
}
