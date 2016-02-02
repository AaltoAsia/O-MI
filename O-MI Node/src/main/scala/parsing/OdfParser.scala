/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package parsing

import types._
import OmiTypes._
import OdfTypes._
import xmlGen._
import xmlGen.xmlTypes._
import scala.util.{Try, Success, Failure}
import java.util.Date
import java.io.File
import scala.util.control.NonFatal
import scala.xml.XML
import java.sql.Timestamp
import java.text.SimpleDateFormat
import javax.xml.transform.stream.StreamSource
import scala.xml.Utility.trim
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable}

/** Parser for data in O-DF format*/
object OdfParser extends Parser[OdfParseResult] {

  protected[this] override def schemaPath = new StreamSource(getClass.getClassLoader().getResourceAsStream("odf.xsd"))

  /* ParseResult is either a ParseError or an ODFNode, both defined in TypeClasses.scala*/
  /**
   * Public method for parsing the xml file into OdfParseResults.
   *
   *  @param xml_msg XML formatted file to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(file: File): OdfParseResult = {
    val root = Try(
      XML.loadFile(file)
    ) match {
      case Success(s) => s
      case Failure(f) => return Left( Iterable( ParseError(s"Invalid XML: ${f.getMessage}")))
    }

    parse(root)
  }

  /**
   * Public method for parsing the xml string into OdfParseResults.
   *
   *  @param xml_msg XML formatted string to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(xml_msg: String): OdfParseResult = {
    val root = Try(
      XML.loadString(xml_msg)
    ) match {
      case Success(s) => s
      case Failure(f) => return Left( Iterable( ParseError(s"Invalid XML: ${f.getMessage}")))
    }

    parse(root)
  }

  /**
   * Public method for parsing the xml structure into OdfParseResults.
   *
   *  @param root xml.Node to be parsed. Should be in O-DF format.
   *  @return OdfParseResults
   */
  def parse(root: xml.Node): OdfParseResult = { 
    val schema_err = schemaValidation(root)
    if (schema_err.nonEmpty) return Left(
      schema_err.map{pe : ParseError => ParseError("OdfParser: "+ pe.msg)}
    ) 

    Try{
      val objects = xmlGen.scalaxb.fromXML[ObjectsType](root)
      Right(
        OdfObjects( 
          if(objects.Object.isEmpty)
            Iterable.empty[OdfObject]
          else
            objects.Object.map{ obj => parseObject( obj ) }.toIterable,
          objects.version 
        )
      )
    } match {
      case Success(res) => res
      case Failure(e) => 
        Left( Iterable( ParseError(e + " thrown when parsed.") ) )
    }
  }

  private[this] def validateId(stringId: String): Option[String] = {
    val trimmedName = stringId.trim
    if (trimmedName.isEmpty) None
    else Some(trimmedName)
  }
  private[this] def validateId(optionId: Option[String]): Option[String] = for {
    head <- optionId
    validated <- validateId(head)
  } yield validated

  private[this] def parseObject(obj: ObjectType, path: Path = Path("Objects")) :  OdfObject = { 

    val npath = path / validateId(obj.id.headOption.map(_.value)).getOrElse(
      throw new IllegalArgumentException("No <id> on object: " + obj.id.toString)
    )

    OdfObject(
      npath, 
      obj.InfoItem.map{ item => parseInfoItem( item, npath ) }.toIterable,
      obj.Object.map{ child => parseObject( child, npath ) }.toIterable,
      obj.description.map{ des => OdfDescription( des.value, des.lang )
      }
    ) 
  }
  
  private[this] def parseInfoItem(item: InfoItemType, path: Path) : OdfInfoItem  = { 

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
          timeSolver(value)
        )
      },
      item.description.map{ des =>
        OdfDescription( des.value, des.lang ) 
      },
      if(item.MetaData.isEmpty){
        None
      } else {
        Some( OdfMetaData( scalaxb.toXML[MetaData](item.MetaData.get, Some("odf.xsd"),Some("MetaData"), xmlGen.defaultScope).toString) )
      }
    ) 
  }


  /** Resolves time used in the value (unixtime in seconds or datetime): prefers datetime if both present
   */
  private[this] def timeSolver(value: ValueType ) = value.dateTime match {
    case None => value.unixTime match {
      case None => Some(timer)
      case Some(seconds) => Some( new Timestamp(seconds.toLong * 1000))
    }
    case Some(cal) => Some( new Timestamp(cal.toGregorianCalendar().getTimeInMillis()))
  }
}
