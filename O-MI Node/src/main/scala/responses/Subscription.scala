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

	//basically the same as Read response except it also contains a requestId
	def OMISubscriptionResponse(id: Int): xml.NodeSeq = {
		val subdata = SQLite.getSub(id).get

    omiResult{
      returnCode200 ++
      requestId(id) ++
      odfMsgWrapper(odfGeneration(subdata.paths))
    }
	}

	def odfDataGeneration(itempaths: Array[Path]) : xml.NodeSeq = {
    	var node : xml.NodeSeq = xml.NodeSeq.Empty 
    	for(path <- itempaths){
      		node ++=
        {
            val itemtype = SQLite.get(path)
            itemtype match{
              case Some( sensor : database.DBSensor) => {
              	<InfoItem name={sensor.path.last}>
              	<value dateTime={sensor.time.toString.replace(' ', 'T')}>{sensor.value}</value>
              	</InfoItem>
          		}

              case Some( obj : database.DBObject) => {
              	<Object><id>{ obj.path.last }</id></Object>
              	}

              case _ => <Error> Item not found in the database </Error>
            }
        }
    }

    node
  	}

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
      odfMsgWrapper(odfNoCallbackDataGeneration(subdata.paths, subdata.startTime, subdata.interval))
    }
	}

	def odfNoCallbackDataGeneration(itempaths: Array[Path], starttime:Timestamp, interval:Int) : xml.NodeSeq = {
    	var node : xml.NodeSeq = xml.NodeSeq.Empty

    	if(itempaths.isEmpty == false) {
      		node ++=
        {
            SQLite.get(itempaths.head) match{
              case Some(sensor: database.DBSensor) => {
              	<InfoItem name={sensor.path.last}>
              	{getAllvalues(sensor, starttime, interval)}
              	</InfoItem>
              	//{odfNoCallbackDataGeneration(itempaths.tail, starttime, interval)} //TODO: make recursion continue in this
              }

              case Some(obj : database.DBObject) => {
              	<Object><id>{ obj.path.last }</id>
              	{odfNoCallbackDataGeneration(itempaths.tail, starttime, interval)}
              	</Object>
              	}

              case _ => <Error> Item not found in the database </Error>
            }
        }

    	}

    node
  	}

  	def getAllvalues(sensor: database.DBSensor, starttime:Timestamp, interval:Double) : xml.NodeSeq = {
  		var node : xml.NodeSeq = xml.NodeSeq.Empty
  		val infoitemvaluelist = DataFormater.FormatSubData(sensor.path, starttime, interval)

  		for(innersensor <- infoitemvaluelist) {
  			node ++= <value dateTime={innersensor.time.toString.replace(' ', 'T')}>{innersensor.value}</value>
  		}

  		node
  	}


}
