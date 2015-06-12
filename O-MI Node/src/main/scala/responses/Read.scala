package responses


import parsing.Types._
import parsing.Types.OmiTypes._
import parsing.Types.OdfTypes._
import database._
import Common._

import scala.xml._
import scala.collection.mutable.Buffer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Map

import java.sql.Timestamp
import scala.collection.JavaConversions.iterableAsScalaIterable



/**
 * Object that handles ODF generation for read messages
 */
object Read {



  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
	def generateODFREST(orgPath: Path)(implicit dbConnection: DB): Option[Either[String, xml.Node]] = {

    // Removes "/value" from the end; Returns (normalizedPath, isValueQuery)
    def restNormalizePath(path: Path): (Path, Int) = path.lastOption match {
      case Some("value") => (path.init, 1) 
      case Some("MetaData") => (path.init, 2) 
      case _             => (path, 0)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val (path, wasValue) = restNormalizePath(orgPath)


		dbConnection.get(path) match {
			case Some(sensor: DBSensor) =>
        if (wasValue == 1){
          return Some(Left(sensor.value))
        }else if (wasValue == 2){
          val metaData = dbConnection.getMetaData(path)
          if(metaData.isEmpty)
            return Some(Right(omiResult(returnCode(404,s"No metadata found for $path")).head))
          else
            return Some(Right(XML.loadString(metaData.get)))

        }else{
          return Some(Right(
            <InfoItem name={ sensor.path.last }>
              <value dateTime={ sensor.time.toString.replace(' ', 'T') }>
                { sensor.value }
              </value>
              {
                val metaData = dbConnection.getMetaData(path)
                if(metaData.nonEmpty)
                  <MetaData/>
              }
            </InfoItem>))
        }
      case Some(sensormap: DBObject) =>
        var resultChildren = Buffer[xml.Node]()

        for (item <- sensormap.childs) {
          dbConnection.get(item.path) match {
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

}

/**
 * Class that is used for generation of the read response messages
 */
class ReadResponseGen(implicit val dbConnection: DB) extends ResponseGen[ReadRequest] {

  /**
   * Method that generates a response message from OneTimeRead message
   * @param read OneTimeRead to generate response from
   * @return OMI-response for the read message
   */
  override def genMsg(read: ReadRequest): OmiOdfMsg = {
    OmiOdfMsg(
      <Objects>
        { 

          if(read.odf.objects.isEmpty) buildObjectChildren(dbConnection.getChilds(Path("Objects")), read.begin, read.end, read.newest, read.oldest)
          else {
          odfObjectGeneration(
            read.odf.objects.toList,
            read.begin,
            read.end,
            read.newest,
            read.oldest)
          }
        }
      </Objects>
    )

  }

  /**
   * helper function for generating O-DF's Object nodes, recursive
   * @param objects List of OdfObjects of the node
   * @param begin the start time of the time interval from where to get sensors
   * @param end the end time of the time interval from where to get sensors
   * @param newest get only this many newest items
   * @param oldest get only this many oldest items
   * @return generated xml
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
          //we can check it's just an object when it has no sensors or childs
          //we check if it has any children in the database, if not, it's probably an error
          if (obj.objects.isEmpty && obj.infoItems.isEmpty && dbConnection.getChilds(obj.path).nonEmpty) {
            buildObjectChildren(dbConnection.getChilds(obj.path), begin, end, newest, oldest)
          }
          else if (obj.objects.nonEmpty || obj.infoItems.nonEmpty) {
              odfInfoItemGeneration(obj.infoItems.toList, begin, end, newest, oldest ) ++ 
              odfObjectGeneration(obj.objects.toList, begin, end, newest, oldest )

          } else {
            //TODO: sqlite get begin to end
            dbConnection.get(obj.path) match {
              case Some(infoItem: DBSensor) =>

              case Some(subobj: DBObject) =>

                val childs: Array[DBItem] = subobj.childs
                for (child <- childs) {

                  child match {
                    case infoItem: DBSensor =>
                      <InfoItem name={ infoItem.path.last }></InfoItem>
                    case subobj: DBObject =>
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
   * @return generated xml as
   */
  def odfInfoItemGeneration(infoItems: List[OdfInfoItem],
                            begin: Option[Timestamp],
                            end: Option[Timestamp],
                            newest: Option[Int],
                            oldest: Option[Int] ): xml.NodeSeq = {

      var node: xml.NodeSeq = xml.NodeSeq.Empty
      for (infoItem <- infoItems) {
        node ++= getInfoItem(infoItem.path, begin, end, newest, oldest)
        }
      
      node
  }

  /**
   * Used when just an object is requested (returns the object and all its children as xml)
   * 
   * @param children Array of objects or infoitems (just their paths are used)
   * @param begin the start time of the time interval from where to get sensors
   * @param end the end time of the time interval from where to get sensors
   * @param newest get only this many newest items
   * @param oldest get only this many oldest items
   * @return generated xml as
   */

  def buildObjectChildren(children: Array[DBItem],
                            begin: Option[Timestamp],
                            end: Option[Timestamp],
                            newest: Option[Int],
                            oldest: Option[Int]): xml.NodeSeq = {

    var node: xml.NodeSeq = xml.NodeSeq.Empty

    if(children.isEmpty == false ) {
      //sort so infoitems come first (not PERFECTLY sure how this sorts..)
      for(child <- children.sortBy(_.path.toString)) {
        dbConnection.get(child.path) match {
          case Some(sensor: DBSensor) => {
            node ++= getInfoItem(sensor.path, begin, end, newest, oldest)
          }

          case Some(objekti: DBObject) => {
            node ++=
            <Object>
              <id>{ objekti.path.last }</id>
              { 
                buildObjectChildren(dbConnection.getChilds(objekti.path), begin, end, newest, oldest)
              }
            </Object>
          }

          case _ => //do nothing
        }
      }
    }

    node
  }

  /**
   * Helper function for infoitem generation
   * 
   * @param infoitempath The path of the infoitem
   * @param begin the start time of the time interval from where to get sensors
   * @param end the end time of the time interval from where to get sensors
   * @param newest get only this many newest items
   * @param oldest get only this many oldest items
   * @return generated xml
   */

  def getInfoItem(infoitempath: Path,
                    begin: Option[Timestamp],
                    end: Option[Timestamp],
                    newest: Option[Int],
                    oldest: Option[Int]): xml.NodeSeq = {

    var node: xml.NodeSeq = xml.NodeSeq.Empty
    var many = if(begin != None || end != None || newest != None || oldest != None) {true} else {false}
    var notfound = false

    node ++=
      <InfoItem name={infoitempath.last}>
      {
      var intervaldata : xml.NodeSeq = xml.NodeSeq.Empty
      // if one of begin, end, newest or oldest was defined
      if(many) {
          val sensors = dbConnection.getNBetween(infoitempath, begin, end, oldest, newest)
          if (sensors.isEmpty) {
            notfound = true
          } else {
            for (sensor <- sensors) {
              intervaldata ++= <value dateTime={ sensor.time.toString.replace(' ', 'T')}>{ sensor.value }</value>
            }
          }
      }

      // if none of the above were defined get just the latest value
      else {
        val sensor = dbConnection.get(infoitempath)
        sensor match {
          case Some(sensor: DBSensor) => {intervaldata ++= <value dateTime={ sensor.time.toString.replace(' ', 'T')}>{ sensor.value }</value>}
          case _ => {notfound = true}
        }
      }

      if(notfound) {intervaldata ++= <Error> Item not found in the database </Error>}

      val metaData = dbConnection.getMetaData(infoitempath)
      if( metaData.isEmpty )
        intervaldata
      else
        intervaldata //++ XML.loadString(metaData.get)


      }
      </InfoItem>

    node
  }

}
