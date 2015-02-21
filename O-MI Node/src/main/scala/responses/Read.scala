package responses

import parsing._
import database._
import scala.xml
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import java.sql.Timestamp

import parsing.Path._

object Read {



  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param path The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
	def generateODFREST(orgPath: Path): Option[Either[String, xml.Node]] = {

    // Returns (normalizedPath, isValueQuery)
    def restNormalizePath(path: Path): (Path, Boolean) = {
      if (!path.isEmpty && path.last == "value") (path.init, true) 
      else (path, false)
    }


    val (path, wasValue) = restNormalizePath(orgPath)


		SQLite.get(path) match {
			case Some(sensor: DBSensor) =>
        if (wasValue)
          return Some(Left(sensor.value))
        else
          return Some(Right(
            <InfoItem name={ sensor.path.last }>
              <value dateTime={ sensor.time.toString }>{ sensor.value }</value>
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
      { odfObjectGeneration(read.sensors.toList, read.begin, read.end) }
    </Objects>
  }

  /**
   * helper function for generating O-DF's Object nodes, recursive
   * @param nodes to generate
   * @return generated xml as String
   */
  def odfObjectGeneration(objects: List[parsing.OdfObject], begin: String, end: String): xml.NodeSeq = {
    var node: xml.NodeSeq = xml.NodeSeq.Empty
    for (obj <- objects) {
      node ++=
        <Object>
          <id>{ obj.path.last }</id>
          {
            if (obj.childs.nonEmpty || obj.sensors.nonEmpty) {
              odfInfoItemGeneration(obj.sensors.toList, begin, end) ++ 
              odfObjectGeneration(obj.childs.toList, begin, end)
            } else {
              //TODO: sqlite get begin to end
              val childs: Array[DBItem] = SQLite.get(obj.path) match {
                case Some(infoItem: database.DBSensor) =>
                  println("Found DBSensor instead of DBObject, when should not be possible.")
                  ???
                case Some(subobj: database.DBObject) =>
                  subobj.childs
                case None =>
                  println("DBObject not found, when should not be possible.")
                  ???
              }
              for (child <- childs) {
                child match {
                  case infoItem: database.DBSensor =>
                    <InfoItem name={ infoItem.path.last }></InfoItem>
                  case subobj: database.DBObject =>
                    <Object><id>{ subobj.path.last }</id></Object>
                }
              }
            }
          }
          {
            if(obj.metadata != "") <MetaData>{ obj.metadata }</MetaData>
          }
        </Object>
    }
    node
  }

  /** helper function for generating O-DF's InfoItem nodes
    * @param nodes to generate
    * @return generated xml as String
    */
  def odfInfoItemGeneration(infoItems: List[ parsing.OdfInfoItem]) : xml.NodeSeq = {
    var node : xml.NodeSeq = xml.NodeSeq.Empty 
    for(infoItem <- infoItems){
      node ++= <InfoItem name={infoItem.path.last}>
        {
            val item = SQLite.get(infoItem.path)
            item match{
              case Some( sensor : database.DBSensor) =>
              <value dateTime={sensor.time.toString.replace(' ', 'T')}>{sensor.value}</value>
              case Some( obj : database.DBObject) =>
                println("WARN: Object found in InfoItem in DB!")
              case _ => println("unhandled case") //TODO Any better ideas?
            }
        }
        {
          if(infoItem.metadata != "") <MetaData>{infoItem.metadata}</MetaData>
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
  def odfInfoItemGeneration(infoItems: List[parsing.OdfInfoItem], begin: String, end: String): xml.NodeSeq = {

    try {
      var beginTime = Timestamp.valueOf(begin.replace('T', ' '))
      var endTime = Timestamp.valueOf(end.replace('T', ' '))

      var node: xml.NodeSeq = xml.NodeSeq.Empty
      for (infoItem <- infoItems) {
        node ++= <InfoItem name={ infoItem.path.last }>
                   {
                     val items = SQLite.getInterval(infoItem.path, beginTime, endTime)
                     items match {
                       case sensors: Array[DBSensor] => {
                         var intervaldata : xml.NodeSeq = xml.NodeSeq.Empty 
                         for (sensor <- sensors) {
                           intervaldata ++= <value dateTime={ sensor.time.toString }>{ sensor.value }</value>
                         }

                         intervaldata
                       }
                       case _ =>
                         println("Error in interval read")
                     }
                   }
                   {
                    if(infoItem.metadata != "") <MetaData>{infoItem.metadata}</MetaData>
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
