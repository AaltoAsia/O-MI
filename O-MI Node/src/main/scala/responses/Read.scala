package responses


import parsing.Types._
import parsing.Types.Path._
import database._
import scala.xml
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
    def restNormalizePath(path: Path): (Path, Boolean) = path.lastOption match {
      case Some("value") => (path.init, true) 
      case _             => (path, false)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val (path, wasValue) = restNormalizePath(orgPath)


		SQLite.get(path) match {
			case Some(sensor: DBSensor) =>
        if (wasValue)
          return Some(Left(sensor.value))
        else
          return Some(Right(
            <InfoItem name={ sensor.path.last }>
              <value dateTime={ sensor.time.toString.replace(' ', 'T') }>{ sensor.value }</value>
            </InfoItem>))

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

        val mapId = sensormap.path.last
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

  def OMIReadResponse(read: OneTimeRead): xml.Node = { //takes the return value of OmiParser straight
    val xml =
      <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
        <omi:response>
          <omi:result msgformat="odf">
            <omi:return returnCode="200"></omi:return>
            <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
              {
                odfGeneration(read)
              }
            </omi:msg>
          </omi:result>
        </omi:response>
      </omi:omiEnvelope>
    xml
  }

  /**
   * helper function for generating whold O-DF xml.
   * @param nodes in Objects node to be generated
   * @return generated O-DF xml as String
   */
  def odfGeneration(read: OneTimeRead): xml.NodeSeq = {
    <Objects>
      { odfObjectGeneration(read.sensors.toList, read.begin, read.end, read.newest, read.oldest) }
    </Objects>
  }

  /**
   * helper function for generating O-DF's Object nodes, recursive
   * @param nodes to generate
   * @return generated xml as String
   */
  def odfObjectGeneration(objects: List[OdfObject], begin: Option[Timestamp], end: Option[Timestamp], newest: Option[Int], oldest: Option[Int] ): xml.NodeSeq = {
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
                    case _ => <Error> Item not found or wrong type (InfoItem/Object) </Error>
                  }
                }
              case _ => <Error> Item not found or wrong type (InfoItem/Object) </Error>
            }
          }
        }
      </Object>
    }
    node
  }

  /** helper function for generating O-DF's InfoItem nodes
    * @param nodes to generate
    * @return generated xml as String
    */
  def odfInfoItemGeneration(infoItems: List[ OdfInfoItem]) : xml.NodeSeq = {
    var node : xml.NodeSeq = xml.NodeSeq.Empty 
    for(infoItem <- infoItems){
      node ++= <InfoItem name={infoItem.path.last}>
        {
            val item = SQLite.get(infoItem.path)
            item match{
              case Some( sensor : database.DBSensor) =>
              <value dateTime={sensor.time.toString.replace(' ', 'T')}>{sensor.value}</value>
              case Some( obj : database.DBObject) =>
                <Error> Wrong type of request: this item is an InfoItem, not an Object </Error>
              case _ => <Error> Item not found in the database </Error>
            }
        }
      </InfoItem>
    }
    node
  }

  /**
   * helper function for generating O-DF's InfoItem nodes
   * @param nodes to generate
   * @param the start time of the time interval from where to get sensors
   * @param the end time of the time interval from where to get sensors
   * @return generated xml as String
   */
  def odfInfoItemGeneration(infoItems: List[OdfInfoItem], begin: Option[Timestamp], end: Option[Timestamp], newest: Option[Int], oldest: Option[Int] ): xml.NodeSeq = {

    try {
      var node: xml.NodeSeq = xml.NodeSeq.Empty
      for (infoItem <- infoItems) {
        node ++= 
        <InfoItem name={ infoItem.path.last }>
          {
            val sensors = SQLite.getNBetween(infoItem.path, begin, end, newest, oldest )
            if(sensors.nonEmpty){
                var intervaldata : xml.NodeSeq = xml.NodeSeq.Empty 
                for (sensor <- sensors) {
                  intervaldata ++= <value dateTime={ sensor.time.toString.replace(' ', 'T')}>{ sensor.value }</value>
                }

                intervaldata
            }else{
              <Error> Item not found in the database </Error>
            }
          }
        </InfoItem>
      }
      node
    } catch {
      case e: IllegalArgumentException =>
        //println("invalid begin and/or end parameter; ignoring")
        odfInfoItemGeneration(infoItems)
    }
  }
}

/*        {
          if(infoItem.metadata != "") <MetaData>{infoItem.metadata}</MetaData>
        }*/
