package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
class  OdfObjectImpl(
  path:                 Path,
  infoItems:            JavaIterable[OdfInfoItem],
  objects:              JavaIterable[OdfObject],
  description:          Option[OdfDescription] = None,
  typeValue:            Option[String] = None
){
require(path.length > 1,
  s"OdfObject should have longer than one segment path (use OdfObjects for <Objects>): Path(${path})")

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

  def update( another: OdfObject ) : (OdfObject, Seq[(Path, OdfNode)]) = sharedAndUniques[(OdfObject,Seq[(Path,OdfNode)])](another){(
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
                head.update(last)
              case None =>
                throw new Exception("No last found when updating OdfObject")
            }
            case None =>
              throw new Exception("No head found when updating OdfObject")
          }
      }
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
    val anotherUniqueInfosOut = anotherUniqueInfos.map{ info => (info.path, info)}
    val anotherUniqueObjsOut = getOdfNodes(anotherUniqueObjs : _*).map{ node => (node.path, node) }
    val newObj = OdfObject(
      path, 
      sharedInfosOut.collect{case (path, node : OdfInfoItem) => node}.toSeq ++
      uniqueInfos ++
      anotherUniqueInfos,
      updatedSharedObjs  ++ 
      uniqueObjs ++ 
      anotherUniqueObjs,
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
    (
      newObj,
      (Seq((path,newObj)) ++
      sharedInfosOut ++
      sharedObjsOut ++
      anotherUniqueInfosOut ++
      anotherUniqueObjsOut).toSeq
    )
  }

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
  def get(path: Path) : Option[OdfNode] ={
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
