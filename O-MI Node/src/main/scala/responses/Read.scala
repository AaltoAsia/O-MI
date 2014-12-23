package responses

import sensorDataStructure._
import scala.xml
import scala.collection.mutable.Buffer


object Read {
	def generateODF(path: String, root: SensorMap): Option[xml.Node] = {
		root.get(path) match {
			case Some(sensor: SensorData) => {
				return Some(sensor.xmlElem)
			}

			case Some(sensormap: SensorMap) => {
				var xmlreturn = Buffer[xml.Node]()
				xmlreturn += <id>{sensormap.id}</id>
				for(item <- sensormap.content.single.values) {
					item match {
						case sensor: SensorData => {
							xmlreturn += <InfoItem name={sensor.id}/>
						}

						case subobject: SensorMap => {
							xmlreturn += <Object><id>{subobject.id}</id></Object>
						}

					}
				}

				return Some(<Object>{xmlreturn}</Object>)

			}

			case None => {
				return None
			}
		}
	}

}

