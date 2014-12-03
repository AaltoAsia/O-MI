package SensorDataStructure

import scala.concurrent.stm._
import java.util.Date;
/**
 * Abstract base class for sensors' data atructures
 *
 * @param Path to were node is. Last part is kye for this.
 *
 */
abstract sealed class SensorNode(val path: String) {
  def id = path.split("/").last
}
/**
 * Data structure for storing sensor data. a leaf.
 *
 * @tparam Numeric basic data type of actual sensor value
 * @param Path to were sensor is. Last part is kye for this.
 * @param SI unit
 *        Should be in UCUM format.
 *        Empty if unknown.
 * @param Actual value from sensor
 */
case class SensorData[T](
  override val path: String,
  val unit: String,
  val value: T, // is a basic numeric data type
  val timestamp: Date // find better one if possible...
  ) extends SensorNode(path)
/**
 * Data structure were sensors exist. a node.
 *
 * @param Path to were sensor is. Last part is kye for this.
 */
case class SensorMap(override val path: String) extends SensorNode(path) {
  val content: TMap[String, SensorNode] = TMap.empty
  
  def get(pathTo: String): Option[SensorNode] = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => Some(sensor)  //_ IS a basic numeric data type
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty) {
          Some(sensormap)
        } else
          sensormap.get(after.mkString("/"))
      }
      case _ => None
    }
  }
  def exist(pathTo: String): Boolean = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => true
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty)
          true
        else
          sensormap.isSensor(after.mkString("/"))
      }
      case _ => false
    }
  }
  def isSensor(pathTo: String): Boolean = {

    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => true
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty)
          false
        else
          sensormap.isSensor(after.mkString("/"))
      }
      case _ => false
    }
  }
  def isSensorMap(pathTo: String): Boolean = {

    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => false
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty)
          true
        else
          sensormap.isSensor(after.mkString("/"))
      }
      case _ => false
    }
  }
  def updateSensor(pathTo: String, newsensor: SensorData[_]): Unit = {
      val spl = pathTo.split("/")
      val key = spl.head
      val after = spl.tail
      require(exist(pathTo) && isSensor(pathTo))
      content.single.get(key) match {
        case Some(sensor: SensorData[_]) => { content.single.update(pathTo, newsensor) } //_ IS a basic numeric data type
        case Some(sensormap: SensorMap) => {
          sensormap.updateSensor(after.mkString("/"), newsensor)
        }
        case _ => 
      }
    }
  def insertSensor(pathTo: String, newsensor: SensorData[_]): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(!exist(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => 
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.insertSensor(after.mkString("/"), newsensor)
      }
      case _ => content.single.put(pathTo, newsensor)
    }
  }
  def insertSensorMap(pathTo: String, newsensormap: SensorData[_]): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(!exist(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => 
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.insertSensorMap(after.mkString("/"), newsensormap)
      }
      case _ => content.single.put(pathTo, newsensormap)
    }
  }
  def removeSensor(pathTo: String): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(isSensor(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => content.single.remove(key)
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.removeSensor(after.mkString("/"))
      }
      case _ => 
    }
  }
  def removeSensorMap(pathTo: String): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(isSensor(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData[_]) => 
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.removeSensor(after.mkString("/"))
        else
          content.single.remove(key)
      }
      case _ => 
    }
  }
}

