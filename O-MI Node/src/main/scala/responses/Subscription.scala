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

	def setSubscription(subscription: Subscription): (Int, xml.NodeSeq) = {
		var requestIdInt: Int = -1
    val paths = getInfoItemPaths(subscription.sensors.toList)

    if(paths.isEmpty == false) {
		  val xml =
      omiResult{
        returnCode200 ++
        requestId{

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

    else {
      val xml = 
      omiResult{
        returnCode(400, "No InfoItems found in the paths")
      }

      return (requestIdInt, xml)
    }
	}

  /**
   * Used for getting only the infoitems from the request (objects can't be subscribed to (?))
   *
   * @param A hierarchy of the ODF-structure that the parser creates
   * @return Paths of the infoitems
   **/

    def getInfoItemPaths(objects: List[OdfObject]): Buffer[Path] = {
    var paths = Buffer[Path]()
    for (obj <- objects) {
      if (obj.childs.nonEmpty) {
        paths ++= getInfoItemPaths(obj.childs.toList)
      }

      if (obj.sensors.nonEmpty) {
        for (sensor <- obj.sensors) {
          SQLite.get(sensor.path) match {
            case Some(infoitem: DBSensor) => paths += infoitem.path
            case _ => //do nothing
            }
          }
        }
      }
    return paths
  }

	/**
   * Subscription response
   *
   * @param Id of the subscription
   * @return The response XML
   **/

	def OMISubscriptionResponse(id: Int): xml.NodeSeq = {
    SQLite.getSub(id) match {
    case None => {
      omiResult{
      returnCode(400, "A subscription with this id has expired or doesn't exist") ++
      requestId(id)
      }
    }

    case _ => {
      omiResult{
      returnCode200 ++
      requestId(id) ++
      odfMsgWrapper(odfGeneration(id))
      }
    }
  }
	}

  /**
   * Used for generating data in ODF-format. When the subscription has callback set it acts like a onetimeread with a requestID,
   * when it doesn't have a callback it generates the values accumulated in the database.
   *
   * @param Id of the subscription
   * @return The data in ODF-format
   **/

	def odfGeneration(id: Int): xml.NodeSeq = {
    val subdata = SQLite.getSub(id).get
    subdata.callback match {
      case Some(callback: String) => {
        <Objects>
        {createFromPaths(subdata.paths, 1)}
        </Objects>
      }

      case None => {
        <Objects>
        {createFromPathsNoCallback(subdata.paths, 1, subdata.startTime, subdata.interval)}
        </Objects>
      }
    }
  }

  /**
   * Uses the Dataformater from database package to get a list of the values that have been accumulated during the start of the sub and the request
   * 
   * @param The InfoItem that's been subscribed to
   * @param Start time of the subscription
   * @param Interval of the subscription
   * @return The values accumulated in ODF format
   **/

  	def getAllvalues(sensor: database.DBSensor, starttime:Timestamp, interval:Double) : xml.NodeSeq = {
  		var node : xml.NodeSeq = xml.NodeSeq.Empty
  		val infoitemvaluelist = DataFormater.FormatSubData(sensor.path, starttime, interval,None)

  		for(innersensor <- infoitemvaluelist) {
  			node ++= <value dateTime={innersensor.time.toString.replace(' ', 'T')}>{innersensor.value}</value>
  		}

  		node
  	}

  /**
   * Creates the right hierarchy from the infoitems that have been subscribed to.
   *
   * @param The paths of the infoitems that have been subscribed to
   * @param Index of the current 'level'. Used because it recursively drills deeper.
   * @return The ODF hierarchy as XML
   **/

    def createFromPaths(paths: Array[Path], index: Int): xml.NodeSeq = {
      var node : xml.NodeSeq = xml.NodeSeq.Empty

      if (paths.isEmpty == false)
        {
        var slices = Buffer[Path]()
        var previous = paths.head

        for(path <- paths) {
            var slicedpath = Path(path.toSeq.slice(0, index+1))
            SQLite.get(slicedpath) match {
              case Some(sensor: database.DBSensor) => {
                node ++= 
                <InfoItem name={sensor.path.last}>
                <value dateTime={sensor.time.toString.replace(' ', 'T')}>{sensor.value}</value>
                </InfoItem>
              }

              case Some(obj: database.DBObject) => {
                if (path(index) == previous(index)) {
                  slices += path
                }

                else {
                  node ++= <Object><id>{previous(index)}</id>{createFromPaths(slices.toArray, index+1)}</Object>
                  slices = Buffer[Path](path)
                }

              }

              case None => node ++= <Error> Item not found in the database </Error>
            }

            previous = path

            //in case this is the last item in the array, we check if there are any non processed paths left
            if (path == paths.last) {
              if (slices.isEmpty == false) {
                node ++= <Object><id>{slices.last.toSeq(index)}</id>{createFromPaths(slices.toArray, index+1)}</Object>
              }
            }
          }

        }
      
      return node
    }

  /**
   * Creates the right hierarchy from the infoitems that have been subscribed to and no callback is given (one infoitem may have many values)
   *
   * @param The paths of the infoitems that have been subscribed to
   * @param Index of the current 'level'. Used because it recursively drills deeper.
   * @param Start time of the subscription
   * @param Interval of the subscription
   * @return The ODF hierarchy as XML
   **/

    def createFromPathsNoCallback(paths: Array[Path], index: Int, starttime:Timestamp, interval:Double): xml.NodeSeq = {
      var node : xml.NodeSeq = xml.NodeSeq.Empty

      if (paths.isEmpty == false)
        {
        var slices = Buffer[Path]()
        var previous = paths.head

        for(path <- paths) {
            var slicedpath = Path(path.toSeq.slice(0, index+1))
            SQLite.get(slicedpath) match {
              case Some(sensor: database.DBSensor) => {
                node ++= 
                <InfoItem name={sensor.path.last}>
                {getAllvalues(sensor, starttime, interval)}
                </InfoItem>
              }

              case Some(obj: database.DBObject) => {
                if (path(index) == previous(index)) {
                  slices += path
                }

                else {
                  node ++= <Object><id>{previous(index)}</id>{createFromPathsNoCallback(slices.toArray, index+1, starttime, interval)}</Object>
                  slices = Buffer[Path](path)
                }

              }

              case None => node ++= <Error> Item not found in the database </Error>
            }

            previous = path

            if (path == paths.last) {
              if (slices.isEmpty == false) {
                node ++= <Object><id>{slices.last.toSeq(index)}</id>{createFromPathsNoCallback(slices.toArray, index+1, starttime, interval)}</Object>
              }
            }
          }

        }
      
      return node
    }

}
