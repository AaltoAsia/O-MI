package types

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList


/** Object containing internal types used to represent O-DF formatted data
  *
  **/
object OdfTypes{

  sealed trait OdfElement

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

    def asInfoItemType: InfoItemType = {
      require(this.path.length > 1, s"OdfObject should have longer than one segment path: ${path}")
      InfoItemType(
        description = description.map( des => des.asDescription ),
        MetaData = metaData.map{ odfMetaData => odfMetaData.asMetaData},
        name = path.last, // require checks
        value = values.map{ 
          value : OdfValue =>
          value.asValueType
        }.toSeq,
        attributes = Map.empty
      )
    }
    def apply( data: ( Path, OdfValue ) ) : OdfInfoItem = OdfInfoItem(data._1, Iterable( data._2))
    def apply(path: Path, timestamp: Timestamp, value: String, valueType: String = "") : OdfInfoItem = OdfInfoItem(path, Iterable( OdfValue(value, valueType, Some(timestamp))))
  }

  case class OdfMetaData(
    data:                 String
  ) extends OdfElement {
    implicit def asMetaData : MetaData = {
      scalaxb.fromXML[MetaData]( XML.loadString( data ) )
    }
  }

  case class OdfValue(
    value:                String,
    typeValue:            String,
    timestamp:            Option[Timestamp] = None
  ) extends OdfElement {
    implicit def asValueType : ValueType = {
      ValueType(
        value,
        typeValue,
        unixTime = Some(timestamp.get.getTime/1000),
        attributes = Map.empty
      )
    }
  }

  case class OdfDescription(
    value:                String,
    lang:                 Option[String] = None
  ) extends OdfElement {
    implicit def asDescription = Description( value, lang, Map.empty)
  }
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
    def description: Option[OdfDescription]
  }
  def getHasPaths(hasPaths : Seq[HasPath]) : Seq[HasPath] ={
    hasPaths.flatMap{ 
      case info : OdfInfoItem => Seq(info)
      case obj : OdfObject => Seq( obj ) ++ getHasPaths(obj.objects.toSeq ++ obj.infoItems.toSeq)
      case objs : OdfObjects => Seq( objs ) ++ getHasPaths(objs.objects.toSeq)
    }.toSeq
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
