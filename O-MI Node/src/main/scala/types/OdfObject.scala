package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
case class OdfObject(
  path:                 Path,
  infoItems:            JavaIterable[OdfInfoItem],
  objects:              JavaIterable[OdfObject],
  description:          Option[OdfDescription] = None,
  typeValue:            Option[String] = None
) extends OdfElement with HasPath {
require(path.length > 1,
  s"OdfObject should have longer than one segment path (use OdfObjects for <Objects>): Path(${path})")

  def combine( another: OdfObject ) : OdfObject = {
    assert( path == another.path )
    val uniqueInfos = ( 
      infoItems.filterNot( 
        obj => another.infoItems.toSeq.exists( 
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq ++ another.infoItems.filterNot(
        aobj => infoItems.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    )
    val sameInfos = ( infoItems.toSeq ++ another.infoItems.toSeq ).filterNot(
        obj => uniqueInfos.exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)

    val uniqueObjs = ( 
      objects.filterNot( 
        obj => another.objects.toSeq.exists( 
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq ++ 
      another.objects.filterNot(
        aobj => objects.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    )
    val sameObjs = (objects.toSeq ++ another.objects.toSeq).filterNot(
      obj => uniqueObjs.exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)

    OdfObject(
      path, 
      sameInfos.map{
        case (path:Path, sobj: Seq[OdfInfoItem]) =>
        assert(sobj.length == 2)
        sobj.head.combine(sobj.last) // assert checks
      }.toSeq ++ uniqueInfos,
      sameObjs.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.head.combine(sobj.last) // assert checks
      }.toSeq ++ uniqueObjs,
      (description, another.description) match{
        case (Some(a), Some(b)) => Some(a)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      },
      (typeValue, another.typeValue) match{
        case (Some(a), Some(b)) => Some(a)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      }
    )
  }
  def update( another: OdfObject ) : OdfObject ={
    assert( path == another.path )
    val uniqueInfos = ( 
      infoItems.filterNot( 
        obj => another.infoItems.toSeq.exists( 
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq ++ another.infoItems.filterNot(
        aobj => infoItems.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    )
    val sameInfos = ( infoItems.toSeq ++ another.infoItems.toSeq ).filterNot(
        obj => uniqueInfos.exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)

    val uniqueObjs = ( 
      objects.filterNot( 
        obj => another.objects.toSeq.exists( 
          aobj => aobj.path  == obj.path 
        ) 
      ).toSeq ++ 
      another.objects.filterNot(
        aobj => objects.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
    )
    val sameObjs = (objects.toSeq ++ another.objects.toSeq).filterNot(
      obj => uniqueObjs.exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)

    OdfObject(
      path, 
      sameInfos.map{
        case (path:Path, sobj: Seq[OdfInfoItem]) =>
        assert(sobj.length == 2)
        sobj.head.update(sobj.last) // assert checks
      }.toSeq ++ uniqueInfos,
      sameObjs.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.head.update(sobj.last) // assert checks
      }.toSeq ++ uniqueObjs,
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
  def get(path: Path) : Option[HasPath] ={
    val haspaths = infoItems.toSeq.map{ item => item : HasPath} ++ objects.toSeq.map{ item => item : HasPath}
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
       }
      case Some(obj: OdfObject) => Some(obj)
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
