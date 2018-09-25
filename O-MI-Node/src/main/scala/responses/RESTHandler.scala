
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

import akka.pattern.ask
import akka.util.Timeout
import database._
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import types._
import types.odf.{ImmutableODF, InfoItem, Node, Object, Objects, Value}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq

object RESTHandler {

  sealed trait RESTRequest {
    def path: Path
  } // path is OdfNode path
  case class RESTValue(path: Path) extends RESTRequest

  case class RESTMetaData(path: Path) extends RESTRequest

  case class RESTDescription(path: Path) extends RESTRequest

  case class RESTObjId(path: Path) extends RESTRequest

  case class RESTInfoName(path: Path) extends RESTRequest

  case class RESTNodeReq(path: Path) extends RESTRequest

  object RESTRequest {
    def apply(path: Path): RESTRequest = path.lastOption match {
      case attr@Some("value") => RESTValue(path.init)
      case attr@Some("MetaData") => RESTMetaData(path.init)
      case attr@Some("description") => RESTDescription(path.init)
      case attr@Some("id") => RESTObjId(path.init)
      case attr@Some("name") => RESTInfoName(path.init)
      case _ => RESTNodeReq(path)
    }
  }

  /**
    * Generates ODF containing only children of the specified path's (with path as root)
    * or if path ends with "value" it returns only that value.
    *
    * @param orgPath The path as String, elements split by a slash "/"
    * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
    */
  def handle(orgPath: Path)(implicit singleStores: SingleStores, timeout: Timeout): Future[Option[Either[String, xml.NodeSeq]]] = {
    handle(RESTRequest(orgPath))
  }

  /**
    * Generates ODF containing only children of the specified path's (with path as root)
    * or if path ends with "value" it returns only that value.
    *
    * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
    */
  def handle(request: RESTRequest)(implicit singleStores: SingleStores, timeout: Timeout): Future[Option[Either[String, xml.NodeSeq]]] = {
    request match {
      case RESTValue(path) =>
        singleStores.readValue(path)
          .map(_.map(value => Left(value.value.toString)))

      case RESTMetaData(path) =>
        singleStores.getMetaData(path).map(_.map { metaData =>
          Right(scalaxb.toXML[xmlTypes.MetaDataType](metaData.asMetaDataType,
            Some("odf"),
            Some("MetaData"),
            defaultScope))
        })
      case RESTObjId(path) => { //should this query return the id as plain text or inside Object node?
        singleStores.getSingle(path).map(node => node.map {
          case obj: Object =>
            scalaxb.toXML[xmlTypes.ObjectType](
              obj.asObjectType(Vector.empty, Vector.empty), Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from odf.Object</error>
            )
          case objs: Objects => <error>Id query not supported for root Objects</error>
          case infoItem: InfoItem => <error>Id query not supported for InfoItem</error>
          case _ => <error>Matched default case. The impossible happened?</error>
        }.map(Right(_)))
      }

      case RESTInfoName(path) =>
        Future.successful(Some(Right(<InfoItem xmlns="odf.xsd" name={path.last}>
          <name>
            {path.last}
          </name>
        </InfoItem>)))
      // TODO: support for multiple name
      case RESTDescription(path) =>
        singleStores.getHierarchyTree().map(_.get(path).flatMap {
          case o: Object => Some(o.descriptions map (_.asDescriptionType))
          case ii: InfoItem => Some(ii.descriptions map (_.asDescriptionType))
          case n: Node => None
        } map {
          descriptions: Set[xmlTypes.DescriptionType] =>
            descriptions.map {
              desc: xmlTypes.DescriptionType =>
                scalaxb.toXML[xmlTypes.DescriptionType](
                                                         desc, Some("odf"), Some("description"), defaultScope
                                                       )
            }.fold(xml.NodeSeq.Empty) {
              case (l: xml.NodeSeq, r: xml.NodeSeq) => l ++ r
            }
        } map (Right.apply _))

      case RESTNodeReq(path) => {
        singleStores.getSingle(path).flatMap {

          case Some(obj: Object) =>
            for {
              odf <- singleStores.getHierarchyTree()
              elems = odf.getChilds(obj.path).collect {
                case cobj: Object =>
                  cobj.copy(descriptions = Set.empty).asObjectType(Vector.empty, Vector.empty)
                case ii: InfoItem =>
                  ii.copy(descriptions = Set.empty, values = Vector.empty, metaData = None).asInfoItemType
              }
              objs = elems.collect { case cobj: xmlTypes.ObjectType => cobj }
              iis = elems.collect { case ii: xmlTypes.InfoItemType => ii }
              res: Option[Either[String, NodeSeq]] = Some(scalaxb.toXML[xmlTypes.ObjectType](
                obj.asObjectType(iis, objs), Some("odf"), Some("Object"), defaultScope
              ).headOption.getOrElse(
                <error>Could not create from odf.Object</error>
              )).map(Right(_))
            } yield res

          case Some(objs: Objects) =>
            for {
              odf <- singleStores.getHierarchyTree()
              childs: Seq[xmlTypes.ObjectType] = odf.getChilds(objs.path).collect {
                case obj: Object =>
                  obj.copy(descriptions = Set.empty).asObjectType(Vector.empty, Vector.empty)
              }
              xmlObjs = objs.asObjectsType(childs)
              xmlR: Option[Either[String, NodeSeq]] = Some(scalaxb.toXML[xmlTypes.ObjectsType](
                xmlObjs, Some("odf"), Some("Objects"), defaultScope
              ).headOption.getOrElse(
                <error>Could not create from odf.Objects</error>
              )).map(Right(_))
            } yield xmlR
          case Some(infoitem: InfoItem) =>
            Future.successful(
              Some(scalaxb.toXML[xmlTypes.InfoItemType](
                infoitem.asInfoItemType, Some("odf"), Some("InfoItem"), defaultScope
              ).headOption.getOrElse(
                <error>Could not create from odf.InfoItem</error>
              )).map(Right(_))
            )
          case None => Future.successful(None)
          case other => Future.failed(new Exception(s"Invalid type found in rest response handler: $other"))
        }
      }
    }
  }
}
