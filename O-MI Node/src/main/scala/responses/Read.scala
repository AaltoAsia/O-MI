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




	def OMIReadResponse( depth: Int, ODFnodes: Seq[ODFNode]): String = {	//parsing is done somewhere and the possible result sent here
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
 				val odfxml = generateODFresponse(node.path)

 				odfxml match {
 					case Some(Right(xmlstuff)) => xmlreturn += xmlstuff.mkString
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
 			if(key == "Objects") xmlreturn += "<Objects>" + OMIReadResponse(depth+1, value.toList) + "</Objects>"
 			else {
 				val odfxml = generateODFresponse(key)

 				odfxml match {
 					case Some(Right(xmlstuff)) => {
 						xmlreturn += "<Object>"
 						xmlreturn += xmlstuff.mkString
 						xmlreturn += OMIReadResponse(depth+1, value.toList)
 						xmlreturn += "</Object>"
 					}

 					case None => ???
 				}

 			}

 		}

 		return xmlreturn.mkString

	}

}

