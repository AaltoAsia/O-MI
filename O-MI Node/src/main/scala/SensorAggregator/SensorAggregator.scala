
/** Class to convert sensor path and data to OM-I write request
  * 
  * @tparam type of sensor data
  * @param Path to were sensor is. Last part is used as ID
  * @param Data value from the sensor
  * @return Returns the O-MI write request XML
  */
class SensorData[T](val path:String,val data:T) {
	val id = path.split("/").last
def getWriteRequest():scala.xml.Elem ={
	  var writeRequest =
	    <omi:omiEnvelope ttl="0" version="1.0">
			  <omi:write msgformat="odf">
			  <omi:msg>
	    <Objects>
			  <Object>
		  <id>{id}</id>
	  <Infoitem name="Sensor Data">
	  <value>{data}</value>
	  </Infoitem>
	  </Object>
</Objects>
</omi:msg>
</omi:write>
</omi:omiEnvelope>
	 return writeRequest
	}
}

