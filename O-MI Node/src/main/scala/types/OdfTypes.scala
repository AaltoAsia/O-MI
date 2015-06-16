package types

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList


object OdfTypes{

  sealed trait OdfElement

  case class OdfObjects(
    objects:              JavaIterable[OdfObject] = Iterable(),
    version:              Option[String] = None
  ) extends OdfElement with HasPath {
    val path = Path("Objects")
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
      val sames = (objects.toSeq ++ another.objects.toSeq).filterNot(
        obj => uniques.exists(
          uobj => uobj.path == obj.path
        )
      ).groupBy(_.path)
      OdfObjects(
        sames.map{ case (path:Path, sobj: Seq[OdfObject]) =>
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
  }

  def OdfObjectsAsObjectsType( objects: OdfObjects ) : ObjectsType ={
    ObjectsType(
      Object = objects.objects.map{
        obj: OdfObject => 
        OdfObjectAsObjectType( obj )
      }.toSeq,
      objects.version
    )
  }
  
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
      ).toSeq ++ 
      another.infoItems.filterNot(
        aobj => infoItems.toSeq.exists(
          obj => aobj.path  == obj.path
        )
      ).toSeq
      )
      val sameInfos = (infoItems.toSeq ++ another.infoItems.toSeq).filterNot(
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
        sameInfos.map{ case (path:Path, sobj: Seq[OdfInfoItem]) =>
          assert(sobj.length == 2)
          sobj.head.combine(sobj.last) // assert checks
        }.toSeq ++ uniqueInfos,
        sameObjs.map{ case (path:Path, sobj: Seq[OdfObject]) =>
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
  }

  def OdfObjectAsObjectType(obj: OdfObject) : ObjectType = {
    require(obj.path.length > 1, s"OdfObject should have longer than one segment path: ${obj.path}")
    ObjectType(
      Seq( QlmID(
          obj.path.last, // require checks (also in OdfObject)
          attributes = Map.empty
      )),
      InfoItem = obj.infoItems.map{ 
        info: OdfInfoItem =>
          OdfInfoItemAsInfoItemType( info )
        }.toSeq,
      Object = obj.objects.map{ 
        subobj: OdfObject =>
        OdfObjectAsObjectType( subobj )
      }.toSeq,
      attributes = Map.empty
    )
  }

  case class OdfInfoItem(
    path:                 Path,
    values:               JavaIterable[OdfValue],
    description:          Option[OdfDescription] = None,
    metaData:             Option[OdfMetaData] = None
  ) extends OdfElement with HasPath {
    def combine(another: OdfInfoItem) : OdfInfoItem ={
      assert(path == another.path)
      OdfInfoItem(
        path,
        (values ++ another.values).toSeq.distinct,
        (description, another.description) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        },
        (metaData, another.metaData) match{
          case (Some(a), Some(b)) => Some(a)
          case (None, Some(b)) => Some(b)
          case (Some(a), None) => Some(a)
          case (None, None) => None
        }
      )
    }
  }

  def OdfInfoItemAsInfoItemType(info: OdfInfoItem) : InfoItemType = {
    require(info.path.length > 1, s"OdfObject should have longer than one segment path: ${info.path}")
    InfoItemType(
      name = info.path.last, // require checks
      value = info.values.map{ 
        value : OdfValue =>
        ValueType(
          value.value,
          value.typeValue,
          unixTime = Some(value.timestamp.get.getTime/1000),
          attributes = Map.empty
        )
      },
      MetaData = 
        if(info.metaData.nonEmpty)
          Some( scalaxb.fromXML[MetaData]( XML.loadString( info.metaData.get.data ) ) )
        else 
          None
      ,
      attributes = Map.empty
    )
  }
  case class OdfMetaData(
    data:                 String
  ) extends OdfElement

  case class OdfValue(
    value:                String,
    typeValue:            String = "",
    timestamp:            Option[Timestamp] = None
  ) extends OdfElement

  case class OdfDescription(
    value:                String,
    lang:                 Option[String] = None
  ) extends OdfElement
  
  type  OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]
  def getObjects( odf: OdfParseResult ) : JavaIterable[OdfObject] = 
    odf match{
      case Right(objs: OdfObjects) => objs.objects
      case _ => Iterable()
    }
  def getErrors( odf: OdfParseResult ) : JavaIterable[ParseError] = 
    odf match {
      case Left(pes: JavaIterable[ParseError]) => pes
      case _ => Iterable()
    }

  def getLeafs(objects: OdfObjects ) : JavaIterable[HasPath] = {
    def getLeafs(obj: OdfObject ) : JavaIterable[HasPath] = {
      if(obj.infoItems.isEmpty && obj.objects.isEmpty)
        Iterable(obj)
      else 
        obj.infoItems ++ obj.objects.flatMap{          
          subobj =>
            getLeafs(subobj)
        } 
    }
    if (objects.objects.nonEmpty)
      objects.objects.flatMap{
        obj => getLeafs(obj)
      }
    else Iterable(objects)
  }
  sealed trait HasPath {
    def path: Path
  }
  
  /**
   * Generates odf tree containing the ancestors of given object.
   */
  @annotation.tailrec
  def fromPath( last: HasPath) : OdfObjects = {

    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject( parentPath, Iterable(info), Iterable() )  
        fromPath(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects( Iterable(obj) )
        else {
          val parent = OdfObject( parentPath, Iterable(), Iterable(obj) )  
          fromPath(parent)
        }

      case objs: OdfObjects =>
        objs
    }
  }
}
