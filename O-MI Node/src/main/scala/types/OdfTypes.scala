/**********************************************************************************
 *    Copyright (c) 2015 Aalto University.                                        *
 *                                                                                *
 *    Licensed under the 4-clause BSD (the "License");                            *
 *    you may not use this file except in compliance with the License.            *
 *    You may obtain a copy of the License at top most directory of project.      *
 *                                                                                *
 *    Unless required by applicable law or agreed to in writing, software         *
 *    distributed under the License is distributed on an "AS IS" BASIS,           *
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    *
 *    See the License for the specific language governing permissions and         *
 *    limitations under the License.                                              *
 **********************************************************************************/
package types
package OdfTypes

import java.lang.{Iterable => JavaIterable}

import scala.collection.JavaConverters._
import scala.language.existentials

import parsing.xmlGen.xmlTypes._

object OdfTreeCollection {
  def apply[T](): OdfTreeCollection[T] = Vector()
  def empty[T]: OdfTreeCollection[T] = Vector()
  def apply[T](elems: T*): OdfTreeCollection[T] = Vector(elems:_*)
  def fromIterable[T](elems: Iterable[T]): OdfTreeCollection[T] = elems.toVector
  def toJava[T](c: OdfTreeCollection[T]): java.util.List[T] = c.toBuffer.asJava
  def fromJava[T](i: java.lang.Iterable[T]): OdfTreeCollection[T] = fromIterable(i.asScala)
  import scala.language.implicitConversions
  implicit def seqToOdfTreeCollection[E](s: Iterable[E]): OdfTreeCollection[E] = OdfTreeCollection.fromIterable(s)
}

/** Sealed base trait defining all shared members of OdfNodes*/
sealed trait OdfNode {
  /** Member for storing path of OdfNode */
  def path: Path
  /** Member for storing description for OdfNode */
  def description: Option[OdfDescription]
  /** Method for searching OdfNode from O-DF Structure */
  def get(path: Path): Option[OdfNode]
}

/** Class presenting O-DF Objects structure*/
case class OdfObjects(
  objects: OdfTreeCollection[OdfObject] = OdfTreeCollection(),
  version: Option[String] = None) extends OdfObjectsImpl(objects, version) with OdfNode {

  /** Method for searching OdfNode from O-DF Structure */
  def get(path: Path) : Option[OdfNode] = {
    if( path == this.path ) return Some(this)
    //HeadOption is because of values being OdfTreeCollection of OdfObject
    val grouped = objects.groupBy(_.path).mapValues{_.headOption.getOrElse(throw new Exception("Pathless Object was grouped."))}
    grouped.get(path) match {
      case None => 
        grouped.get(path.take(2)) match{
          case None => 
            None
          case Some(obj: OdfObject) =>
            obj.get(path)
       }
      case Some(obj) => Some(obj)
    }
  }

  def valuesRemoved: OdfObjects = this.copy(objects = objects map (_.valuesRemoved))
  //def withValues: (Path, Seq[OdfValue]) => OdfObjects = { case (p, v) => withValues(p, v) } 
  def withValues(p: Path, v: Seq[OdfValue]): OdfObjects = {
    val nextPath = path.toSeq.tail
    require(nextPath.nonEmpty, s"Tried to set values for $path: $p -> $v")

    this.copy(objects = objects map (o => if (o.path == nextPath) o.withValues(p, v) else o))
  }

  lazy val infoItems = getInfoItems(this)
  lazy val paths = infoItems map (_.path)

  /**
   * Returns Object nodes that have metadata-like information.
   * Includes nodes that have type or description
   */
  lazy val objectsWithMetadata = getOdfNodes(this) collect {
      case o @ OdfObject(_, _, _, _, desc, typeVal) if desc.isDefined || typeVal.isDefined => o
    } 

}

/** Class presenting O-DF Object structure*/
case class OdfObject(
  id: OdfTreeCollection[QlmID],
  path: Path,
  infoItems: OdfTreeCollection[OdfInfoItem] = OdfTreeCollection(),
  objects: OdfTreeCollection[OdfObject] = OdfTreeCollection(),
  description: Option[OdfDescription] = None,
  typeValue: Option[String] = None
  ) extends OdfObjectImpl(id, path, infoItems, objects, description, typeValue) with OdfNode with Serializable{


  def get(path: Path) : Option[OdfNode] = path match{
      case this.path => Some(this)
      case default : Path =>
    val haspaths = infoItems.toSeq.map{ item => item : OdfNode} ++ objects.toSeq.map{ item => item : OdfNode}
    val grouped = haspaths.groupBy(_.path).mapValues{_.headOption.getOrElse(OdfObjects())}
    grouped.get(path) match {
      case None => 
        grouped.get(path.take(this.path.length + 1)) match{
          case None => 
            None
          case Some(obj: OdfObject) =>
            obj.get(path)
          case Some(obj: OdfInfoItem) =>
            None
          case Some(obj: OdfObjects) =>
            None
       }
      case Some(obj: OdfObject) => Some(obj)
      case Some(obj: OdfObjects) =>
            None
      case Some(obj: OdfInfoItem) => Some(obj)
    }
  
  }
  def valuesRemoved: OdfObject = this.copy(
        objects   = objects map (_.valuesRemoved),
        infoItems = infoItems map (_.valuesRemoved)
      )
  def withValues(p: Path, v: Seq[OdfValue]): OdfObject = {
    val nextPath = p.toSeq.tail
    require(nextPath.nonEmpty, s"Tried to set values for Object $path: $p -> $v")

    this.copy(
      objects = objects map (o => if (o.path == nextPath) o.withValues(p, v) else o),
      infoItems = infoItems map (i => if (i.path == nextPath) i.withValues(v) else i) 
    ) 
  }

}
  
/** Class presenting O-DF InfoItem structure*/
case class OdfInfoItem(
    path: Path,
    values: OdfTreeCollection[OdfValue] = OdfTreeCollection(),
    description: Option[OdfDescription] = None,
    metaData: Option[OdfMetaData] = None)
  extends OdfInfoItemImpl(path, values, description, metaData) with OdfNode {
  def get(path: Path): Option[OdfNode] = if (path == this.path) Some(this) else None
  def valuesRemoved: OdfInfoItem = if (values.nonEmpty) this.copy(values = OdfTreeCollection()) else this
  def withValues(v: Seq[OdfValue]): OdfInfoItem = this.copy(values = OdfTreeCollection(v:_*))
}

/** Class presenting O-DF description element*/
case class OdfDescription(
    value: String,
    lang: Option[String] = None) {
  implicit def asDescription : Description= Description(value, lang, Map.empty)
}

