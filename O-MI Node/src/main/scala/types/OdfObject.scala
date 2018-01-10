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

import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import javax.xml.datatype.{DatatypeConstants => XMLConst}

import scala.collection.immutable.HashMap

import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection._

/** Class implementing OdfObject. */
class  OdfObjectImpl(
  id:                   OdfTreeCollection[OdfQlmID],
  path:                 Path,
  infoItems:            OdfTreeCollection[OdfInfoItem],
  objects:              OdfTreeCollection[OdfObject],
  description:          Option[OdfDescription] = None,
  typeValue:            Option[String] = None,
  attributes:           Map[String,String] = HashMap.empty
) extends Serializable {
  require(path.length > 1,
    s"OdfObject should have longer than one segment path (use OdfObjects for <Objects>): Path($path)")

  def hasDescription: Boolean = description.nonEmpty

  /** Method for combining two OdfInfoItems with same path */
  def combine(another: OdfObject): OdfObject = {
    val thisInfo: HashMap[Path, OdfInfoItem] = HashMap(infoItems.map(ii => (ii.path, ii)): _*)
    val thatInfo: HashMap[Path, OdfInfoItem] = HashMap(another.infoItems.map(ii => (ii.path, ii)): _*)
    val thisObj: HashMap[Path, OdfObject] = HashMap(objects.map(o => (o.path, o)): _*)
    val thatObj: HashMap[Path, OdfObject] = HashMap(another.objects.map(o => (o.path, o)): _*)
    val tmp: OdfTreeCollection[OdfQlmID] = id
    val tmp2: OdfTreeCollection[OdfQlmID] = another.id
    val idsWithDuplicate: Vector[OdfQlmID] = this.id ++ another.id
    val ids: Seq[OdfQlmID]  = idsWithDuplicate.groupBy {
      qlmId: OdfQlmID => qlmId.value
    }.values.collect{
        case Seq(single: OdfQlmID) => Seq(single)
        case Seq( id: OdfQlmID, otherId: OdfQlmID) => 
          if( id.unionable(otherId) ){
            Seq(id.union(otherId))
          } else Seq[OdfQlmID](id, otherId )
    }.toSeq.flatten

    OdfObject(
      ids,
        //case Seq(QlmIDType(valueA, attributesA), QlmIDType(valueB, attributesB)) => //idTypeB, tagTypeB, startDateB, endDateB, attrB)) =>
          //QlmIDType(valueB, attributesA ++ attributesB)},//,
  //          unionOption(startDateB, startDateA) { case (b, a) =>
  //            a compare b match {
  //              case XMLConst.LESSER => a // a < b
  //              case _ => b
  //            }
  //          },
  //          unionOption(endDateB, endDateA) { case (b, a) =>
  //            a compare b match {
  //              case XMLConst.GREATER => a // a > b
  //              case _ => b

  //            }
  //          },
  //          attrA ++ attrB
            path,
      thisInfo.merged(thatInfo) { case ((k1, v1), (_, v2)) => (k1, v1.combine(v2)) }.values,
      thisObj.merged(thatObj) { case ((k1, v1), (_, v2)) => (k1, v1.combine(v2)) }.values,
      another.description orElse description,
      another.typeValue orElse typeValue,
      this.attributes ++ another.attributes
    )
  }

  /**
   * Does something similar to intersection. Note that this method should be called first on hierarchytree and then
   * on the tree that should add the data. Another should be subset of this odfTree. Used to collect metadatas and
   * descriptions from a read request.
   * @param another another Object to merge with
   * @return
   */
  def intersect(another: OdfObject): Option[OdfObject] = {
    sharedAndUniques[Option[OdfObject]]( another: OdfObject){(
      uniqueInfos: Seq[OdfInfoItem],
      anotherUniqueInfos: Seq[OdfInfoItem],
      sharedInfos: Map[Path, Seq[OdfInfoItem]],
      uniqueObjs: Seq[OdfObject],
      anotherUniqueObjs: Seq[OdfObject],
      sharedObjs: Map[Path, Seq[OdfObject]]
      )=>

    val sharedInfosOut = sharedInfos.flatMap{
        case (path: Path, sobj: Seq[OdfInfoItem]) =>
          assert(sobj.length == 2)
          for{
            head <- sobj.headOption
            last <- sobj.lastOption
            infoI = OdfInfoItem(
              path,
              last.values,
              last.description.fold(last.description)(_ => head.description),
              last.metaData.fold(last.metaData)(_ => head.metaData),
              last.typeValue.orElse(head.typeValue),
              head.attributes ++ last.attributes 
              ) //use metadata and description from hierarchytree
          } yield infoI
    }

    val sharedObjsOut = sharedObjs.flatMap{
        case (path: Path, sobj: Seq[OdfObject]) =>
          assert(sobj.length == 2)
          for{
            head <- sobj.headOption
            last <- sobj.lastOption
            res  <- head.intersect(last)
          } yield res
    }

    if(sharedInfosOut.isEmpty && sharedObjsOut.isEmpty && another.description.isEmpty && another.typeValue.isEmpty){
      None
    } else{
      Option(
        OdfObject(
          id, //another.id should be set to empty
          path,
          sharedInfosOut,
          sharedObjsOut,
          //get description only if another has it too
          another.description.fold(another.description)(_ => description),
          another.typeValue orElse typeValue,
          attributes ++ another.attributes 
        )
      )
    }
  }
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
          id, //TODO remove ids of another object?
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
        obj => another.infoItems.exists(
          aobj => aobj.path == obj.path
        )
      )
    val anotherUniqueInfos = another.infoItems.filterNot(
      aobj => infoItems.exists(
        obj => aobj.path == obj.path
      )
    )
    
    val sharedInfos = ( infoItems ++ another.infoItems ).filterNot(
        obj => (uniqueInfos ++ anotherUniqueInfos).exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)

    val uniqueObjs =  
      objects.filterNot(
        obj => another.objects.exists(
          aobj => aobj.path == obj.path
        )
      )
    val anotherUniqueObjs = another.objects.filterNot(
      aobj => objects.exists(
        obj => aobj.path == obj.path
      )
    )
    
    val sharedObjs = (objects ++ another.objects).filterNot(
      obj => (uniqueObjs ++ anotherUniqueObjs).exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)
    constructor(uniqueInfos, anotherUniqueInfos, sharedInfos, uniqueObjs, anotherUniqueObjs, sharedObjs)
  }
  implicit def asObjectType : ObjectType = {
    require(path.length > 1, s"OdfObject should have longer than one segment path: $path")
    ObjectType(
      /*Seq( OdfQlmID(
        path.last, // require checks (also in OdfObject)
        attributes = Map.empty
      )),*/
      id.map(_.asQlmIDType), //
      description.map( des => des.asDescription ).toSeq,
      infoItems.map {
        info: OdfInfoItem =>
          info.asInfoItemType
      },
      ObjectValue = objects.map {
        subobj: OdfObject =>
          subobj.asObjectType
      },
      attributes = Map.empty[String, DataRecord[Any]] ++
        typeValue.map{ n => "@type" -> DataRecord(n) } ++
        attributesToDataRecord( this.attributes )
    )
  }
}
