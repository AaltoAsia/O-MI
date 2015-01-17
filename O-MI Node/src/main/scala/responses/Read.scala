package responses

import sensorDataStructure._
import parsing._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map


object Read {

  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param path The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
	def generateODFresponse(path: String): Option[Either[String,xml.Node]] = {
    var wasValue = false
    val npath = if(path.split("/").last == "value"){
      wasValue =true
      path.dropRight(5) 
    }else path
		SQLite.get(path) match {
			case Some(sensor: DBSensor) => {
        if (wasValue)
          return Some(Left(sensor.value))
        else
          return Some(Right(
          <InfoItem name={sensor.path.split("/").last}>
            <value dateTime={sensor.time.toString}>
              {sensor.value}
            </value>
          </InfoItem>
        ))
			}

			case Some(sensormap: DBObject) => {
				var resultChildren = Buffer[xml.Node]()

				for (item <- sensormap.childs) {
					item match {
						case sensor: DBSensor => {
							resultChildren += <InfoItem name={sensor.path.split("/").last}/>
						}

						case subobject: DBObject => {
							resultChildren += <Object><id>{subobject.path.split("/").last}</id></Object>
						}

					}
				}

        val mapId = sensormap.path.split("/").last
        val xmlReturn =
          if (mapId == "Objects") {
            <Objects>{resultChildren}</Objects>
          } else {
            resultChildren.prepend(<id>{mapId}</id>)
            <Object>{resultChildren}</Object>
          }

        return Some(Right(xmlReturn))
			}

			case None => return None
		}
	}

	def generateODFresponse(path: String, root: SensorMap): String = {
		root.get(path) match {
			case Some(sensor: SensorData) => {
				val id = path.split("/").last
				val xmlreturn = <InfoItem name={id}><value dateTime={sensor.dateTime}>{sensor.value}</value></InfoItem>
				return xmlreturn.toString
			}

			case Some(sensormap: SensorMap) => {
				
				val xmlreturn = <id>{sensormap.id}</id>
				return xmlreturn.toString
			}

			case None => return "No object or value found"
		}
	}

	def OMIReadGenerate(root: SensorMap, depth: Int, ODFnodes: List[ODFNode]): String = {	//parsing is done somewhere and the possible result sent here

 		ODFnodes.sortWith(_.path < _.path)
 		var xmlreturn = Buffer[String]()
 		var nextnodes = Buffer[ODFNode]()
 		var previousSpl = ODFnodes(0).path.stripPrefix("/").split("/").slice(0, depth)		//stripPrefix just a quick fix since parser returns with a starting /.

 		for (node <- ODFnodes) {
 			var spl = node.path.stripPrefix("/").split("/")

 			if(depth < spl.length) {
 				var thispath = spl.slice(0, depth).mkString("/")
 				var prevpath = previousSpl.slice(0, depth).mkString("/")

 				if(thispath != prevpath) {
 					
 					xmlreturn += "<Object>"
 					xmlreturn += generateODFresponse(prevpath, root)
 					xmlreturn += OMIReadGenerate(root, depth+1, nextnodes.toList)
 					xmlreturn += "</Object>"
 					nextnodes.clear

 				}

 				nextnodes += node

 			}

 			else {
 				xmlreturn += generateODFresponse(node.path, root)
 			}

 			previousSpl = spl

 		}

 		if(nextnodes.isEmpty == false) {	//because the loop uses previous elements, theres sometimes more left in the list
 			var thispath = nextnodes(0).path.stripPrefix("/").split("/").slice(0, depth).mkString("/")
 			xmlreturn += "<Object>"
 			xmlreturn += generateODFresponse(thispath, root)
 			xmlreturn += OMIReadGenerate(root, depth+1, nextnodes.toList)
 			xmlreturn += "</Object>"
 		}

 		return xmlreturn.mkString

	}

	def OMIReadResponse(root: SensorMap, depth: Int, ODFnodes: List[ODFNode]): String = {
		val OMIresponseStart = 
		"""
		<omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
				<omi:response>
 					<omi:result msgformat="odf">
 						<omi:return returnCode="200"></omi:return>
 						<omi:requestId>REQ654534</omi:requestId>
 						<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
 							<Objects>
 		"""

 		val OMIelements = OMIReadGenerate(root, depth, ODFnodes)

 		val OMIresponseEnd = 
 		"""
 							</Objects>
 						</omi:msg>
 					</omi:result>
 				</omi:response>
		</omi:omiEnvelope>
		"""

		return OMIresponseStart + OMIelements + OMIresponseEnd
	}

}

