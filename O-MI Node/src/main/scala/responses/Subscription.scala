package responses


import parsing.Types._
import parsing.Types.Path._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import java.sql.Timestamp

object OMISubscription {
	def setSubscription(subscription: Subscription): (String, xml.Node) = {	//returns requestID and the response
		var requestIdInt = -1
		val ttl = subscription.ttl
		val xml =
		<omi:omiEnvelope ttl={ttl} version="1.0" xsi:schemaLocation="omi.xsd omi.xsd" 
		 xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
        	<omi:response>
          		<omi:result msgformat="odf">
            		<omi:return returnCode="200"></omi:return>
						<omi:requestId>{

							val paths = getInfoItemPaths(subscription.sensors.toList)
							val ttlInt = subscription.ttl.toInt
							val interval = subscription.interval.toInt
							val callback = subscription.callback

							requestIdInt = SQLite.saveSub(
								new DBSub(paths.toArray, ttlInt, interval, callback)
								)

							requestIdInt.toString
						}</omi:requestId>
				</omi:result>
 			</omi:response>
		</omi:omiEnvelope>

		return (requestIdInt.toString, xml)
	}

	def getInfoItemPaths(objects: List[OdfObject]): Buffer[Path] = {
		var paths = Buffer[Path]()
		for (obj <- objects) {
			if (obj.childs.nonEmpty) {
				paths ++= getInfoItemPaths(obj.childs.toList)
			}

			if (obj.sensors.nonEmpty) {
				var infoitems = obj.sensors.collect {
					case infoitem: OdfInfoItem => infoitem.path
				}

				paths ++= infoitems.toBuffer
			}
		}

		return paths

	}
}