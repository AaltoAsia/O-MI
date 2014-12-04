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
object OmiParser extends Parser{
  def parse(xml_msg: String): Option[_]={
    val root = XML.loadString(xml_msg)
    if(root.label != "omi:omiEnvelope")
      throw new java.lang.RuntimeException("XML's root isn't omi:Envelope")
    val request = root.head
    request.label match{
      case "omi:write"  =>{
        (request\"@msgformat").text match{ 
          case "odf" => {
            Some(new SensorWrite(OdfParser.parse( new PrettyPrinter(80,2).format((request\"{omi}msg").head)))) 
          }
          case _ =>
            ???
        }        
      } 
      case "omi:read"  => ???
      case "omi:cancel"  => ??? 
      case "omi:response"  => ??? 
    }
    None
  }
}

case class SensorWrite(sensors: Seq[SensorData])
