
/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/

package responses
import scala.xml.XML

import database._
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import types.odf.{Object, InfoItem, Node, Objects}
import types._
import http.ActorSystemContext


object RESTHandler{

sealed trait RESTRequest{def path: Path} // path is OdfNode path
case class Value(path: Path)      extends RESTRequest
case class MetaData(path: Path)   extends RESTRequest
case class Description(path: Path)extends RESTRequest
case class ObjId(path: Path)      extends RESTRequest
case class InfoName(path: Path)   extends RESTRequest
case class NodeReq(path: Path)    extends RESTRequest

object RESTRequest{
    def apply(path: Path): RESTRequest = path.lastOption match {
      case attr @ Some("value")      => Value(path.init)
      case attr @ Some("MetaData")   => MetaData(path.init)
      case attr @ Some("description")=> Description(path.init)
      case attr @ Some("id")         => ObjId(path.init)
      case attr @ Some("name")       => InfoName(path.init)
      case _                         => NodeReq(path)
    }
}
  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def handle(orgPath: Path)(implicit singleStores: SingleStores): Option[Either[String, xml.NodeSeq]] = {
    handle( RESTRequest(orgPath) )
  }
  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def handle(request: RESTRequest)(implicit singleStores: SingleStores): Option[Either[String, xml.NodeSeq]] = {
    request match {
      case Value(path) =>
        singleStores.latestStore execute LookupSensorData(path) map { Left apply _.value.toString }

      case MetaData(path) =>
        singleStores.getMetaData(path) map { metaData =>
          Right(scalaxb.toXML[xmlTypes.MetaDataType](metaData.asMetaDataType, Some("odf"), Some("MetaData"),defaultScope))
        }
      case ObjId(path) =>{  //should this query return the id as plain text or inside Object node?
        val xmlReturn = singleStores.getSingle(path).map{
          case obj: Object =>
            scalaxb.toXML[xmlTypes.ObjectType](
              obj.asObjectType(Vector.empty,Vector.empty), Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from odf.Object </error>
            )
          case objs: Objects => <error>Id query not supported for root Objects</error>
          case infoItem: InfoItem => <error>Id query not supported for InfoItem</error>
          case _ => <error>Matched default case. The impossible happened?</error>
        }

        xmlReturn map Right.apply
        //Some(Right(<Object xmlns="odf.xsd"><id>{path.last}</id></Object>)) // TODO: support for multiple id
      }
      case InfoName(path) =>
        Some(Right(<InfoItem xmlns="odf.xsd" name={path.last}><name>{path.last}</name></InfoItem>))
        // TODO: support for multiple name
      case Description(path) =>
        singleStores.hierarchyStore execute GetTree() get path flatMap {
          case o: Object => Some(o.descriptions map(_.asDescriptionType))
          case ii: InfoItem => Some(ii.descriptions map (_.asDescriptionType))
          case n: Node => None
        } map {
          case descriptions: Set[xmlTypes.DescriptionType] =>
            descriptions.map{ 
              case desc: xmlTypes.DescriptionType =>
                scalaxb.toXML[xmlTypes.DescriptionType](
                  desc, Some("odf"), Some("description"), defaultScope
                )
            }.fold(xml.NodeSeq.Empty){
              case (l:xml.NodeSeq, r:xml.NodeSeq) => l ++ r
            }
        } map( Right.apply _ )
        
      case NodeReq(path) =>
        val xmlReturn = singleStores.getSingle(path) map {

          case obj: Object =>
            val (objs: Seq[xmlTypes.ObjectType],iis: Seq[xmlTypes.InfoItemType]) = (singleStores.hierarchyStore execute GetTree() ).getChilds( obj.path).collect{
              case cobj: Object => 
                cobj.copy(descriptions = Set.empty).asObjectType( Vector.empty, Vector.empty )
              case ii: InfoItem =>
                ii.copy(descriptions = Set.empty, values= Vector.empty, metaData = None ).asInfoItemType
            }.partition{
              case cobj: xmlTypes.ObjectType => true
              case ii: xmlTypes.InfoItemType => false
            }
            scalaxb.toXML[xmlTypes.ObjectType](
              obj.asObjectType(iis,objs), Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from odf.Object </error>
            )

          case objs: Objects =>
            val childs: Seq[xmlTypes.ObjectType] = (singleStores.hierarchyStore execute GetTree()).getChilds( objs.path).collect{
              case obj: Object => 
                obj.copy(descriptions = Set.empty).asObjectType( Vector.empty, Vector.empty )
            }
            val xmlObjs =objs.asObjectsType(childs)
            val xmlR = scalaxb.toXML[xmlTypes.ObjectsType](
              xmlObjs, Some("odf"), Some("Objects"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from odf.Objects </error>
            )
            //println("\n\n"+objs.toString)
            //println("\n\n"+xmlObjs.toString)
            //println("\n\n"+xmlR.toString)
            xmlR

          case infoitem: InfoItem =>
            scalaxb.toXML[xmlTypes.InfoItemType](
              infoitem.asInfoItemType, Some("odf"), Some("InfoItem"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from odf.InfoItem</error>
            )
          case _ => <error>Matched default case. The impossible happened?</error>
        }

        xmlReturn map Right.apply
    }
  }
}
