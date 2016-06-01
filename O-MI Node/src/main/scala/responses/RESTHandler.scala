
package responses
import types._
import OdfTypes._
import parsing.xmlGen.{ xmlTypes, scalaxb, defaultScope }
import scala.xml.{ NodeSeq, XML }
import OdfTypes._
import database._

trait RESTHandler extends OmiRequestHandlerBase{
  private sealed trait ODFRequest {def path: Path} // path is OdfNode path
  private case class Value(path: Path)      extends ODFRequest
  private case class MetaData(path: Path)   extends ODFRequest
  private case class Description(path: Path)extends ODFRequest
  private case class ObjId(path: Path)      extends ODFRequest
  private case class InfoName(path: Path)   extends ODFRequest
  private case class NodeReq(path: Path)    extends ODFRequest

  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def generateODFREST(orgPath: Path): Option[Either[String, xml.Node]] = {
    def getODFRequest(path: Path): ODFRequest = path.lastOption match {
      case attr @ Some("value")      => Value(path.init)
      case attr @ Some("MetaData")   => MetaData(path.init)
      case attr @ Some("description")=> Description(path.init)
      case attr @ Some("id")         => ObjId(path.init)
      case attr @ Some("name")       => InfoName(path.init)
      case _                         => NodeReq(path)
    }
    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val request = getODFRequest(orgPath)
    request match {
      case Value(path) =>
        SingleStores.latestStore execute LookupSensorData(path) map { Left apply _.value }

      case MetaData(path) =>
        SingleStores.getMetaData(path) map { metaData =>
          Right(XML.loadString(metaData.data))
        }
      case ObjId(path) =>{  //should this query return the id as plain text or inside Object node?
        val xmlReturn = SingleStores.getSingle(path).map{
          case odfObj: OdfObject =>
            scalaxb.toXML[xmlTypes.ObjectType](
              odfObj.copy(infoItems = OdfTreeCollection(),objects = OdfTreeCollection(), description = None)
              .asObjectType, Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObject </error>
            )
          case odfObjs: OdfObjects => <error>Id query not supported for root Object</error>
          case odfInfoItem: OdfInfoItem => <error>Id query not supported for InfoItem</error>
          case _ => <error>Matched default case. The impossible happened?</error>
        }

        xmlReturn map Right.apply
        //Some(Right(<Object xmlns="odf.xsd"><id>{path.last}</id></Object>)) // TODO: support for multiple id
      }
      case InfoName(path) =>
        Some(Right(<InfoItem xmlns="odf.xsd" name={path.last}><name>{path.last}</name></InfoItem>))
        // TODO: support for multiple name
      case Description(path) =>
        SingleStores.hierarchyStore execute GetTree() get path flatMap (
          _.description map (_.value)
        ) map Left.apply _
        
      case NodeReq(path) =>
        val xmlReturn = SingleStores.getSingle(path) map {

          case odfObj: OdfObject =>
            scalaxb.toXML[xmlTypes.ObjectType](
              odfObj.asObjectType, Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObject </error>
            )

          case odfObj: OdfObjects =>
            scalaxb.toXML[xmlTypes.ObjectsType](
              odfObj.asObjectsType, Some("odf"), Some("Objects"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObjects </error>
            )

          case infoitem: OdfInfoItem =>
            scalaxb.toXML[xmlTypes.InfoItemType](
              infoitem.asInfoItemType, Some("odf"), Some("InfoItem"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfInfoItem</error>
            )
          case _ => <error>Matched default case. The impossible happened?</error>
        }

        xmlReturn map Right.apply
    }
  }
}
