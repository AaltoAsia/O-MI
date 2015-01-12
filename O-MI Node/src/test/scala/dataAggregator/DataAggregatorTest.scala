package dataAggregator
//import org.specs2._
import org.specs2.mutable._
import dataAggregator._

object DataAggregatorTest extends Specification {
  val xmlData = DataAggregator.getWriteRequest(new SensorDataStructure.SensorData("/objects/kitchen/oven/heat","Celcius","30","30.6.2014"))
  
  "Data Aggregator" should {
    
    "return correct id field inside an O-MI message" in {
       (xmlData \\ "id").head.text mustEqual "oven"
    }
    
    "return correct value field inside an O-MI message" in {
      (xmlData \\ "value").head.text mustEqual "30"
    }
    
    "return correct name field inside an O-MI message" in {
      xmlData \\ "InfoItem" \@ "name" mustEqual "heat"
    }
  }
}