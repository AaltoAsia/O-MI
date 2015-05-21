package responses

import database._
import parsing.xmlGen._
import parsing.Types.Path
import xml.XML

object DBConversion {
  def sensorToInfoItem(sensor: Array[DBSensor], metaData : Option[String] = None) : InfoItemType = {
    val path = sensor.head.path
    if(sensor.contains{sen : DBSensor => sen.path != path})
      throw new Exception("Different paths in InfoItem generation")
    
    InfoItemType(
      name = path.last,
      value = sensor.map{ value : DBSensor =>
      ValueType(
        value.value,
        "",
        unixTime = Some(value.time.getTime/1000),
        attributes = Map.empty
      )
      },
      MetaData = 
        if(metaData.nonEmpty)
          Some( scalaxb.fromXML[MetaData]( XML.loadString( metaData.get ) ) )
        else 
          None
      ,
      attributes = Map.empty
    )
  }
  def sensorsToObject(obj: Array[DBSensor], rec_path: Path)(implicit dbConnection: DB) : ObjectType = {
    val path = obj.head.path
    if(obj.contains{sen : DBSensor => sen.path.head != path.head})
      throw new Exception("Different root nodes in InfoItem generation")
    val sensors = obj.filter{ 
      sensor : DBSensor =>
        sensor.path.tail.length == 1
    }.groupBy{
      sensor : DBSensor =>
        sensor.path
    }
    val subobjs = obj.filter{
      sensor : DBSensor => 
        sensor.path.length > 2
    }.map{
      sobj : DBSensor => 
        DBSensor( sobj.path.tail,  sobj.value, sobj.time )
    }.groupBy{
      sobj : DBSensor => 
       sobj.path.head
    }

    ObjectType(
      Seq( QlmID(
        path.head,
        attributes = Map.empty
      )),
    InfoItem= sensors.map{
      case ( path :  Path, sensor :Array[DBSensor] ) => 
      sensorToInfoItem(sensor,dbConnection.getMetaData(rec_path))  
    }.toSeq,
    Object = subobjs.map{
      case ( path :  String, sensors :Array[DBSensor] ) => 
      sensorsToObject(sensors,rec_path)
    }.toSeq,
      attributes = Map.empty
    )
  }
  def sensorsToObjects( objects: Array[DBSensor] )(implicit dbConnection: DB) : ObjectsType ={
    val path = objects.head.path
    if(objects.contains{sen : DBSensor => sen.path.head != "Objects"})
      throw new Exception("Different root nodes in InfoItem generation")
    val subobjs = objects.map{
      sobj : DBSensor => 
        DBSensor( sobj.path.tail,  sobj.value, sobj.time )
    }.groupBy{
      sobj : DBSensor => 
       sobj.path.head
    }
    ObjectsType(
      Object = subobjs.map{
        case ( path :  String, sensors :Array[DBSensor] ) => 
        sensorsToObject(sensors, Path("Objects") / path)
      }.toSeq
    )
  }
}
