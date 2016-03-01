/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import scala.xml.XML
import java.sql.Timestamp
import java.lang.{ Iterable => JavaIterable }
import scala.collection.JavaConversions.{ asJavaIterable, iterableAsScalaIterable }
import scala.collection.JavaConverters._
import scala.language.existentials

/**
 * Package containing classes presenting O-DF format internally and helper methods for them
 *
 */
object `package` {
  type OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]

  /**
   * Collection type to be used as all children members in odf tree types
   */
  type OdfTreeCollection[T] = Vector[T]
  
  /** Helper method for getting all leaf nodes of O-DF Structure */
  def getLeafs(obj: OdfObject): OdfTreeCollection[OdfNode] = {
    if (obj.infoItems.isEmpty && obj.objects.isEmpty)
      OdfTreeCollection(obj)
    else
      obj.infoItems ++ obj.objects.flatMap {
        subobj =>
          getLeafs(subobj)
      }
  }
  def getLeafs(objects: OdfObjects): OdfTreeCollection[OdfNode] = {
    if (objects.objects.nonEmpty)
      objects.objects.flatMap {
        obj => getLeafs(obj)
      }
    else OdfTreeCollection(objects)
  }
  /** Helper method for getting all OdfNodes found in given OdfNodes. Bascily get list of all nodes in tree.  */
  def getOdfNodes(hasPaths: OdfNode*): Seq[OdfNode] = {
    hasPaths.flatMap {
      case info: OdfInfoItem => Seq(info)
      case obj:  OdfObject   => Seq(obj) ++ getOdfNodes((obj.objects.toSeq ++ obj.infoItems.toSeq): _*)
      case objs: OdfObjects  => Seq(objs) ++ getOdfNodes(objs.objects.toSeq: _*)
    }.toSeq
  }

  /** Helper method for getting all OdfInfoItems found in OdfObjects */
  def getInfoItems( objects: OdfObjects ) : OdfTreeCollection[OdfInfoItem] = {
    getLeafs(objects).collect{ case info: OdfInfoItem => info}
  }

  /**
   * Generates odf tree containing the ancestors of given object up to the root Objects level.
   */
  @annotation.tailrec
  def fromPath(last: OdfNode): OdfObjects = {

    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject(parentPath, OdfTreeCollection(info), OdfTreeCollection())
        fromPath(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(OdfTreeCollection(obj))
        else {
          val parent = OdfObject(parentPath, OdfTreeCollection(), OdfTreeCollection(obj))
          fromPath(parent)
        }

      case objs: OdfObjects =>
        objs

      case newType => throw new MatchError(newType)
    }
  }
  /** Method for generating parent OdfNode of this instance */
  def getParent(child: OdfNode): OdfNode = {
    val parentPath = child.path.dropRight(1)
    child match {
      case info: OdfInfoItem =>
        val parent = OdfObject(parentPath, OdfTreeCollection(info), OdfTreeCollection())
        parent
      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(OdfTreeCollection(obj))
        else {
          val parent = OdfObject(parentPath, OdfTreeCollection(), OdfTreeCollection(obj))
          parent
        }

      case objs: OdfObjects =>
        objs

      case newType => throw new MatchError(newType)
    }
  }

  def getPathValuePairs( objs: OdfObjects ) : OdfTreeCollection[(Path,OdfValue)]={
    getInfoItems(objs).flatMap{ infoitem => infoitem.values.map{ value => (infoitem.path, value)} }
  }
}
object OdfTreeCollection {
  def apply[T](): OdfTreeCollection[T] = Vector()
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


}
/** Class presenting O-DF Object structure*/
case class OdfObject(
  path: Path,
  infoItems: OdfTreeCollection[OdfInfoItem] = OdfTreeCollection(),
  objects: OdfTreeCollection[OdfObject] = OdfTreeCollection(),
  description: Option[OdfDescription] = None,
  typeValue: Option[String] = None
  ) extends OdfObjectImpl(path, infoItems, objects, description, typeValue) with OdfNode with Serializable{

  def get(path: Path) : Option[OdfNode] ={
    if( path == this.path ) return Some(this)
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
  implicit def asDescription = Description(value, lang, Map.empty)
}

