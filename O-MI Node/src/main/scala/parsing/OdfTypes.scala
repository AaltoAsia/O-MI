package parsing
package Types

import java.sql.Timestamp
import java.lang.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.seqAsJavaList


object OdfTypes{

  sealed trait OdfElement

  case class OdfObjects(
    objects:              Iterable[OdfObject] = asJavaIterable(Seq.empty[OdfObject]),
    version:              Option[String] = None
  ) extends OdfElement

  case class OdfObject(
    path:                 Path,
    infoItems:            Iterable[OdfInfoItem],
    objects:              Iterable[OdfObject],
    desctription:         Option[OdfDesctription] = None,
    typeValue:            Option[String] = None
  ) extends OdfElement

  case class OdfInfoItem(
    path:                 Types.Path,
    values:               Iterable[OdfValue],
    desctription:         Option[OdfDesctription] = None,
    metaData:             Option[OdfMetaData] = None
  ) extends OdfElement

  case class OdfMetaData(
    data:                 String
  ) extends OdfElement

  case class OdfValue(
    value:                String,
    typeValue:            String = "",
    timestamp:            Option[Timestamp] = None
  ) extends OdfElement

  case class OdfDesctription(
    value:                String,
    lang:                 Option[String]
  ) extends OdfElement
  
  type  OdfParseResult = Either[Iterable[ParseError], OdfObjects]
  def getObjects( odf: OdfParseResult ) : Iterable[OdfObject] = 
    odf match{
      case Right(objs: OdfObjects) => objs.objects
      case _ => asJavaIterable(Seq.empty[OdfObject])
    }
  def getErrors( odf: OdfParseResult ) : Iterable[ParseError] = 
    odf match {
      case Left(pes: Iterable[ParseError]) => pes
      case _ => asJavaIterable(Seq.empty[ParseError])
    }

}
