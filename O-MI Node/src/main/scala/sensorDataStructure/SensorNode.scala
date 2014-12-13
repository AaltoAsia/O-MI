package sensorDataStructure

import scala.concurrent.stm._
import java.util.Date;
import scala.xml
/**
 * Abstract base class for sensors' data structures
 *
 * @param Path to were node is. Last part is key for this.
 *
 */
abstract sealed class SensorNode(val path: String) {
  def id = path.split("/").last
}
/**
 * Data structure for storing sensor data. a leaf.
 *
 * @param Path to were sensor is. Last part is key for this.
 * @param Actual value from sensor
 * @param SI unit
 *        Should be in UCUM format.
 *        Empty if unknown.
 */
case class SensorData(
  override val path: String,
  val xmlElem: xml.Elem 
  //val value: String, // is a basic numeric data type
  //val dateTime: String // find better one if possible...
  ) extends SensorNode(path)
/**
 * Data structure were sensors exist. a node.
 *
 * @param Path to were sensor is. Last part is key for this.
 */
case class SensorMap(override val path: String) extends SensorNode(path) {
  val content: TMap[String, SensorNode] = TMap.empty

  def get(pathTo: String): Option[SensorNode] = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => Some(sensor) //_ IS a basic numeric data type
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty) {
          Some(sensormap)
        } else
          sensormap.get(after.mkString("/"))
      }
      case _ => None
    }
  }

  def set(pathTo: String, node: SensorNode): Unit = {
    node match {
      case data: SensorData => {
        if (exists(pathTo))
          updateSensor(pathTo, data)
        else
          insertSensor(pathTo, data)
      }
      case sensorMap: SensorMap => {
        if (!exists(pathTo)) insertSensorMap(pathTo, sensorMap)
      }
      case _ =>
    }
  }

  def getChilds(pathTo: String): Array[String] = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(isSensorMap(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData) => Array.empty //_ IS a basic numeric data type
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty) {
          sensormap.content.single.keys.toArray
        } else
          sensormap.getChilds(after.mkString("/"))
      }
      case _ => Array.empty
    }
  }

  def exists(pathTo: String): Boolean = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => true
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
      case Some(sensor: SensorData) => true
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
      case Some(sensor: SensorData) => false
      case Some(sensormap: SensorMap) => {
        if (after.isEmpty)
          true
        else
          sensormap.isSensor(after.mkString("/"))
      }
      case _ => false
    }
  }

  private def updateSensor(pathTo: String, newsensor: SensorData): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(isSensor(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData) => { content.single.update(pathTo, newsensor) } //_ IS a basic numeric data type
      case Some(sensormap: SensorMap) => {
        sensormap.updateSensor(after.mkString("/"), newsensor)
      }
      case _ =>
    }
  }

  private def insertSensor(pathTo: String, newsensor: SensorData): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(!exists(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData) =>
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.insertSensor(after.mkString("/"), newsensor)
      }
      case _ => content.single.put(pathTo, newsensor)
    }
  }
  
  private def insertSensorMap(pathTo: String, newsensormap: SensorMap): Unit = {
    val spl = pathTo.split("/")
    val key = spl.head
    val after = spl.tail
    require(!exists(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData) =>
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
      case Some(sensor: SensorData) => content.single.remove(key)
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
    require(isSensorMap(pathTo))
    content.single.get(key) match {
      case Some(sensor: SensorData) =>
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

