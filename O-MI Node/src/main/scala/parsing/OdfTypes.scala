package parsing
package Types

import xmlGen._
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
  ) extends OdfElement {
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
          sobj.head.combine(sobj.last)
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
          sobj.head.combine(sobj.last)
        }.toSeq ++ uniqueInfos,
        sameObjs.map{ case (path:Path, sobj: Seq[OdfObject]) =>
          assert(sobj.length == 2)
          sobj.head.combine(sobj.last)
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
    ObjectType(
      Seq( QlmID(
          obj.path.last,
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
    path:                 Types.Path,
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
    InfoItemType(
      name = info.path.last,
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
    objects.objects.flatMap{
      obj => getLeafs(obj)
    }
  }
  trait HasPath {
    def path: Path
  }
  
  def fromPath( last: HasPath) : OdfObjects = {
    var parentPath = last.path.dropRight(1)
    var obj = last match {
      case info: OdfInfoItem =>
        OdfObject( parentPath, Iterable(info), Iterable() )  
      case obj: OdfObject =>
        OdfObject( parentPath, Iterable(), Iterable(obj) )  
    }
    while( parentPath.length > 1){
      parentPath = parentPath.dropRight(1)
      obj = OdfObject( parentPath, Iterable(), Iterable(obj) )
    }
    OdfObjects( Iterable(obj) )
  }
}
