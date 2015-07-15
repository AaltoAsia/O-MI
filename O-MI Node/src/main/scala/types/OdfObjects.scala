package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}

case class OdfObjects(
  objects:              JavaIterable[OdfObject] = Iterable(),
  version:              Option[String] = None
) extends OdfElement with HasPath {

  val path = Path("Objects")
  val description: Option[OdfDescription] = None
  
  def combine( another: OdfObjects ): OdfObjects ={
    val uniques : Seq[OdfObject]  = ( 
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
    val sames = ( objects.toSeq ++ another.objects.toSeq ).filterNot(
      obj => uniques.exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)
    OdfObjects(
      sames.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.head.combine(sobj.last) // assert checks
      }.toSeq ++ uniques,
      (version, another.version) match{
        case (Some(a), Some(b)) => Some(a)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      }
    )
  }
  def update( another: OdfObjects ): OdfObjects ={
    val uniques : Seq[OdfObject]  = ( 
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
    val sames = ( objects.toSeq ++ another.objects.toSeq ).filterNot(
      obj => uniques.exists(
        uobj => uobj.path == obj.path
      )
    ).groupBy(_.path)
    OdfObjects(
      sames.map{
        case (path:Path, sobj: Seq[OdfObject]) =>
        assert(sobj.length == 2)
        sobj.head.update(sobj.last) // assert checks
      }.toSeq ++ uniques,
      (version, another.version) match{
        case (Some(a), Some(b)) => Some(b)
        case (None, Some(b)) => Some(b)
        case (Some(a), None) => Some(a)
        case (None, None) => None
      }
    )
  }

  def get(path: Path) : Option[HasPath] = {
    val grouped = objects.groupBy(_.path).mapValues{_.headOption.getOrElse(OdfObjects())}
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
