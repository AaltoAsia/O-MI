
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

package types


import java.lang.{Iterable => JavaIterable}
import java.util.{GregorianCalendar, Date}
import javax.xml.datatype.DatatypeFactory
import java.sql.Timestamp
import javax.xml.datatype.XMLGregorianCalendar

import scala.language.existentials
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq
import scala.collection.JavaConversions

import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen._
import types.OdfTypes._

/**
 * Package containing classes presenting O-DF format internally and helper methods for them
 *
 */
package object OdfTypes {
  type OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]

  /**
   * Collection type to be used as all children members in odf tree types
   */
  type OdfTreeCollection[T] = Vector[T]

  def unionOption[T](a: Option[T], b: Option[T])(f: (T,T) => T): Option[T] = {
    (a,b) match{
        case (Some(_a), Some(_b)) => Some(f(_a,_b))
        case (None, Some(_b)) => Some(_b)
        case (Some(_a), None) => Some(_a)
        case (None, None) => None
    }
  }

  
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
  /** Helper method for getting all OdfNodes found in given OdfNodes. Basically get list of all nodes in tree.  */
  def getOdfNodes(hasPaths: OdfNode*): Seq[OdfNode] = hasPaths.flatMap {
    case info: OdfInfoItem => Seq(info)
    case obj: OdfObject => Seq(obj) ++ getOdfNodes(obj.objects.toSeq ++ obj.infoItems.toSeq: _*)
    case objs: OdfObjects => Seq(objs) ++ getOdfNodes(objs.objects.toSeq: _*)
  }

  /** Helper method for getting all OdfInfoItems found in OdfObjects */
  def getInfoItems( objects: OdfObjects ) : OdfTreeCollection[OdfInfoItem] = {
    getLeafs(objects).collect{ case info: OdfInfoItem => info}
  }

  def getInfoItems( _object: OdfObject ) : Vector[OdfInfoItem] = {
    getLeafs(_object).collect{ case info: OdfInfoItem => info}

    /*nodes.flatMap {
   }.toVector*/
  }
  def getInfoItems( nodes: OdfNode*) : Vector[OdfInfoItem] ={
    nodes.flatMap{
      case info: OdfInfoItem => Vector(info)
      case obj: OdfObject    => getInfoItems(obj)
      case objs: OdfObjects  => getInfoItems(objs)
    }.toVector
  }

  /**
   * Generates odf tree containing the ancestors of given object up to the root Objects level.
   */
  @annotation.tailrec
  def createAncestors(last: OdfNode): OdfObjects = {
    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject(OdfTreeCollection(OdfQlmID(parentPath.last)), parentPath, OdfTreeCollection(info), OdfTreeCollection())
        createAncestors(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(OdfTreeCollection(obj))
        else {
          val parent = OdfObject(OdfTreeCollection(OdfQlmID(parentPath.last)),parentPath, OdfTreeCollection(), OdfTreeCollection(obj))
          createAncestors(parent)
        }

      case objs: OdfObjects =>
        objs
    }
  }
  /** Method for generating parent OdfNode of this instance */
  def getParent(child: OdfNode): OdfNode = {
    val parentPath = child.path.dropRight(1)
    child match {
      case info: OdfInfoItem =>
        val parent = OdfObject(OdfTreeCollection(), parentPath, OdfTreeCollection(info), OdfTreeCollection())
        parent
      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(OdfTreeCollection(obj))
        else {
          val parent = OdfObject(OdfTreeCollection(), parentPath, OdfTreeCollection(), OdfTreeCollection(obj))
          parent
        }

      case objs: OdfObjects =>
        objs

    }
  }

  def getPathValuePairs( objs: OdfObjects ) : OdfTreeCollection[(Path,OdfValue[Any])]={
    getInfoItems(objs).flatMap{ infoitem => infoitem.values.map{ value => (infoitem.path, value)} }
  }
  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar ={
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }

  def attributesToDataRecord( attributes: Map[String,String] ) : Map[String,DataRecord[Any]] ={
    attributes.map{
      case (key: String, value: String) =>
        if (key.startsWith("@"))
          key -> DataRecord(None, Some(key.tail), value)
        else
          "@" + key -> DataRecord(None, Some(key), value)
    }
  }
}
