package responses

import parsing._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map


object Read {

  def normalizePath(path: String): String = 
    if (path.endsWith("/")) path.init
    else path

  private val valueLength = "/value".length

  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param path The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
	def generateODFREST(path: String): Option[Either[String,xml.Node]] = {

    // Returns (normalizedPath, isValueQuery)
    def restNormalizePath(path: String): (String, Boolean) = {

      val npath = normalizePath(path)

      if (npath.split("/").last == "value") {
        val pathWithoutVal = npath.dropRight(valueLength)
        (pathWithoutVal, true) 
      } else
        (npath, false)
    }


    val (npath, wasValue) = restNormalizePath(path)


		SQLite.get(npath) match {
			case Some(sensor: DBSensor) =>
        if (wasValue)
          return Some(Left(sensor.value))
        else
          return Some(Right(
          <InfoItem name={sensor.path.split("/").last}>
            <value dateTime={sensor.time.toString}>{sensor.value}</value>
          </InfoItem>
        ))


			case Some(sensormap: DBObject) =>
				var resultChildren = Buffer[xml.Node]()

				for (item <- sensormap.childs) {
					SQLite.get(item.path) match {
						case Some(sensor: DBSensor) => {
							resultChildren += <InfoItem name={sensor.path.split("/").last}/>
						}

						case Some(subobject: DBObject) => {
							resultChildren +=
                <Object>
                  <id>{subobject.path.split("/").last}</id>
                </Object>
						}
            case None => return None
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

			case None => return None
		}
	}

	def generateODFresponse(path: String, begin : Option[java.sql.Timestamp], end : Option[java.sql.Timestamp]): String = {
		SQLite.get(path) match {
			case Some(sensor: DBSensor) => {
				val id = path.split("/").last
				val xmlreturn = <InfoItem name={id}><value dateTime={sensor.time.toString}>{sensor.value}</value></InfoItem>
				return xmlreturn.toString
			}

			case Some(sensormap: DBObject) => {
				val mapId = sensormap.path.split("/").last
				val xmlreturn = <id>{mapId}</id>
				return xmlreturn.toString
			}

			case None => return "No object or value found"
		}
	}
/*
	def OMIReadGenerate(depth: Int, ODFnodes: List[ODFNode]): String = {	//parsing is done somewhere and the possible result sent here

 		ODFnodes.sortWith(_.path < _.path)
 		var xmlreturn = Buffer[String]()
 		var nextnodes = Buffer[ODFNode]()
 		var previousSpl = ODFnodes(0).path.stripPrefix("/").split("/").slice(0, depth)		//stripPrefix just a quick fix since parser returns with a starting /.

 		for (node <- ODFnodes) {
 			var spl = node.path.stripPrefix("/").split("/")

 			if(depth < spl.length) {		//we're not at the depth the user is asking for

 				if(nextnodes.isEmpty == false) {
 					var lastnodepath = nextnodes.last.path.stripPrefix("/").split("/").slice(0, depth).mkString("/")
 					var thisnodepath = node.path.stripPrefix("/").split("/").slice(0, depth).mkString("/")

 					if(lastnodepath != thisnodepath) {
 						xmlreturn += "<Object>"
 						xmlreturn += generateODFresponse(lastnodepath, begin, end)
 						xmlreturn += OMIReadGenerate(depth+1, nextnodes.toList, begin, end)
 						xmlreturn += "</Object>"
 						nextnodes.clear
 					}
 				}

 				nextnodes += node

 			}

 			else {		//now we are at the depth and return the infoitem if its a value and the object and its childrens names if its an object
 				val xmltree = generateODFREST(node.path.stripPrefix("/"))
 				if(!xmltree.isEmpty) {
 					xmltree.get match {
 						case (eetteri: Either[String,xml.Node]) => {
 						eetteri match {
 							case Right(xmlstuff) => xmlreturn += xmlstuff.toString
              case _ =>  // nop
 						}
 					}

 					}
 				}
 				
 			}


 		}

 		if(nextnodes.isEmpty == false) {	//because the loop uses previous elements, theres sometimes more left in the list
 			var thispath = nextnodes(0).path.stripPrefix("/").split("/").slice(0, depth).mkString("/")
 			xmlreturn += "<Object>"
 			xmlreturn += generateODFresponse(thispath, begin, end)
 			xmlreturn += OMIReadGenerate(depth+1, nextnodes.toList, begin, end)
 			xmlreturn += "</Object>"
 		}

 		return xmlreturn.mkString

	}
  */

/*
	def OMIReadResponse(depth: Int, ODFnodes: List[ParseMsg]): String = {			//takes the return value of OmiParser straight
		val OMIresponseStart = 
		"""
		<omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
				<omi:response>
 					<omi:result msgformat="odf">
 						<omi:return returnCode="200"></omi:return>
 						<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
 							<Objects>
 		"""

 		var listofnodes = ODFnodes.collect {
            case OneTimeRead(_,_,_,c) => c
        }

        val nodelist = listofnodes.head

 		val OMIelements = OMIReadGenerate(depth, nodelist.toList, begin, end)

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
*/
	def OMIReadResponse(requests: List[ParseMsg]): String = {			//takes the return value of OmiParser straight
  val xml =
		<omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
				<omi:response>
 					<omi:result msgformat="odf">
 						<omi:return returnCode="200"></omi:return>
 						<omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            {
              var listofnodes = requests.collect {
                  case OneTimeRead( ttl: String,
                                  sensors: Seq[ OdfObject],
                                  begin: String,
                                  end: String,
                                  newest: String,
                                  oldest: String,
                                  callback: String,
                                  requstId: Seq[ String]
                                ) => sensors
                  }

                val OMIelements = odfGeneration(
                  listofnodes.flatMap(
                    node => node)
                ) 
              }
              </omi:msg>
            </omi:result>
          </omi:response>
      </omi:omiEnvelope>
      xml.toString
    }
    /** helper function for generating whold O-DF xml.
      * @param nodes in Objects node to be generated
      * @return generated O-DF xml as String
      */
    def odfGeneration(objects: List[ parsing.OdfObject]) : String = {
      (<Objects>{odfObjectGeneration(objects)}</Objects>).toString
    }

    /** helper function for generating O-DF's Object nodes, recursive
      * @param nodes to generate
      * @return generated xml as String
      */
    def odfObjectGeneration(objects: List[ parsing.OdfObject]) : String = {
    var node : xml.NodeSeq = xml.NodeSeq.Empty 
    for(obj <- objects){
      node ++=
        <Object>
          <id> {obj.path.last} </id>
          {
            if(obj.childs.nonEmpty || obj.sensors.nonEmpty) {
              odfObjectGeneration(obj.childs.toList)
              odfInfoItemGeneration(obj.sensors.toList)     
            } else {
              val childs : Array[ DBItem]  = SQLite.get( obj.path.mkString( "/") ) match {
                case Some(infoItem: database.DBSensor) =>
                  println("Found DBSensor instead of DBObject, when should not be possible.")
                  ???
                case Some(subobj: database.DBObject) =>
                  subobj.childs
                case None => 
                  println("DBObject not found, when should not be possible.")
                  ???
              }
              for( child  <- childs){
                child match{
                  case infoItem: database.DBSensor =>
                    <InfoItem name={infoItem.path.split("/").last}></InfoItem>
                  case subobj: database.DBObject =>
                    <Object><id>{subobj.path.split("/").last}</id></Object>
                }
              }
            }
          }
          <MetaData>{obj.metadata}</MetaData>
        </Object>
    }
    node.toString
  }

  /** helper function for generating O-DF's InfoItem nodes
    * @param nodes to generate
    * @return generated xml as String
    */
  def odfInfoItemGeneration(infoItems: List[ parsing.OdfInfoItem]) : String = {
    var node : xml.NodeSeq = xml.NodeSeq.Empty 
    for(infoItem <- infoItems){
      node ++= <InfoItem name={infoItem.path.last}>
        {
            val item = SQLite.get(infoItem.path.mkString("/"))
            item match{
              case Some( sensor : database.DBSensor) =>
              <value unixTime={sensor.time.toString}>{sensor.value}</value>
              case Some( obj : database.DBObject) =>
                println("WARN: Object found in InfoItem in DB!")
              case _ => println("unhandled case") //TODO Any better ideas?
            }
        }
        <MetaData>{infoItem.metadata}</MetaData>
      </InfoItem>
    }
    node.toString
  }
}

