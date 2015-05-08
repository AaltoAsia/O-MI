package parsing

import Types._
import Types.OdfTypes._
import xmlGen._
import scala.collection.mutable.Map
import scala.util.Try
import scala.xml.XML
import java.sql.Timestamp
import java.text.SimpleDateFormat
import javax.xml.transform.stream.StreamSource
import scala.xml.Utility.trim


/** Object for parsing data in O-DF format into sequence of ParseResults. */
object OdfParser extends Parser[OdfParseResult] {

  override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("odf.xsd"))

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/

  /**
   * Public method for parsing the xml string into seq of ParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return Seq of ParseResults
   */
  def parse(xml_msg: String): OdfParseResult = {
    val root = Try(
      XML.loadString(xml_msg)
    ).getOrElse(
      return  Left( Seq( ParseError("Invalid XML") ) ) 
    )
    val schema_err = schemaValitation(root)
    if (schema_err.nonEmpty)
      return Left( schema_err )  

    val objects = scalaxb.fromXML[ObjectsType](root)
    Right(
      OdfObjects( 
        objects.Object.map{ obj => parseObject( obj ) }.toSeq,
        objects.version 
      )
    )
  }

  private def parseObject(obj: ObjectType, path: Path = Path("Objects")) :  OdfObject = { 
    val npath = path / obj.id.head.value
      OdfObject(
        npath, 
        obj.InfoItem.map{ item => parseInfoItem( item, npath ) }.toSeq,
        obj.Object.map{ child => parseObject( child, npath ) }.toSeq
      ) 
  }
  
  private def parseInfoItem(item: InfoItemType, path: Path) : OdfInfoItem  = { 
    val npath = path / item.name
      OdfInfoItem(
        npath,
        item.value.map{
          value => 
          OdfValue(
            value.value,
            value.typeValue,
            value.dateTime match {
              case None => value.unixTime match {
                case None => None
                case Some(seconds) => Some( new Timestamp(seconds/1000))
              }
              case Some(cal) => Some( new Timestamp(cal.toGregorianCalendar().getTimeInMillis()))
            }
          )
        },
        None,
        if(item.MetaData.isEmpty) None
        else Some( OdfMetaData( scalaxb.toXML[MetaData](item.MetaData.get, "MetaData", xmlGen.defaultScope).toString) )
      ) 
  }
}
