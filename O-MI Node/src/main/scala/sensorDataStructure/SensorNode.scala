package sensorDataStructure

import scala.concurrent.stm._
import java.util.Date;
import scala.xml

trait SDSType
case class Sensor() extends SDSType
case class Map() extends SDSType

abstract trait SDSMsg
abstract trait SDSSuccess extends SDSMsg
abstract trait SDSError extends SDSMsg
case class UpdateSuccess( pathTo: String ) extends SDSSuccess
case class MapRemoveSuccess( pathTo: String ) extends SDSSuccess
case class SensorRemoveSuccess( pathTo: String ) extends SDSSuccess
case class MapInsertionSuccess( pathTo: String ) extends SDSSuccess
case class MapAllreadyExistError( pathTo: String ) extends SDSError
case class SensorInsertionSuccess( pathTo: String ) extends SDSSuccess
case class SensorAllreadyExistError( pathTo: String ) extends SDSError
case class PathError( pathTo: String, msg: String ) extends SDSError
case class PathNotFoundError( pathTo: String) extends SDSError
case class WrongTypeFoundError( pathTo: String) extends SDSError
case class UnknownError( pathTo: String, msg: String) extends SDSError

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
  //val xmlElem: xml.Node 
  val value: String, // is a basic numeric data type
  val dateTime: String // find better one if possible...
  ) extends SensorNode(path)
/**
 * Data structure were sensors exist. a node.
 *
 * @param Path to were sensor is. Last part is key for this.
 */
case class SensorMap(override val path: String) extends SensorNode(path) {
  val content: TMap[String, SensorNode] = TMap.empty
  val label: String = ???

  def get(pathTo: String): Option[SensorNode] = {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
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

  def set(pathTo: String, node: SensorNode): SDSMsg = {
    node match {
      case data: SensorData => {
        if (exists(pathTo))
          updateSensor(pathTo, data)
        else
          insertSensor(pathTo, data)
      }
      case sensorMap: SensorMap => {
        if (!exists(pathTo))
          insertSensorMap(pathTo, sensorMap)
        else
          PathNotFoundError(pathTo)
      }
      case _ => WrongTypeFoundError("")
    }
  }

  def getChilds(pathTo: String): Array[String] = {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
    val after = spl.tail
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
    val key = if( spl.head == id) spl.tail.head else spl.head
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
    val key = if( spl.head == id) spl.tail.head else spl.head
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
    val key = if( spl.head == id) spl.tail.head else spl.head
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

  private def updateSensor(pathTo: String, newsensor: SensorData): SDSMsg= {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => { 
        if(after.isEmpty){
          content.single.update(pathTo, newsensor)  //_ IS a basic numeric data type
          UpdateSuccess(key)
        } else 
          WrongTypeFoundError(key)
        }
      case Some(sensormap: SensorMap) => {
        if( !after.isEmpty )
          sensormap.updateSensor(after.mkString("/"), newsensor) match {
            case s: UpdateSuccess => UpdateSuccess(key + "/" + s.pathTo)
            case w: WrongTypeFoundError => WrongTypeFoundError(key + "/" + w.pathTo)
            case p: PathNotFoundError => PathNotFoundError(key + "/" + p.pathTo)
          }
        else
          WrongTypeFoundError(key)
      }
      case _ => PathNotFoundError(key)
    }
  }

  private def insertSensor(pathTo: String, newsensor: SensorData): SDSMsg= {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => {
        if(after.isEmpty)
          SensorAllreadyExistError(key)
        else
          WrongTypeFoundError(key)
      }
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.insertSensor(after.mkString("/"), newsensor) match {
            case s: SensorInsertionSuccess => SensorInsertionSuccess(key + "/" + s.pathTo)
            case e: PathNotFoundError => PathNotFoundError(key + "/" + e.pathTo)
            case w: WrongTypeFoundError => WrongTypeFoundError(key + "/" + w.pathTo)
          }
        else WrongTypeFoundError(key)
      }
      case _ =>{
       if(after.isEmpty){
         content.single.put(key, newsensor)
         SensorInsertionSuccess(key)
       } else
         PathNotFoundError(key)
      }
    }
  }
  
  private def insertSensorMap(pathTo: String, newsensormap: SensorMap): SDSMsg = {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => WrongTypeFoundError(key)
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.insertSensorMap(after.mkString("/"), newsensormap) match {
            case s: MapInsertionSuccess => MapInsertionSuccess(key + "/" + s.pathTo)
            case e: PathNotFoundError => PathNotFoundError(key + "/" + e.pathTo)
            case w: WrongTypeFoundError => WrongTypeFoundError(key + "/" + w.pathTo)
          }
        else {
          MapAllreadyExistError(key)
        }
      }
      case _ =>{
        if(after.isEmpty){
          content.single.put(key, newsensormap) 
          MapInsertionSuccess(key)
        } else { 
          PathNotFoundError(key)
        }
      }
    }
  }
  
  def removeSensor(pathTo: String): SDSMsg = {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => {
        if(after.isEmpty){
          content.single.remove(key)
          SensorRemoveSuccess(key)
        } else WrongTypeFoundError(key)
      }
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.removeSensor(after.mkString("/")) match {
            case r: SensorRemoveSuccess => SensorRemoveSuccess(key + "/" + r.pathTo)
            case e: PathNotFoundError => PathNotFoundError(key + "/" + e.pathTo)
            case w: WrongTypeFoundError => WrongTypeFoundError(key + "/" + w.pathTo)
          }
        else WrongTypeFoundError(key)
      }
      case _ => PathNotFoundError(key)
    }
  }
  
  def removeSensorMap(pathTo: String): SDSMsg = {
    val spl = pathTo.split("/")
    val key = if( spl.head == id) spl.tail.head else spl.head
    val after = spl.tail
    content.single.get(key) match {
      case Some(sensor: SensorData) => WrongTypeFoundError(key)
      case Some(sensormap: SensorMap) => {
        if (!after.isEmpty)
          sensormap.removeSensor(after.mkString("/")) match {
            case r: MapRemoveSuccess => MapRemoveSuccess(key + "/" + r.pathTo)
            case e: PathNotFoundError => PathNotFoundError(key + "/" + e.pathTo)
            case w: WrongTypeFoundError => WrongTypeFoundError(key + "/" + w.pathTo)
          }
        else{
          content.single.remove(key)
          MapRemoveSuccess(key)
        }
      }
      case _ => PathNotFoundError(key)
    }
  }
  
}

