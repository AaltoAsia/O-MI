package responses


import parsing.Types._
import parsing.Types.Path._
import database._
import Common._

import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import java.sql.Timestamp

object Read {



  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param path The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
	def generateODFREST(orgPath: Path): Option[Either[String, xml.Node]] = {

    // Removes "/value" from the end; Returns (normalizedPath, isValueQuery)
    def restNormalizePath(path: Path): (Path, Int) = path.lastOption match {
      case Some("value") => (path.init, 1) 
      case Some("MetaData") => (path.init, 2) 
      case _             => (path, 0)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val (path, wasValue) = restNormalizePath(orgPath)


		SQLite.get(path) match {
			case Some(sensor: DBSensor) =>
        if (wasValue == 1){
          return Some(Left(sensor.value))
        }else if (wasValue == 2){
          val metaData = SQLite.getMetaData(path)
          if(metaData.isEmpty)
            return Some(Right(omiResult(returnCode(404,s"No metadata found for $path")).head))
          else
            return Some(Left(metaData.get))

        }else{
          return Some(Right(
            <InfoItem name={ sensor.path.last }>
              <value dateTime={ sensor.time.toString.replace(' ', 'T') }>
                { sensor.value }
              </value>
            </InfoItem>))
        }
      case Some(sensormap: DBObject) =>
        var resultChildren = Buffer[xml.Node]()

        for (item <- sensormap.childs) {
          SQLite.get(item.path) match {
            case Some(sensor: DBSensor) => {
              resultChildren += <InfoItem name={ sensor.path.last }/>
            }

            case Some(subobject: DBObject) => {
              resultChildren += <Object><id>{ subobject.path.last }</id></Object>
            }

            case None => return None
          }
        }

        resultChildren = resultChildren.sortBy(_.mkString) //InfoItems are meant to come first

        val mapId = sensormap.path.lastOption.getOrElse("")
        val xmlReturn =
          if (mapId == "Objects") {
            <Objects>{ resultChildren }</Objects>
          } else {
            resultChildren.prepend(<id>{ mapId }</id>)
            <Object>{ resultChildren }</Object>
          }

        return Some(Right(xmlReturn))

      case None => return None
    }
  }

  //takes the return value of OmiParser straight
  def OMIReadResponse(read: OneTimeRead): xml.NodeSeq = {
    if (read.requestId.isEmpty) {
    omiOdfResult(
        returnCode200 ++
        requestIds(read.requestId) ++
        odfMsgWrapper(odfGeneration(read))
        )
    }

    else {
      val id = read.requestId.head.toInt
      OMISubscription.OMISubscriptionResponse(id)
      
    }
  }
  /**
   * helper function for generating whold O-DF xml.
   * @param nodes in Objects node to be generated
   * @return generated O-DF xml as String
   */
  def odfGeneration(read: OneTimeRead): xml.NodeSeq = {
      <Objects>
        { 
          odfObjectGeneration(
            read.sensors.toList,
            read.begin,
            read.end,
            read.newest,
            read.oldest)
        }
      </Objects>

  }

  /**
   * helper function for generating O-DF's Object nodes, recursive
   * @param nodes to generate
   * @return generated xml as String
   */
  def odfObjectGeneration(objects: List[OdfObject],
                          begin: Option[Timestamp],
                          end: Option[Timestamp],
                          newest: Option[Int],
                          oldest: Option[Int] ): xml.NodeSeq = {
    var node: xml.NodeSeq = xml.NodeSeq.Empty
    for (obj <- objects) {
      node ++=
      <Object>
        <id>{ obj.path.last }</id>
        {
          if (obj.childs.nonEmpty || obj.sensors.nonEmpty) {
              odfInfoItemGeneration(obj.sensors.toList, begin, end, newest, oldest ) ++ 
              odfObjectGeneration(obj.childs.toList, begin, end, newest, oldest )

          } else {
            //TODO: sqlite get begin to end
            SQLite.get(obj.path) match {
              case Some(infoItem: database.DBSensor) =>

              case Some(subobj: database.DBObject) =>

                val childs: Array[DBItem] = subobj.childs
                for (child <- childs) {

                  child match {
                    case infoItem: database.DBSensor =>
                      <InfoItem name={ infoItem.path.last }></InfoItem>
                    case subobj: database.DBObject =>
                      <Object><id>{ subobj.path.last }</id></Object>
                    case _ =>
                      <Error> Item not found or wrong type (InfoItem/Object) </Error>
                  }
                }
              case _ =>
                <Error> Item not found or wrong type (InfoItem/Object) </Error>
            }
          }
        }
      </Object>
    }
    node
  }
  /**
   * helper function for generating O-DF's InfoItem nodes. If any of the values begin, end, newest or oldest are defined, it uses them for generation.
   * otherwise it fetches just the present value of the sensor.
   * @param infoItems nodes to generate
   * @param begin the start time of the time interval from where to get sensors
   * @param end the end time of the time interval from where to get sensors
   * @param newest get only this many newest items
   * @param oldest get only this many oldest items
   * @return generated xml as String
   */
  def odfInfoItemGeneration(infoItems: List[OdfInfoItem],
                            begin: Option[Timestamp],
                            end: Option[Timestamp],
                            newest: Option[Int],
                            oldest: Option[Int] ): xml.NodeSeq = {

      var node: xml.NodeSeq = xml.NodeSeq.Empty
      if(begin != None || end != None || newest != None || oldest != None) {
        for (infoItem <- infoItems) {
          node ++= 
          <InfoItem name={ infoItem.path.last }>
            {
              //val sensors = SQLite.getNBetween(infoItem.path, begin, end, newest, oldest )
              // The parametres in database (fromStart, fromEnd)
              val sensors = SQLite.getNBetween(infoItem.path, begin, end, oldest, newest )
              if(sensors.nonEmpty){

                  var intervaldata : xml.NodeSeq = xml.NodeSeq.Empty 
                  for (sensor <- sensors) {
                    intervaldata ++= <value dateTime={ sensor.time.toString.replace(' ', 'T')}>{ sensor.value }</value>
                  }
                  val metaData = SQLite.getMetaData(infoItem.path)
                  if( metaData.isEmpty )
                    intervaldata
                  else
                    //TODO: make sure that this really works, combined NodeSeq and String
                    intervaldata ++ XML.loadString(metaData.get)
              }else{
                <Error> Item not found in the database </Error>
              }
            }
          </InfoItem>
        }
      } else {
        for (infoItem <- infoItems) {
          node ++= 
          <InfoItem name={ infoItem.path.last }>
            {
              SQLite.get(infoItem.path) match {
                case Some(sensor: DBSensor) => 
                val value = <value dateTime={ sensor.time.toString.replace(' ', 'T')}>{ sensor.value }</value>
                  val metaData = SQLite.getMetaData(infoItem.path)
                  if( metaData.isEmpty )
                    value
                  else
                    //TODO: make sure that this really works, combined NodeSeq and String
                    value ++ XML.loadString(metaData.get)
                case _ => <Error> Item not found in the database </Error>
              }

            }

          </InfoItem>
        }
      }
      
      node
  }

}
