package responses

import Common._
import parsing.Types._
import parsing.Types.Path._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import java.sql.Timestamp
import java.util.Date

object OMISubscription {
	/**
	 * Creates a subscription in the database and generates the immediate answer to a subscription
	 *
	 * @param subscription an object of Subscription class which contains information about the request
	 * @return A tuple with the first element containing the requestId and the second element 
	 			containing the immediate xml that's used for responding to a subscription request
	 **/

	def setSubscription(subscription: Subscription): (Int, xml.NodeSeq) = {	//returns requestID and the response
		var requestIdInt: Int = -1
		val xml =
      omiResult{
        returnCode200 ++
        requestId{

          val paths = getPaths(subscription.sensors.toList)
          val ttlInt = subscription.ttl.toInt
          val interval = subscription.interval.toInt
          val callback = subscription.callback

          requestIdInt = SQLite.saveSub(
            new DBSub(paths.toArray, ttlInt, interval, callback, Some(new Timestamp( new Date().getTime())))
            )

          requestIdInt
        }
      }

		return (requestIdInt, xml)
	}

  /**
   * Return the right order of the paths so hierarchy stays intact
   *
   * @param The objects a Subscription class has
   * @return A Buffer with paths in the right order
   **/

	def getPaths(objects: Iterable[OdfObject]): Buffer[Path] = {
		var paths = Buffer[Path]()
		for (obj <- objects) {
			paths += obj.path
			if (obj.childs.nonEmpty) {
				paths ++= getPaths(obj.childs.toList)
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

	/**
   * Subscription response on intervals.
   *
   * @param Id of the subscription
   * @return The response XML
   **/

	def OMISubscriptionResponse(id: Int): xml.NodeSeq = {
		val subdata = SQLite.getSub(id).get
    omiResult{
      returnCode200 ++
      requestId(id) ++
      odfMsgWrapper(odfGeneration(subdata.paths))
    }
	}

  /**
   * Used for generating the ODF data
   *
   * @param Array of the paths (in the right order, given by getPaths)
   * @return The ODF data XML
   **/

	def odfDataGeneration(itempaths: Array[Path]) : xml.NodeSeq = {
    	var node : xml.NodeSeq = xml.NodeSeq.Empty
      if(itempaths.isEmpty == false) { 
      		node ++=
        {
            val itemtype = SQLite.get(itempaths.head)
            itemtype match{
              case Some( sensor : database.DBSensor) => {
              	<InfoItem name={sensor.path.last}>
              	<value dateTime={sensor.time.toString.replace(' ', 'T')}>{sensor.value}</value>
              	</InfoItem> ++
                {odfDataGeneration(itempaths.tail)}
          		}

              case Some( obj : database.DBObject) => {
              	<Object><id>{ obj.path.last }</id>
                {odfDataGeneration(itempaths.tail)}
                </Object>
              	}

              case _ => <Error> Item not found in the database </Error>
            }
        }
      }

    node
  	}

  /**
   * Wraps ODF data in Objects
   **/

	def odfGeneration(subdata: Array[Path]): xml.NodeSeq = {
    <Objects>
      { odfDataGeneration(subdata) }
    </Objects>
  	}

  	def OMINoCallbackResponse(id: Int): xml.NodeSeq = {
		val subdata = SQLite.getSub(id).get

    omiResult{
      returnCode200 ++
      requestId(id) ++
      odfMsgWrapper(<Objects>{odfNoCallbackDataGeneration(subdata.paths, subdata.startTime, subdata.interval)}</Objects>)
    }
	}

  /**
   * Used for generating the ODF data when no callback is given. Same as normal sub except there are potentially more values in infoitems
   *
   * @param Array of the paths (in the right order, given by getPaths)
   * @return The ODF data XML
   **/

	def odfNoCallbackDataGeneration(itempaths: Array[Path], starttime:Timestamp, interval:Double) : xml.NodeSeq = {
    	var node : xml.NodeSeq = xml.NodeSeq.Empty

    	if(itempaths.isEmpty == false) {
      		node ++=
        {
            SQLite.get(itempaths.head) match{
              case Some(sensor: database.DBSensor) => {
              	<InfoItem name={sensor.path.last}>
              	{getAllvalues(sensor, starttime, interval)}
              	</InfoItem> ++
              	{odfNoCallbackDataGeneration(itempaths.tail, starttime, interval)}
              }

              case Some(obj : database.DBObject) => {
              	<Object><id>{obj.path.last}</id>
              	{odfNoCallbackDataGeneration(itempaths.tail, starttime, interval)}
              	</Object>
              	}

              case _ => <Error> Item not found in the database </Error>
            }
        }

    	}

    node
  	}

    /**
     * Function that gets all the infoitem values that sub with no callback has accumulated
    **/

  	def getAllvalues(sensor: database.DBSensor, starttime:Timestamp, interval:Double) : xml.NodeSeq = {
  		var node : xml.NodeSeq = xml.NodeSeq.Empty
  		val infoitemvaluelist = DataFormater.FormatSubData(sensor.path, starttime, interval)

  		for(innersensor <- infoitemvaluelist) {
  			node ++= <value dateTime={innersensor.time.toString.replace(' ', 'T')}>{innersensor.value}</value>
  		}

  		node
  	}


}
