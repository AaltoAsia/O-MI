package parsing

import sensorDataStructure._
import scala.xml._
/*
 <?xml version="1.0" encoding="UTF-8"?>
 <omi:omiEnvelope xmlns:omi="omi.xsd"
 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="-1">
 <omi:write msgformat="odf" targetType="device">
   <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
     <Objects>
       <Object>
         <id>SmartFridge22334411</id>
         <InfoItem
         name="FridgeTemperatureSetpoint"><value>3.5</value></InfoItem>
         <InfoItem name="FreezerTemperatureSetpoint"><value>-
         20.0</value></InfoItem>
       </Object>
     </Objects>
   </omi:msg>
 </omi:write>
 </omi:omiEnvelope>
 */
object  OdfParser extends Parser{
  def parse(xml_msg: String): Seq[SensorData] ={
    val root = XML.loadString(xml_msg) 
    (root\"Object").map(obj =>
       parseObject(obj,"")
    ).flatten 
  }  
  private def parseObject(obj: Node, currentPath: String): Seq[SensorData] = {
    (obj\"InfoItem").map(infoitem =>{
      val path: String = currentPath + "/" + ((obj\"id").headOption match{
        case Some(path_) => path_.text
        case None =>  throw new java.lang.RuntimeException("Broken odf format. No id.")
      }) + "/" +  (infoitem\"@name").text
      val value: String = (infoitem\"value").headOption match{
        case Some(value_) => value_.text
        case None => throw new java.lang.RuntimeException("No value element found in infoitem.")
      }
      val dateTime: String = (infoitem\"value"\"@dateTime").headOption match {
        case Some(value_) => value_.text
        case None => ""
      }
      val typeOfInfo: String = (infoitem\"value"\"@type").headOption match {
        case Some(value_) => value_.text
        case None => ""
      }
      new SensorData(path,value,dateTime) 
    }) ++
    (obj\"Object").map(subobj =>{
      parseObject(subobj, currentPath + "/" + ((obj\"id").headOption match{
        case Some(path_) => path_.text
        case None =>  throw new java.lang.RuntimeException("Broken odf format. No id.")
      }))
    }).flatten
  }
}

