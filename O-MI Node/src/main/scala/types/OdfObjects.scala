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
package OdfTypes


import scala.collection.immutable.HashMap
import scala.xml.NodeSeq

import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import parsing.xmlGen.{odfDefaultScope, scalaxb, defaultScope}
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfObjects. */
class OdfObjectsImpl(
  objects:              OdfTreeCollection[OdfObject] = OdfTreeCollection(),
  version:              Option[String] = None,
  attributes:           Map[String,String] = HashMap.empty
) extends Serializable {

  val path = Path("Objects")
  val description: Option[OdfDescription] = None

  /** Method for combining two OdfObjects with same path */
  def union(another: OdfObjects): OdfObjects = {
    val thisObjs: HashMap[Path, OdfObject] = HashMap(objects.map(o => (o.path, o)):_*)
    val anotherObjs: HashMap[Path, OdfObject] = HashMap(another.objects.map(ao => (ao.path, ao)):_*)
    OdfObjects(
      thisObjs.merged(anotherObjs){case ((k1, v1),(_, v2)) => (k1,v1.combine(v2))}.values,
    unionOption(version,another.version){
      case (a, b) if a > b =>a
      case (a, b) => b
    },
      this.attributes ++ another.attributes
    )

  }

  def --(another: OdfObjects): OdfObjects = sharedAndUniques[OdfObjects] ( another ) {
    (uniqueObjs: Seq[OdfObject], anotherUniqueObjs: Seq[OdfObject], sharedObjs: Map[Path, Seq[OdfObject]]) =>
    OdfObjects(
      sharedObjs.flatMap{
        case (path:Path, sobj: Seq[OdfObject]) =>
          sobj.headOption.flatMap{head => sobj.lastOption.flatMap(last =>
            head -- last)}//.getOrElse(throw new UninitializedError())
      }.toSeq ++ uniqueObjs,
      version
    )
  }

  /**
   * Does something similar to intersection. Note that this method should be called first on hierarchytree and then
   * on the tree that should be added the data. another should be subset of this odfTree.
   * @param another another OdfObjects to intersect with
   * @return
   */
  def intersect(another: OdfObjects): OdfObjects = sharedAndUniques[OdfObjects] ( another ) {
    (
    uniqueObjs: Seq[OdfObject],
    anotherUniqueObjs: Seq[OdfObject],
    sharedObjs: Map[Path, Seq[OdfObject]]) =>
    OdfObjects(
      sharedObjs.flatMap{
      case (path: Path, sobj: Seq[OdfObject]) =>
        for{
          head <- sobj.headOption //first object
          last <- sobj.lastOption //second object
          res  <- head.intersect(last)  //intersection
        } yield res
      }.toSeq,
      version orElse another.version
    )
  }

  private[this] def sharedAndUniques[A]( another: OdfObjects )( constructor: (
    Seq[OdfObject],
    Seq[OdfObject],
    Map[Path,Seq[OdfObject]]) => A) = {
    val uniqueObjs : Seq[OdfObject]  = objects.filterNot(
        obj => another.objects.toSeq.exists(
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq
     val anotherUniqueObjs =  another.objects.filterNot(
        aobj => objects.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    
    val sharedObjs = ( objects.toSeq ++ another.objects.toSeq ).filterNot(
      obj => (uniqueObjs ++ anotherUniqueObjs).exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)
    constructor(uniqueObjs, anotherUniqueObjs,sharedObjs)
  }

  def odfDefaultScope = scalaxb.toScope(
    (Set(
      None -> "http://www.opengroup.org/xsd/odf/1.0/",
      Some("xs") -> "http://www.w3.org/2001/XMLSchema",
      Some("odf") -> "http://www.opengroup.org/xsd/odf/1.0/"
      
      )).toSet:_*
  )

  /** Method to convert to scalaxb generated class. */
  implicit def asObjectsType : ObjectsType ={
    ObjectsType(
      ObjectValue = objects.map{
        obj: OdfObject => 
        obj.asObjectType
      }.toSeq,
      attributes = version.fold(Map.empty[String, DataRecord[Any]])(n => Map(("@version" -> DataRecord(n)))) ++ attributesToDataRecord( this.attributes )
    )
  }
  implicit def asXML : NodeSeq= {
    val xml  = scalaxb.toXML[ObjectsType](asObjectsType, None, Some("Objects"), odfDefaultScope)
    xml//.asInstanceOf[Elem] % new UnprefixedAttribute("xmlns","odf.xsd", Node.NoAttributes)
  }
}    
