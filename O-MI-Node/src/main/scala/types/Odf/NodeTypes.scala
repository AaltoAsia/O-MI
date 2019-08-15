package types
package odf

import akka.stream.alpakka.xml.{Attribute, EndElement, ParseEvent, StartElement}
import database.journal.PPersistentNode.NodeType.{Ii, Obj, Objs}
import database.journal.{PInfoItem, PObject, PObjects, PPersistentNode, PValueList}

import scala.language.implicitConversions
import scala.collection.{Seq, SeqView}
import scala.collection.immutable.{HashMap, Map}

import akka.stream.Materializer

sealed trait Node {
  def createAncestors: Seq[Node]

  def createParent: Node

  def attributes: Map[String, String]

  def path: Path

  def hasStaticData: Boolean

  def persist(implicit mat: Materializer): PPersistentNode.NodeType // = PersistentNode(path.toString,attributes)
}
object Objects {
  val empty: Objects = Objects()
}

@SerialVersionUID(2921615916249400841L)
case class Objects(
                    version: Option[String] = None,
                    attributes: Map[String, String] = HashMap.empty
                  ) extends Node {
  val path: Path = new Path("Objects")

  def hasStaticData: Boolean = attributes.nonEmpty

  def update(that: Objects): Objects = {
    Objects(that.version.orElse(version), attributes ++ that.attributes)
  }

  def createParent: Node = {
    this
  }

  def createAncestors: Seq[Node] = {
    Vector()
  }

  /**
    * true if first is greater or equeal version number
    */
  private def vcomp(x: String, y: String): Boolean =
    (x.split("\\.") zip y.split("\\."))
      .foldLeft(true) {
        case (true, (a, b)) if a >= b => true;
        case _ => false
      }

  def intersection(that: Objects): Objects = {
    Objects(
      (this.version, that.version) match {
        case (o@Some(oldv), n@Some(newv)) =>
          if (vcomp(oldv, newv)) n
          else o
        case _ => this.version orElse that.version
      },
      that.attributes ++ attributes
    )
  }

  def union(that: Objects): Objects = {
    Objects(
      (this.version, that.version) match {
        case (o@Some(oldv), n@Some(newv)) =>
          if (vcomp(oldv, newv)) o
          else n
        case _ => optionUnion(this.version, that.version)
      },
      attributeUnion(attributes, that.attributes)
    )
  }

  def readTo(to: Objects): Objects = {
    to.copy(
      this.version.orElse(to.version),
      this.attributes ++ to.attributes
    )
  }

  def persist(implicit mat: Materializer): PPersistentNode.NodeType = Objs(PObjects(version.getOrElse(""), attributes))
}

object Object {
  def apply(
             path: Path
           ): Object = Object(
    Vector(QlmID(path.last)),
    path
  )

  def apply(
             path: Path,
             typeAttribute: Option[String]
           ): Object = Object(
    Vector(QlmID(path.last)),
    path,
    typeAttribute
  )

  def apply(
             path: Path,
             typeAttribute: Option[String],
             descriptions: Set[Description],
             attributes: Map[String, String]
           ): Object = Object(
    Vector(QlmID(path.last)),
    path,
    typeAttribute,
    descriptions,
    attributes
  )
}

case class Object(
                   ids: Vector[QlmID],
                   path: Path,
                   typeAttribute: Option[String] = None,
                   descriptions: Set[Description] = Set.empty,
                   attributes: Map[String, String] = HashMap.empty
                 ) extends Node with Unionable[Object] {
  assert(ids.nonEmpty, "Object doesn't have any ids.")
  assert(path.length > 1, "Length of path of Object is not greater than 1 (Objects/).")

  def idsToStr(): Vector[String] = ids.map {
    id: QlmID =>
      id.id
  }

  def idTest: Boolean = idsToStr().exists {
    id: String =>
      val pl = path.last
      id == pl
  }

  def tmy = s"Ids don't contain last id in path. ${ path.last } not in (${idsToStr().mkString(",")})"

  assert(idTest, tmy)

  def update(that: Object): Object = {
    val pathsMatches = path == that.path
    val containSameId = ids.map(_.id).toSet.intersect(that.ids.map(_.id).toSet).nonEmpty
    assert(containSameId && pathsMatches)
    Object(
      QlmID.unionReduce(ids ++ that.ids).toVector,
      path,
      that.typeAttribute.orElse(typeAttribute),
      Description.unionReduce(descriptions ++ that.descriptions),
      attributes ++ that.attributes
    )

  }

  def hasStaticData: Boolean = {
    attributes.nonEmpty ||
      ids.length > 1 ||
      typeAttribute.nonEmpty ||
      descriptions.nonEmpty
  }

  def union(that: Object): Object = {
    val pathsMatches = path == that.path
    val containSameId = ids.map(_.id).toSet.intersect(that.ids.map(_.id).toSet).nonEmpty
    assert(containSameId && pathsMatches)
    Object(
      QlmID.unionReduce(ids ++ that.ids).toVector,
      path,
      optionAttributeUnion(typeAttribute, that.typeAttribute),
      Description.unionReduce(descriptions ++ that.descriptions),
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
    val parentPath = path.getParent
    if (parentPath.isEmpty || parentPath == Path("Objects")) {
      Objects()
    } else {
      Object(
        Vector(
          QlmID(
            parentPath.last
          )
        ),
        parentPath
      )
    }
  }


  def readTo(to: Object): Object = {
    val pathsMatches = path == to.path
    val containSameId = ids.map(_.id).toSet.intersect(to.ids.map(_.id).toSet).nonEmpty
    assert(containSameId && pathsMatches)

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
    //TODO: Filter ids based on QlmID attributes
    to.copy(
      ids = QlmID.unionReduce(ids ++ to.ids).toVector,
      typeAttribute = typeAttribute.orElse(to.typeAttribute),
      descriptions = desc.toSet,
      attributes = attributes ++ to.attributes
    )
  }

  def persist(implicit mat: Materializer): PPersistentNode.NodeType = Obj(PObject(typeAttribute.getOrElse(""),
    ids.map(_.persist),
    descriptions.map(_.persist()).toSeq,
    attributes))
}

object InfoItem {
  sealed trait Parameters {
    def apply(): InfoItem
  }
  trait Builders {
    implicit def fromPath(path: Path) = new Parameters {
      def apply() = InfoItem(path.last, path)
    }
    implicit def fromPathValues(t: (Path, Vector[Value[Any]])) = new Parameters {
      val (path, values) = t
      def apply() =
        InfoItem( path.last, path, values=values)
    }
    implicit def fromPathValuesDesc(t: (Path,
      Vector[Value[Any]],
      Description)) = new Parameters {
      val (path, values, description) = t
      def apply() = InfoItem(path.last, path, values=values, descriptions=Set(description))
    }
    implicit def fromPathValuesMeta(t: (Path,
      Vector[Value[Any]],
      MetaData)) = new Parameters {
      val (path, values, metaData) = t
      def apply() = InfoItem(path.last, path, values=values, metaData=Some(metaData))
    }
    implicit def fromPathValuesDescMeta(t: (Path,
      Vector[Value[Any]],
      Description,
      MetaData)) = new Parameters {
      val (path, values, description, metaData) = t
      def apply() = InfoItem(path.last, path, values=values, metaData=Some(metaData), descriptions=Set(description))
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
             attributes: Map[String, String]
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
                     attributes: Map[String, String] = HashMap.empty
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

  def persist(implicit mat: Materializer): PPersistentNode.NodeType = Ii(PInfoItem(typeAttribute.getOrElse(""),
    names.map(_.persist),
    descriptions.map(_.persist()).toSeq,
    metaData.map(_.persist),
    attributes
    ))
  final implicit def asXMLEvents: SeqView[ParseEvent,Seq[_]] = {
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
    ).view ++ names.view.flatMap{
      case id: QlmID => id.asXMLEvents("name")
    } ++ descriptions.view.flatMap{
      case desc: Description =>
        desc.asXMLEvents
    } ++ metaData.view.flatMap{
      case meta: MetaData =>
        meta.asXMLEvents
    } ++ values.view.flatMap{
      case value: Value[_] =>
        value.asXMLEvents
    } ++ Seq(
      EndElement(
        "InfoItem"
      )
    )
  }
}
