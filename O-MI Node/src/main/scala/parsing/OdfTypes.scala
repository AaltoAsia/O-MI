package parsing
package Types

import java.sql.Timestamp

object OdfTypes{

  sealed trait OdfElement

  case class OdfObjects(
    objects:              Seq[OdfObject] = Seq.empty,
    version:              Option[String] = None
  ) extends OdfElement

  case class OdfObject(
    path:                 Path,
    infoItems:            Seq[OdfInfoItem],
    objects:              Seq[OdfObject],
    desctription:         Option[OdfDesctription] = None,
    typeValue:            Option[String] = None
  ) extends OdfElement

  case class OdfInfoItem(
    path:                 Types.Path,
    values:               Seq[OdfValue],
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
  
  type  OdfParseResult = Either[Seq[ParseError], OdfObjects]
  def getObjects( odf: OdfParseResult ) : Seq[OdfObject] = 
    odf match{
      case Right(objs: OdfObjects) => objs.objects
      case _ => Seq.empty
    }
  def getErrors( odf: OdfParseResult ) : Seq[ParseError] = 
    odf match {
      case Left(pes: Seq[ParseError]) => pes
      case _ => Seq.empty
    }

}
