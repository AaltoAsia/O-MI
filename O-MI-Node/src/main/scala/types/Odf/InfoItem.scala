package types
package odf

import java.sql.Timestamp
import scala.language.implicitConversions
import database.journal.PPersistentNode.NodeType.Ii
import database.journal.{PInfoItem, PPersistentNode}
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes.InfoItemType

import akka.stream.alpakka.xml._
import scala.collection.immutable.{HashMap, Map => IMap}

object InfoItem {
  sealed trait Parameters {
    def apply(): InfoItem
  }
  trait Builders {
    implicit def fromPath(path: Path) = new Parameters {
      def apply = InfoItem( path.last, path)
    }
    implicit def fromPathValues(t: (Path, Vector[Value[Any]])) = new Parameters {
      val (path, values) = t
      def apply =
        InfoItem( path.last, path, values=values)
    }
    implicit def fromPathValuesDesc(t: (Path,
                                 Vector[Value[Any]],
                                 Description)) = new Parameters {
      val (path, values, description) = t
      def apply = InfoItem( path.last, path, values=values, descriptions=Set(description))
    }
    implicit def fromPathValuesMeta(t: (Path,
                                 Vector[Value[Any]],
                                 MetaData)) = new Parameters {
      val (path, values, metaData) = t
      def apply = InfoItem( path.last, path, values=values, metaData=Some(metaData))
    }
    implicit def fromPathValuesDescMeta(t: (Path,
                                 Vector[Value[Any]],
                                 Description,
                                 MetaData)) = new Parameters {
      val (path, values, description, metaData) = t
      def apply = InfoItem( path.last, path, values=values, metaData=Some(metaData), descriptions=Set(description))
    }
  }

  def build(magnet: Parameters): InfoItem = magnet()


  def apply(path: Path, values: Vector[Value[Any]]): InfoItem = {
    InfoItem(
      path.last,
      path,
      values = values
    )
  }

  def apply(
             path: Path,
             typeAttribute: Option[String],
             names: Vector[QlmID],
             descriptions: Set[Description],
             values: Vector[Value[Any]],
             metaData: Option[MetaData],
             attributes: IMap[String, String]
           ): InfoItem = {
    InfoItem(
      path.last,
      path,
      typeAttribute,
      names,
      descriptions,
      values,
      metaData,
      attributes
    )
  }
}

case class InfoItem(
                     nameAttribute: String,
                     path: Path,
                     typeAttribute: Option[String] = None,
                     names: Vector[QlmID] = Vector.empty,
                     descriptions: Set[Description] = Set.empty,
                     values: Vector[Value[Any]] = Vector.empty,
                     metaData: Option[MetaData] = None,
                     attributes: IMap[String, String] = HashMap.empty
                   ) extends Node with Unionable[InfoItem] {
  assert(nameAttribute == path.last && path.length > 2)

  def updateValues(vals: Vector[Value[Any]]): InfoItem = this.copy(values = vals)

  def update(that: InfoItem): InfoItem = {
    val pathsMatches = path == that.path
    assert(nameAttribute == that.nameAttribute && pathsMatches)
    InfoItem(
      nameAttribute,
      path,
      that.typeAttribute.orElse(typeAttribute),
      QlmID.unionReduce(names ++ that.names).toVector.filter { id => id.id.nonEmpty },
      Description.unionReduce(descriptions ++ that.descriptions).toVector.filter(desc => desc.text.nonEmpty).toSet,
      if (that.values.nonEmpty) that.values else values,
      that.metaData.flatMap {
        md: MetaData =>
          metaData.map {
            current: MetaData =>
              current update md
          }.orElse(that.metaData)
      }.orElse(metaData),
      attributes ++ that.attributes
    )

  }

  /*
  def intersection( that: InfoItem ): InfoItem ={
    val typeMatches = typeAttribute.forall {
      typeStr: String =>
        that.typeAttribute.forall {
          otherTypeStr: String => typeStr == otherTypeStr
        }
    }
    val pathsMatches = path == that.path
    assert( nameAttribute == that.nameAttribute && pathsMatches && typeMatches )
    new InfoItem(
      nameAttribute,
      path,
      that.typeAttribute.orElse( typeAttribute),
      if( that.names.nonEmpty ){
        QlmID.unionReduce( that.names ++ names).toVector.filter{ id => id.id.nonEmpty}
      } else Vector.empty,
      if( that.descriptions.nonEmpty ){
        Description.unionReduce(that.descriptions ++ descriptions).toVector.filter(desc => desc.text.nonEmpty)
      } else Vector.empty,
      values,
      (metaData, that.metaData) match{
        case (Some( md ), Some( omd )) => Some( omd.union(md) )
        case ( Some(md), None) => Some( MetaData( Vector()))
        case (None, _) => None 
      },
      that.attributes ++ attributes 
    )
  }*/

  def union(that: InfoItem): InfoItem = {
    val pathsMatches = path == that.path
    assert(nameAttribute == that.nameAttribute && pathsMatches)
    new InfoItem(
      nameAttribute,
      path,
      optionAttributeUnion(this.typeAttribute, that.typeAttribute),
      QlmID.unionReduce(names ++ that.names).toVector,
      Description.unionReduce(descriptions ++ that.descriptions).toSet,
      values ++ that.values,
      (metaData, that.metaData) match {
        case (Some(md), Some(omd)) => Some(md.union(omd))
        case (md, omd) => optionUnion(md, omd)
      },
      attributeUnion(attributes, that.attributes)
    )
  }

  def createAncestors: Seq[Node] = {
    path.getAncestors.collect {
      case ancestorPath: Path if ancestorPath.nonEmpty =>
        if (ancestorPath == Path("Objects")) {
          Objects()
        } else {
          Object(
            Vector(
              new QlmID(
                ancestorPath.last
              )
            ),
            ancestorPath
          )
        }
    }.toVector
  }

  def createParent: Node = {
    val parentPath: Path = path.getParent
    if (parentPath == new Path("Objects") || parentPath.isEmpty) {
      Objects()
    } else {
      Object(
        Vector(
          new QlmID(
            parentPath.last
          )
        ),
        parentPath
      )
    }
  }

  def asInfoItemType: InfoItemType = {
    val nameTags = if (this.names.exists(id => id.id == nameAttribute) && this.names.length == 1) {
      this.names.filter {
        qlmid =>
          qlmid.idType.nonEmpty ||
            qlmid.tagType.nonEmpty ||
            qlmid.startDate.nonEmpty ||
            qlmid.endDate.nonEmpty ||
            qlmid.attributes.nonEmpty
      }
    } else if (!this.names.exists(id => id.id == nameAttribute) && this.names.nonEmpty) {
      this.names ++ Vector(QlmID(nameAttribute))
    } else {
      this.names
    }

    InfoItemType(
      nameTags.map {
        qlmid => qlmid.asQlmIDType
      },
      this.descriptions.toVector.map {
        des: Description =>
          des.asDescriptionType
      },
      this.metaData.map(_.asMetaDataType).toSeq,
      //Seq(QlmIDType(path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: $path")))),
      this.values.map {
        value: Value[Any] => value.asValueType
      },
      HashMap(
        "@name" -> DataRecord(
          nameAttribute
        )
      ) ++ attributesToDataRecord(this.attributes) ++ typeAttribute.map { ta => "@type" -> DataRecord(ta) }.toVector
    )
  }

  def hasStaticData: Boolean = {
    attributes.nonEmpty ||
      metaData.nonEmpty ||
      names.nonEmpty ||
      typeAttribute.nonEmpty ||
      descriptions.nonEmpty
  }

  def readTo(to: InfoItem): InfoItem = {
    val desc = if (to.descriptions.nonEmpty) {

      val languages = to.descriptions.flatMap(_.language)
      if (languages.nonEmpty) {
        descriptions.filter {
          case Description(text, Some(lang)) => languages.contains(lang)
          case Description(text, None) => true
        }
      } else {
        descriptions
      }
    } else if (this.descriptions.nonEmpty) {
      Vector(Description("", None))
    } else Vector.empty
    val mD = to.metaData match {
      case Some(md: MetaData) =>
        val names = md.infoItems.map(_.nameAttribute)
        if (names.nonEmpty) {
          this.metaData.map {
            md =>
              md.copy(md.infoItems.filter {
                ii: InfoItem =>
                  names.contains(ii.nameAttribute)
              })
          }
        } else this.metaData
      case None =>
        if (this.metaData.nonEmpty) Some(MetaData(Vector()))
        else None
    }
    //TODO: Filter names based on QlmID attributes

    to.copy(
      names = QlmID.unionReduce(names ++ to.names).toVector,
      typeAttribute = typeAttribute.orElse(to.typeAttribute),
      values = to.values ++ this.values,
      descriptions = desc.toSet,
      metaData = mD,
      attributes = attributes ++ to.attributes
    )
  }

  def persist: PPersistentNode.NodeType = Ii(PInfoItem(typeAttribute.getOrElse(""),
                                                       names.map(_.persist),
                                                       descriptions.map(_.persist()).toSeq,
                                                       metaData.map(_.persist()),
                                                       attributes))
  final def asXMLEvents: Seq[ParseEvent] = {
    Seq(
      StartElement(
        "InfoItem",
        List(
          Attribute("name",nameAttribute),
        ) ++ typeAttribute.map{
          str: String =>
          Attribute("type",str)
        }.toList ++ attributes.map{
          case (key: String, value: String) => Attribute(key,value)
        }.toList
      )
    ) ++ names.flatMap{
      case id: QlmID =>
        Seq(
          StartElement( "name",
            id.tagType.map{
              str: String =>
                Attribute("tagType",str)
              }.toList ++ id.idType.map{
                str: String =>
                Attribute("idType",str)
              }.toList ++ id.startDate.map{
                timestamp: Timestamp =>
                Attribute("startDate",timestampToDateTimeString(timestamp))
              }.toList ++  id.endDate.map{
                timestamp: Timestamp =>
                Attribute("endDate",timestampToDateTimeString(timestamp))
              }.toList ++ id.attributes.map{
                case (key: String, value: String) => Attribute(key,value)
              }.toList

          ),
          Characters( id.id ),
          EndElement("name")
        )
    } ++ descriptions.flatMap{
      case desc: Description =>
        desc.asXMLEvents
    } ++ values.flatMap{
      case value: Value[_] =>
        value.asXMLEvents
    } ++ Seq(
      EndElement(
        "InfoItem"
      )
    )
  }
}
