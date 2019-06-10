package types
package odf

import database.journal.PPersistentNode.NodeType.Objs
import database.journal.{PObjects, PPersistentNode}
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes.{ObjectType, ObjectsType}

import scala.collection.immutable.{HashMap, Map => IMap}
import scala.language.implicitConversions

object Objects {
  val empty: Objects = Objects()
}

@SerialVersionUID(2921615916249400841L)
case class Objects(
                    version: Option[String] = None,
                    attributes: IMap[String, String] = HashMap.empty
                  ) extends Node {
  val path: Path = new Path("Objects")

  def hasStaticData: Boolean = attributes.nonEmpty

  def update(that: Objects): Objects = {
    Objects(that.version.orElse(version), attributes ++ that.attributes)
  }

  def createParent: Node = {
    this
  }

  def createAncestors: Iterable[Node] = {
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

  implicit def asObjectsType(objects: Iterable[ObjectType]): ObjectsType = {
    ObjectsType(
      objects.toSeq,
      attributes = attributesToDataRecord(attributes) ++ version.map {
        version: String => "@version" -> DataRecord(version)
      }
    )
  }

  def readTo(to: Objects): Objects = {
    to.copy(
      this.version.orElse(to.version),
      this.attributes ++ to.attributes
    )
  }

  def persist: PPersistentNode.NodeType = Objs(PObjects(version.getOrElse(""), attributes))
}
