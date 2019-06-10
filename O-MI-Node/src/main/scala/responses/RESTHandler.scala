
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

import akka.util.Timeout
import akka.stream.alpakka.xml._
import database._
import parsing.xmlGen.{defaultScope, scalaxb, xmlTypes}
import types._
import types.odf._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.NodeSeq
import scala.collection.SeqView

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
      case attr@Some("description") => RESTDescription(path.init)
      case attr@Some("id") => RESTObjId(path.init)
      case attr@Some("MetaData") => RESTMetaData(path.init)
      case attr@Some("name") => RESTInfoName(path.init)
      case Some(str) => 
        RESTNodeReq(path)
    }
  }

  /**
    * Generates ODF containing only children of the specified path's (with path as root)
    * or if path ends with "value" it returns only that value.
    *
    * @param orgPath The path as String, elements split by a slash "/"
    * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
    */
  def handle(orgPath: Path)(implicit singleStores: SingleStores, timeout: Timeout): Future[Either[String,SeqView[ParseEvent,Seq[_]]]] = {
    val labels = Set("value","name","id","description","MetaData")
    def createEvents(nodeOpt: Option[Node], label: Option[String]): Future[Either[String,SeqView[ParseEvent,Seq[_]]]] = 
      nodeOpt match{
        case Some(node: Node) =>
          toEvents(node,label)
        case None =>
          Future.successful(
            Right(
              Vector.empty.view
            )
          )
      }
    if(orgPath.toSeq.contains("MetaData") && !orgPath.lastOption.contains("MetaData")){
      val (path, metaPath) = orgPath.toSeq.splitAt(orgPath.toSeq.indexOf("MetaData"))
      val nodeOptF = singleStores.getSingle(path).map{
        nOption: Option[Node] => 
        nOption.collect{
          case ii: InfoItem => 
            ii.metaData.flatMap{ 
              md: MetaData => 
                md.infoItems.find{
                  mdii: InfoItem => 
                    metaPath.tail.headOption.contains(mdii.nameAttribute)
                }
            }
        }.flatten
      }
      val label = if( labels.contains(metaPath.lastOption.getOrElse(""))) 
        metaPath.lastOption
      else None
      nodeOptF.flatMap{
        nodeOpt: Option[Node] => 
          createEvents(nodeOpt, label)
      }
    }else{
      if( labels.contains(orgPath.lastOption.getOrElse(""))) {
        singleStores.getSingle(orgPath.init).flatMap{ 
          nodeOpt: Option[Node] =>
          createEvents(nodeOpt, orgPath.lastOption)
        }
      } else {
        singleStores.getSingle(orgPath).flatMap{ 
          nodeOpt: Option[Node] =>
          createEvents(nodeOpt, None)
        }
      }
    }
    
  }

  def toEvents(node: Node, member: Option[String])(implicit singleStores: SingleStores, timeout: Timeout): Future[Either[String,SeqView[ParseEvent,Seq[_]]]] = node match{
    case ii: InfoItem => 
      member match{
        case Some("value") => 
          val value: Either[String,SeqView[ParseEvent,Seq[_]]] = ii.values.headOption.map{ 
            case odfvalue: Value[_] => 
              odfvalue.value match {
                case value: ODF =>
                  Right( Vector(StartDocument).view ++ value.asXMLEvents ++ Vector(EndDocument))
                case value: Any =>
                  Left(value.toString)
              }
          }.getOrElse(
            Right(
              Vector.empty.view
            )
          )
          Future.successful(value)
        case Some("MetaData") => 
          val events =  ii.metaData.map{
            case md: MetaData => 
              Vector(StartDocument, StartElement("MetaData")).view ++ md.infoItems.view.flatMap{
                ii =>
                Seq(
                  StartElement(
                    "InfoItem",
                    List(
                      Attribute("name",ii.nameAttribute),
                      ) ++ ii.typeAttribute.map{
                        str: String =>
                          Attribute("type",str)
                          }.toList ++ ii.attributes.map{
                            case (key: String, value: String) => Attribute(key,value)
                          }.toList
                          ),
                        EndElement("InfoItem")
                      )
              }++ Vector(EndElement("MetaData"), EndDocument)
          }.toSeq.flatten.view 

          Future.successful(Right(events))
        case Some("name") => 
          val events =  Vector(StartDocument, StartElement("InfoItem",List(Attribute("name",ii.nameAttribute)))).view ++ ii.names.toSeq.view.flatMap{
            case id: QlmID => id.asXMLEvents("name")
          } ++ Vector(EndElement("InfoItem"),EndDocument)
          Future.successful(Right(events))
        case Some("description") => 
          val events =  Vector(StartDocument, StartElement("InfoItem",List(Attribute("name",ii.nameAttribute)))).view ++ ii.descriptions.toSeq.view.flatMap{
            case desc: Description => desc.asXMLEvents
          } ++ Vector(EndElement("InfoItem"),EndDocument)
          Future.successful(Right(events))
        case None =>
          val events = Seq(
            StartDocument,
            StartElement(
              "InfoItem",
              List(
                Attribute("name",ii.nameAttribute),
              ) ++
              ii.typeAttribute.map{
                  str: String => Attribute("type",str)
              }.toList ++ 
              ii.attributes.map{
                      case (key: String, value: String) => Attribute(key,value)
              }.toList
            )
          ).view ++ 
          ii.names.view.flatMap{
            case id: QlmID => id.asXMLEvents("name")
          } ++ 
          ii.descriptions.headOption.map{
            case desc: Description =>
              Vector(StartElement("description"),EndElement("description"))
          }.toSeq.flatten ++ 
          ii.metaData.map{
            case meta: MetaData =>
              Vector(StartElement("MetaData"),EndElement("MetaData"))
          }.toSeq.flatten ++ ii.values.view.flatMap{
            case value: Value[_] =>
            value.asXMLEvents
          } ++ Seq(
            EndElement( "InfoItem"),
            EndDocument
          )
          Future.successful(Right(events))
        case Some(str) => 
          val events = Vector(
            StartDocument,
            StartElement("error"),
            Characters("No such element in InfoItem"),
            EndElement("error"),
            EndDocument
          ).view
          Future.successful(Right(events))
      }
    case obj: Object => 
      member match{
        case Some("description") => 
          val events = Vector(StartDocument,
                StartElement(
                  "Object",
                  obj.typeAttribute.map{
                    str: String => Attribute("type",str)
                  }.toList ++ 
                  obj.attributes.map{
                    case (key: String, value: String) => Attribute(key,value)
                  }.toList
                )
            ).view ++ obj.descriptions.toSeq.view.flatMap{
              case desc: Description => desc.asXMLEvents
          } ++ Vector(EndElement("Object"),EndDocument)
          Future.successful(Right(events))
        case Some("id") => 
          val events = Vector(StartDocument,
                StartElement(
                  "Object",
                  obj.typeAttribute.map{
                    str: String => Attribute("type",str)
                  }.toList ++ 
                  obj.attributes.map{
                    case (key: String, value: String) => Attribute(key,value)
                  }.toList
                )
            ).view ++ obj.ids.view.flatMap{
              case id: QlmID => id.asXMLEvents("id")
            } ++ Vector(EndElement("Object"),EndDocument)
          Future.successful(Right(events))
        case None =>
          singleStores.getHierarchyTree().map{
            odf: ImmutableODF =>
              val events = Vector(
                StartDocument,
                StartElement(
                  "Object",
                  obj.typeAttribute.map{
                    str: String => Attribute("type",str)
                  }.toList ++ 
                  obj.attributes.map{
                    case (key: String, value: String) => Attribute(key,value)
                  }.toList
                )
              ).view ++ obj.ids.view.flatMap{
                id: QlmID => id.asXMLEvents("id")
                } ++ odf.getChilds(obj.path).flatMap{
                case subObj: Object =>
                  Vector(
                    StartElement(
                      "Object",
                      subObj.typeAttribute.map{
                        str: String => Attribute("type",str)
                      }.toList ++ 
                      subObj.attributes.map{
                        case (key: String, value: String) => Attribute(key,value)
                      }.toList
                    )
                  ).view ++ subObj.ids.flatMap{
                    id: QlmID =>
                      id.asXMLEvents("id")
                  } ++ Vector(
                    EndElement("Object" )
                  )
                case ii: InfoItem =>

                  Vector(
                    StartElement(
                      "InfoItem",
                      List(
                        Attribute("name",ii.nameAttribute),
                        ) ++
                      ii.typeAttribute.map{
                        str: String => Attribute("type",str)
                      }.toList ++ 
                      ii.attributes.map{
                        case (key: String, value: String) => Attribute(key,value)
                      }.toList
                    ),
                    EndElement("InfoItem" )
                  ).view  
              } ++ Vector(EndElement("Object" ), EndDocument)
              Right(events)
          }
        case Some(str) => 
          val events = Vector(
            StartDocument,
            StartElement("error"),
            Characters("No such element in Object"),
            EndElement("error"),
            EndDocument
          ).view
          Future.successful(Right(events))
      }
      case objs: Objects =>
          singleStores.getHierarchyTree().map{
            odf: ImmutableODF =>
              val events = Vector(
                StartDocument,
                StartElement(
                  "Objects",
                  objs.version.map{
                    ver: String =>
                      Attribute("version", ver)
                      }.toList ++ objs.attributes.map{
                        case (key: String, value: String) => 
                          Attribute(key,value)
                      }
                )
              ).view ++ 
              odf.getChilds(objs.path).flatMap{
                case subObj: Object =>
                  Vector(
                    StartElement(
                      "Object",
                      subObj.typeAttribute.map{
                        str: String => Attribute("type",str)
                      }.toList ++ 
                      subObj.attributes.map{
                        case (key: String, value: String) => Attribute(key,value)
                      }.toList
                    )
                  ).view ++ subObj.ids.flatMap{
                    id: QlmID =>
                      id.asXMLEvents("id")
                  } ++ Vector(
                    EndElement("Object" )
                  )
                case ii: InfoItem =>

                  Vector(
                    StartElement(
                      "InfoItem",
                      List(
                        Attribute("name",ii.nameAttribute),
                        ) ++
                      ii.typeAttribute.map{
                        str: String => Attribute("type",str)
                      }.toList ++ 
                      ii.attributes.map{
                        case (key: String, value: String) => Attribute(key,value)
                      }.toList
                    ),
                    EndElement("InfoItem" )
                  ).view  
              } ++ Vector( EndElement("Objects" ),EndDocument )
              Right(events)
          }
  
  }
}
