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
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfObject. */
class  OdfObjectImpl(
  path:                 Path,
  infoItems:            OdfTreeCollection[OdfInfoItem],
  objects:              OdfTreeCollection[OdfObject],
  description:          Option[OdfDescription] = None,
  typeValue:            Option[String] = None
) extends Serializable {
require(path.length > 1,
  s"OdfObject should have longer than one segment path (use OdfObjects for <Objects>): Path(${path})")

  def hasDescription: Boolean = description.nonEmpty

  /** Method for combining two OdfInfoItems with same path */
  def combine( another: OdfObject ) : OdfObject =  sharedAndUniques[OdfObject](another){(
      uniqueInfos : Seq[OdfInfoItem] ,
      anotherUniqueInfos : Seq[OdfInfoItem] ,
      sharedInfos : Map[Path, Seq[OdfInfoItem]],
      uniqueObjs : Seq[OdfObject] ,
      anotherUniqueObjs : Seq[OdfObject] ,
      sharedObjs : Map[Path,Seq[OdfObject]]
    ) =>
    val sharedInfosOut = sharedInfos.map{
        case (path:Path, sobj: Seq[OdfInfoItem]) =>
        assert(sobj.length == 2)
        sobj.headOption match{
          case Some( head ) =>
            sobj.lastOption match{
              case Some(last) => 
                head.combine(last)
              case None =>
                throw new Exception("No last found when combining OdfObject")
            }
            case None =>
              throw new Exception("No head found when combining OdfObject")
          }
      }
    val sharedObjsOut = sharedObjs.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.headOption match{
          case Some( head ) =>
            sobj.lastOption match{
              case Some(last) => 
                head.combine(last)
              case None =>
                throw new Exception("No last found when combining OdfObject")
            }
            case None =>
              throw new Exception("No head found when combining OdfObject")
          }
      }
    OdfObject(
      path, 
      sharedInfosOut ++
      uniqueInfos ++
      anotherUniqueInfos,
      sharedObjsOut ++ 
      uniqueObjs ++ 
      anotherUniqueObjs ,
      (description, another.description) match{
        case (Some(a), Some(b)) => Some(b)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      },
      (typeValue, another.typeValue) match{
        case (Some(a), Some(b)) => Some(b)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      }
    )
  }

  /**
   * Method for calculating the unique values to this object compared to the given object. This method calculates the
   * relative component of given objects leafs in current object 'THIS \ THAT'
   * @param another OdfObject to be removed from current Object
   * @return
   */
  def --( another: OdfObject ): Option[OdfObject] = sharedAndUniques[Option[OdfObject]]( another: OdfObject){(
      uniqueInfos : Seq[OdfInfoItem] ,
      anotherUniqueInfos : Seq[OdfInfoItem] ,
      sharedInfos : Map[Path, Seq[OdfInfoItem]],
      uniqueObjs : Seq[OdfObject] ,
      anotherUniqueObjs : Seq[OdfObject] ,
      sharedObjs : Map[Path,Seq[OdfObject]]
      ) =>

      val uniquesAndShared = sharedObjs.flatMap{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.headOption match{
          case Some( head ) =>
            sobj.lastOption match{
              case Some(last) =>
                head -- last
              case None =>
                throw new Exception("No last found when combining OdfObject")
            }
            case None =>
              throw new Exception("No head found when combining OdfObject")
          }
      } ++ uniqueObjs

      if(uniqueInfos.isEmpty && uniquesAndShared.isEmpty){
        None
      } else {
      Option(
        OdfObject(
          path,
          uniqueInfos,
          uniquesAndShared,
          description,
          typeValue
          )
        )
      }
  }

  /** Method to convert to scalaxb generated class. */
  private[this] def sharedAndUniques[A]( another: OdfObject )( 
    constructor: (
      Seq[OdfInfoItem],
      Seq[OdfInfoItem],
      Map[Path,Seq[OdfInfoItem]],
      Seq[OdfObject],
      Seq[OdfObject],
      Map[Path,Seq[OdfObject]]) => A) = {
    assert( path == another.path )
    val uniqueInfos =  
      infoItems.filterNot( 
        obj => another.infoItems.toSeq.exists( 
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq
    val anotherUniqueInfos = another.infoItems.filterNot(
        aobj => infoItems.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    
    val sharedInfos = ( infoItems.toSeq ++ another.infoItems.toSeq ).filterNot(
        obj => (uniqueInfos ++ anotherUniqueInfos).exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)

    val uniqueObjs =  
      objects.filterNot( 
        obj => another.objects.toSeq.exists( 
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq 
    val anotherUniqueObjs = another.objects.filterNot(
        aobj => objects.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    
    val sharedObjs = (objects.toSeq ++ another.objects.toSeq).filterNot(
      obj => (uniqueObjs ++ anotherUniqueObjs).exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)
    constructor(uniqueInfos, anotherUniqueInfos, sharedInfos, uniqueObjs, anotherUniqueObjs, sharedObjs)
  }
  implicit def asObjectType : ObjectType = {
    require(path.length > 1, s"OdfObject should have longer than one segment path: ${path}")
    ObjectType(
      Seq( QlmID(
        path.last, // require checks (also in OdfObject)
        attributes = Map.empty
      )),
      description.map( des => des.asDescription ),
      infoItems.map{ 
        info: OdfInfoItem =>
        info.asInfoItemType
      }.toSeq,
      Object = objects.map{ 
        subobj: OdfObject =>
        subobj.asObjectType
      }.toSeq,
      attributes = Map.empty
    )
  }
}
