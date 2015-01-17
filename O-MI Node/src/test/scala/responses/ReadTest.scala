package responses

import org.specs2._
import scala.io.Source
import responses._
import parsing._
import sensorDataStructure.{SensorMap,SensorData}
import parsing.OdfParser._

class ReadTest extends Specification {
	// Create our in-memory sensor database

	val sensormap: SensorMap = new SensorMap("")

	sensormap.set("Objects", new SensorMap("Objects"))
	sensormap.set("Objects/Refrigerator123", new SensorMap("Objects/Refrigerator123"))
	sensormap.set("Objects/RoomSensors1", new SensorMap("Objects/RoomSensors1"))

	//val date = new Date();
	//val formatDate = new SimpleDateFormat ("yyyy-MM-dd'T'hh:mm:ss");
	val testData = Map(
    	"Objects/Refrigerator123/PowerConsumption" -> "0.123",
    	"Objects/Refrigerator123/RefrigeratorDoorOpenWarning" -> "door closed",
    	"Objects/Refrigerator123/RefrigeratorProbeFault" -> "Nothing wrong with probe",
    	"Objects/RoomSensors1/Temperature" -> "21.2",
    	"Objects/RoomSensors1/CarbonDioxide" -> "too much"
    	)
 	for ((path, value) <- testData){
    	sensormap.set(path, new SensorData(path, value, "2014-12-186T15:34:52"))
  	}

	def is = s2"""
  	Testing for the read response.

  	Read.OMIReadResponse should return correct XML when given a list of values.

      Correct XML with one value       		$e1
      
    """

    //Correct XML with multiple values    	$e2
    //Error message when applicable			$e3

    def e1 = {
    	val testlist = List(
        ODFNode("/Objects/Refrigerator123/PowerConsumption", InfoItem, Some("0.123"), Some("dateTime=\"2014-12-186T15:34:52\""), None))
        println(OmiParser.parse(Read.OMIReadResponse(sensormap, 2, testlist)))
        OmiParser.parse(Read.OMIReadResponse(sensormap, 2, testlist)) == List(
            Result("", Some(testlist)))

    }
}