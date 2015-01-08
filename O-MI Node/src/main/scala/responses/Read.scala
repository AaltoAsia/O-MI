package responses

import sensorDataStructure._
import parsing._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map


object Read {
	def generateODF(path: String, root: SensorMap): Option[xml.Node] = {
		root.get(path) match {
			case Some(sensor: SensorData) => {
        if (sensor.id == "value")
          return Some(xml.PCData(sensor.value))
        else
          return Some(
          <InfoItem name={sensor.id}>
            <value dateTime={sensor.dateTime}>
              {sensor.value}
            </value>
          </InfoItem>
        )
			}

			case Some(sensormap: SensorMap) => {
				var xmlreturn = Buffer[xml.Node]()

        val mapId = sensormap.id
        if (mapId != "Objects"){
          xmlreturn += <id>{mapId}</id>
        }

				for (item <- sensormap.content.single.values) {
					item match {
						case sensor: SensorData => {
							xmlreturn += <InfoItem name={sensor.id}/>
						}

						case subobject: SensorMap => {
							xmlreturn += <Object><id>{subobject.id}</id></Object>
						}

					}
				}

        // add if for objects
				return Some(<Object>{xmlreturn}</Object>)

			}

			case None => return None
		}
	}

	def generateODFresponse(path: String, root: SensorMap): Option[xml.Node] = {
		root.get(path) match {
			case Some(sensor: SensorData) => {
				return Some(<InfoItem name={sensor.id}><value dateTime={sensor.dateTime}>{sensor.value}</value></InfoItem>)
			}

			case Some(sensormap: SensorMap) => {
				
				return Some(<id>{sensormap.id}</id>)

			}

			case None => {
				return Some(<error>No object found</error>)
			}
		}
	}

	def OMIReadResponse(root: SensorMap, depth: Int, ODFnodes: List[ODFNode]): String = {	//parsing is done somewhere and the possible result sent here
	/*	val OMIresponseStart = <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
				<omi:response>
 					<omi:result msgformat="odf">
 						<omi:return returnCode="200"></omi:return>
 						<omi:requestId>REQ654534</omi:requestId>
 						<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">*/

 		//NOT READY, missing the OMI starting thing (above), also not sure what to do if user asks for an Object instead of a value (Infoitem)

 		//starting from depth 1, go through the nodes' paths from start to depth and divide those who have the same path start to their own groups
 		//and go through these groups recursively, adding depth until we are at the 'bottom'
 		//needs to be done like this because unlike in RESTful interface, read response has to return the whole hierarchy and multiple values, if asked.

 		val nodemap = Map[String, ListBuffer[ODFNode]]()
 		var xmlreturn = Buffer[String]()

 		for (node <- ODFnodes) {
 			val spl = node.path.split("/")

 			if(spl.length <= depth) {	//we're at the last part of the path, eg. it's a value the user wants
 				val odfxml = generateODFresponse(node.path, root)

 				odfxml match {
 					case Some(xmlstuff) => xmlreturn += xmlstuff.mkString
 					case None => ???
 				}

 			}

 			else {		// we're not at the bottom, and add it to a list of others who have the same starting path

 			val head = spl.slice(0, depth).mkString("/")
 			val mapping = nodemap.get(head)

 			mapping match {
 				case Some(nodelist) => nodelist += node
 				case None => {
 					nodemap += (head -> ListBuffer[ODFNode]())
 					nodemap(head) += node
 					}

 				}
 			}

 		}

 		for((key, value) <- nodemap) {		//key is Objects always first, then in the next recursion for example Objects/Refrigerator123
 			if(key == "Objects") xmlreturn += "<Objects>" + OMIReadResponse(root, depth+1, value.toList) + "</Objects>"
 			else {
 				val odfxml = generateODFresponse(key, root)

 				odfxml match {
 					case Some(xmlstuff) => {
 						xmlreturn += "<Object>"
 						xmlreturn += xmlstuff.mkString
 						xmlreturn += OMIReadResponse(root, depth+1, value.toList)
 						xmlreturn += "</Object>"
 					}

 					case None => ???
 				}

 			}

 		}

 		return xmlreturn.mkString

	}

}

