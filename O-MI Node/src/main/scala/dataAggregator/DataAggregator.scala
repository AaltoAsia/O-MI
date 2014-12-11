package dataAggregator
import akka.actor.Actor
import SensorDataStructure.SensorData
import akka.actor.ActorRef

class DataAggregator extends Actor {

  def receive = ???
  
  def send(omidata:scala.xml.Elem,target:ActorRef) ={
    target ! omidata
  }
}
/**
 * Class to convert sensor path and data to OM-I write request
 *
 */
object DataAggregator{
  def getWriteRequest(sensorData: SensorData[_]): scala.xml.Elem = {
    var writeRequest =
      <omi:omiEnvelope ttl="0" version="1.0">
        <omi:write msgformat="odf">
          <omi:msg>
            <Objects>
              <Object>
                <id>{ sensorData.path.split("/").init.last }</id>
                <InfoItem name={ sensorData.path.split("/").last }>
                  <value>{ sensorData.value }</value>
                </InfoItem>
              </Object>
            </Objects>
          </omi:msg>
        </omi:write>
      </omi:omiEnvelope>
    return writeRequest
  }
}







