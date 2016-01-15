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
import scala.language.existentials

/**
 * Package containing classes presenting O-DF format internally and helper methods for them
 *
 */
object `package` {
  type OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]
  
  /** Helper method for getting all leaf nodes of O-DF Structure */
  def getLeafs(objects: OdfObjects): JavaIterable[OdfNode] = {
    def getLeafs(obj: OdfObject): JavaIterable[OdfNode] = {
      if (obj.infoItems.isEmpty && obj.objects.isEmpty)
        Iterable(obj)
      else
        obj.infoItems ++ obj.objects.flatMap {
          subobj =>
            getLeafs(subobj)
        }
    }
    if (objects.objects.nonEmpty)
      objects.objects.flatMap {
        obj => getLeafs(obj)
      }
    else Iterable(objects)
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
  def getInfoItems( objects: OdfObjects ) : JavaIterable[OdfInfoItem] = {
    getLeafs(objects).collect{ case info: OdfInfoItem => info}
  }
  /**
   * Generates odf tree containing the ancestors of given object.
   */
  @annotation.tailrec
  def fromPath(last: OdfNode): OdfObjects = {

    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject(parentPath, Iterable(info), Iterable())
        fromPath(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(Iterable(obj))
        else {
          val parent = OdfObject(parentPath, Iterable(), Iterable(obj))
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
        val parent = OdfObject(parentPath, Iterable(info), Iterable())
        parent
      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(Iterable(obj))
        else {
          val parent = OdfObject(parentPath, Iterable(), Iterable(obj))
          parent
        }

      case objs: OdfObjects =>
        objs

      case newType => throw new MatchError(newType)
    }
  }

  def getPathValuePairs( objs: OdfObjects ) : JavaIterable[(Path,OdfValue)]={
    getInfoItems(objs).flatMap{ infoitem => infoitem.values.map{ value => (infoitem.path, value)} }
  }
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
  objects: JavaIterable[OdfObject] = Iterable(),
  version: Option[String] = None) extends OdfObjectsImpl(objects, version) with OdfNode{
  /** Method for searching OdfNode from O-DF Structure */
  def get(path: Path) : Option[OdfNode] = {
    if( path == this.path ) return Some(this)
    //HeadOption is because of values being Iterable of OdfObject
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

}
/** Class presenting O-DF Object structure*/
case class OdfObject(
  path: Path,
  infoItems: JavaIterable[OdfInfoItem],
  objects: JavaIterable[OdfObject],
  description: Option[OdfDescription] = None,
  typeValue: Option[String] = None) extends OdfObjectImpl(path, infoItems, objects, description, typeValue) with OdfNode{
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
        objects = objects map (_.valuesRemoved),
        infoItems = infoItems map (_.valuesRemoved)
      )

}
  
/** Class presenting O-DF InfoItem structure*/
case class OdfInfoItem(
    path: Path,
    values: JavaIterable[OdfValue] = Iterable(),
    description: Option[OdfDescription] = None,
    metaData: Option[OdfMetaData] = None) extends OdfInfoItemImpl(path, values, description, metaData) with OdfNode {
  def get(path: Path): Option[OdfNode] = if (path == this.path) Some(this) else None
  def valuesRemoved: OdfInfoItem = this.copy(values = Iterable())
}

/** Class presenting O-DF description element*/
case class OdfDescription(
    value: String,
    lang: Option[String] = None) {
  implicit def asDescription = Description(value, lang, Map.empty)
}

