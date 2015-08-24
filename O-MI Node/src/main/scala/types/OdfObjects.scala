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
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}

class OdfObjectsImpl(
  objects:              JavaIterable[OdfObject] = Iterable(),
  version:              Option[String] = None
) {

  val path = Path("Objects")
  val description: Option[OdfDescription] = None
  
  def combine( another: OdfObjects ): OdfObjects = sharedAndUniques[OdfObjects]( another ){
    (uniqueObjs : Seq[OdfObject], anotherUniqueObjs : Seq[OdfObject], sharedObjs : Map[Path,Seq[OdfObject]]) =>
    OdfObjects(
      sharedObjs.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        sobj.headOption.flatMap{head =>sobj.lastOption.map(last=> 
          head.combine(last))}.getOrElse(throw new UninitializedError()) // .toList() might also work instead of getOrElse?
      }.toSeq ++ uniqueObjs ++ anotherUniqueObjs,
      (version, another.version) match{
        case (Some(a), Some(b)) => Some(a)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      }
    )
  }
  def update( another: OdfObjects ): (OdfObjects, Seq[(Path,OdfNode)]) =sharedAndUniques[(OdfObjects,Seq[(Path,OdfNode)])]( another ){
    (uniqueObjs : Seq[OdfObject], anotherUniqueObjs : Seq[OdfObject], sharedObjs : Map[Path,Seq[OdfObject]]) =>
    val sharedObjsTuples = sharedObjs.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.headOption match{
          case Some( head ) =>
            sobj.lastOption match{
              case Some(last) => 
                head.update(last)
              case None =>
                throw new Exception("No last found when updating OdfObject")
            }
            case None =>
              throw new Exception("No head found when updating OdfObject")
          }
      }
    val updatedSharedObjs = sharedObjsTuples.map(_._1).toSeq
    val sharedObjsOut  = sharedObjsTuples.flatMap(_._2).toSeq
    val anotherUniqueObjsOut = getOdfNodes(anotherUniqueObjs : _*).map{ node => (node.path, node) } 
    val newObjs = OdfObjects(
      updatedSharedObjs  ++ 
      uniqueObjs ++ 
      anotherUniqueObjs,
      (version, another.version) match{
        case (Some(a), Some(b)) => Some(b)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      }
    )
    (
      newObjs,
      (Seq((Path("Objects"),newObjs)) ++
      sharedObjsOut ++
      anotherUniqueObjsOut).toSeq
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

  def get(path: Path) : Option[OdfNode] = {
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
  implicit def asObjectsType : ObjectsType ={
    ObjectsType(
      Object = objects.map{
        obj: OdfObject => 
        obj.asObjectType
      }.toSeq,
      version
    )
  }
}    
