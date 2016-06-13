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
package parsing

import java.io.File
import java.sql.Timestamp
import javax.xml.transform.stream.StreamSource

import scala.collection.JavaConversions.asJavaIterable
import scala.util.{Failure, Success, Try}
import scala.xml.Elem

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import types.OdfTypes.OdfTreeCollection.seqToOdfTreeCollection
import types.OdfTypes._
import types._

/** Parser for data in O-DF format*/
object OdfParser extends Parser[OdfParseResult] {
  val schemaName = "odf.xsd"
  protected[this] override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream(schemaName))

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  /**
   * Public method for parsing the xml file into OdfParseResults.
   *
   *  @param file XML formatted file to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(file: File): OdfParseResult = {
    val parsed = Try(
      XMLParser.loadFile(file)
    )
    parseTry(parsed)

  }

  /**
   * Public method for parsing the xml string into OdfParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(xml_msg: String): OdfParseResult = {
    val parsed = Try(
      XMLParser.loadString(xml_msg)
    )

    parseTry(parsed)
  }

  private def parseTry(parsed: Try[Elem]): OdfParseResult = {
    parsed match {
      case Success(root) => parse(root)
      case Failure(f) => Left(Iterable(ParseError(s"Invalid XML: ${f.getMessage}")))
    }
  }

  /**
   * Public method for parsing the xml structure into OdfParseResults.
   *
   *  @param root xml.Node to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(root: xml.Node): OdfParseResult = { 
    schemaValidation(root) match {
      case errors : Seq[ParseError] if errors.nonEmpty => Left(
        errors.map{pe : ParseError => ParseError("OdfParser: "+ pe.msg)}
      ) 
      case empty : Seq[ParseError] if empty.isEmpty =>

      val requestProcessTime = currentTime

      Try{
        val objects = xmlGen.scalaxb.fromXML[ObjectsType](root)
        Right(
          OdfObjects( 
            if(objects.Object.isEmpty)
              Iterable.empty[OdfObject]
            else
              objects.Object.map{ obj => parseObject( requestProcessTime, obj ) }.toIterable,
            objects.version
          ))
      } match {
        case Success(res) => res
        case Failure(e) => Left( Iterable( ParseError(e + " thrown when parsed.") ) )
      }
    }
  }

  private[this] def validateId(stringId: String): Option[String] = {
    stringId.trim match{ 
      case "" => None 
      case trimmedName: String => Some(trimmedName)
    }
  }
  private[this] def validateId(optionId: Option[String]): Option[String] = for {
    head <- optionId
    validated <- validateId(head)
  } yield validated

  private[this] def parseObject(requestProcessTime: Timestamp, obj: ObjectType, path: Path = Path("Objects")) :  OdfObject = { 

    val npath = path / validateId(obj.id.headOption.map(_.value)).getOrElse(
      throw new IllegalArgumentException("No <id> on object: " + obj.id.toString)
    )

    OdfObject(
      obj.id,
      npath, 
      obj.InfoItem.map{ item => parseInfoItem( requestProcessTime, item, npath ) }.toIterable,
      obj.Object.map{ child => parseObject( requestProcessTime, child, npath ) }.toIterable,
      obj.description.map{ des => OdfDescription( des.value, des.lang )},
      obj.typeValue
    ) 
  }
  
  private[this] def parseInfoItem(requestProcessTime: Timestamp, item: InfoItemType, path: Path) : OdfInfoItem  = { 

    // TODO: support many names from item.otherName
    val npath = path / validateId(item.name).getOrElse(
      throw new IllegalArgumentException("No name on infoItem")
    )

    OdfInfoItem(
      npath,
      item.value.map{
        value => 
        OdfValue(
          value.value,
          value.typeValue,
          timeSolver(value, requestProcessTime)
        )
      },
      item.description.map{ des =>
        OdfDescription( des.value, des.lang ) 
      },
      item.MetaData.map{ meta =>
        // tests that conversion works before it is in the db and fails when in read request
        OdfMetaData( scalaxb.toXML[MetaData](meta, Some(schemaName),Some("MetaData"), xmlGen.defaultScope).toString)
      }
    ) 
  }


  /** Resolves time used in the value (unixtime in seconds or datetime): prefers datetime if both present
   */
  private[this] def timeSolver(value: ValueType, requestProcessTime: Timestamp) = value.dateTime match {
    case None => value.unixTime match {
      case None => requestProcessTime
      case Some(seconds) => new Timestamp(seconds.toLong * 1000)
    }
    case Some(cal) => new Timestamp(cal.toGregorianCalendar().getTimeInMillis())
  }
}
