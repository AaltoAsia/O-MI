package responses

import database._
import parsing.xmlGen._
import parsing.xmlGen.scalaxb._
import parsing.Types.Path
import xml.XML
import xml.Node

object DBConversions {
  def sensorToInfoItem(sensor: Array[DBSensor], metaData : Option[String] = None) : InfoItemType = {
    if(sensor.isEmpty)
      throw new RequestHandlingException(500,"Empty Array[Sensors] passed to generate InfoItems")

    if(sameSubTree(sensor.map{ sen => sen.path }))
      throw new RequestHandlingException(500,"Different root nodes in InfoItem generation")
    val name = sensor.head.path.last 
    InfoItemType(
      name = name,
      value = sensor.map{ sen : DBSensor =>
      ValueType(
        sen.value,
        "",
        unixTime = Some(sen.time.getTime/1000),
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
    if(obj.isEmpty)
      throw new RequestHandlingException(500,"Empty Array[Sensors] passed to generate Object")

    if(sameSubTree(obj.map{ sobj => sobj.path }))
      throw new RequestHandlingException(500,"Different root nodes in InfoItem generation")

    val id = obj.head.path.head 
    val sensors = obj.map{
      sobj =>
        DBSensor( Path(sobj.path).tail,  sobj.value, sobj.time )
    }.filter{ 
      sensor : DBSensor =>//InfoItems
        sensor.path.length == 1
    }.groupBy{
      sensor : DBSensor =>
        sensor.path.head
    }
    val subobjs = obj.map{
      sobj =>
        DBSensor( Path(sobj.path).tail,  sobj.value, sobj.time )
    }.filter{
      sensor : DBSensor => 
        sensor.path.length > 2
    }.groupBy{
      sobj : DBSensor => 
       sobj.path.head
    }

    ObjectType(
      Seq( QlmID(
          id,
          attributes = Map.empty
      )),
      InfoItem= sensors.map{
        case ( path :  String, sensor :Array[DBSensor] ) => 
          sensorToInfoItem(sensor)  
      }.toSeq,
      Object = subobjs.map{
        case ( path :  String, sensors :Array[DBSensor] ) => 
          sensorsToObject(sensors,rec_path)
      }.toSeq,
      attributes = Map.empty
    )
  }
  def sensorsToObjects( objects: Array[DBSensor] )(implicit dbConnection: DB) : ObjectsType ={
    if(objects.isEmpty)
      throw RequestHandlingException( 404, "No such items found.")
    
    //if( sameSubTree( objects.map{ sobj => sobj.path }))
    //  throw RequestHandlingException( 500,"Paths doens't start with Objects")

    val subobjs = objects.map{
      sobj : DBSensor =>//Get sensors without 'Objects' as first in path 
        DBSensor( sobj.path.tail,  sobj.value, sobj.time )
    }.groupBy{
      sobj : DBSensor =>//Group by first level
       sobj.path.head
    }
    ObjectsType(
      Object = subobjs.map{
        case ( path :  String, sensors :Array[DBSensor] ) => 
        sensorsToObject(sensors, Path("Objects") / path)
      }.toSeq
    )
  }
  private def sameSubTree(paths: Array[Path]) : Boolean = {
    val subTree = paths.head.head
    !paths.exists( path => path.head != subTree)
  }
}

case class RequestHandlingException(errorCode: Int, msg: String) extends Exception(msg)
