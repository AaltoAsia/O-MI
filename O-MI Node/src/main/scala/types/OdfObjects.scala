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

import java.lang.{Iterable => JavaIterable}

import parsing.xmlGen.xmlTypes._

import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}

/** Class implementing OdfObjects. */
class OdfObjectsImpl(
  objects:              JavaIterable[OdfObject] = Iterable(),
  version:              Option[String] = None
) {

  val path = Path("Objects")
  val description: Option[OdfDescription] = None
  
  /** Method for combining two OdfObjects with same path */
  def union( another: OdfObjects ): OdfObjects = sharedAndUniques[OdfObjects]( another ){
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


  /** Method to convert to scalaxb generated class. */
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
