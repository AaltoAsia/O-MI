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
package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}
import http.Boot.settings

/** Class implementing OdfInfoItem. */
class  OdfInfoItemImpl(
  path:                 Path,
  values:               JavaIterable[OdfValue] = Iterable(),
  description:          Option[OdfDescription] = None,
  metaData:             Option[OdfMetaData] = None
){

  /** Method for combining two OdfInfoItems with same path */
  def combine(another: OdfInfoItem) : OdfInfoItem ={
    require(path == another.path, "Should have same paths")
    OdfInfoItem(
      path,
      (values ++ another.values).toSeq,
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

  /** Method to convert to scalaxb generated class. */
  implicit def asInfoItemType: InfoItemType = {
    InfoItemType(
      description = description.map( des => des.asDescription ),
      MetaData = metaData.map{ odfMetaData => odfMetaData.asMetaData},
      name = path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: ${path}")),
      value = values.map{ 
        value : OdfValue =>
        value.asValueType
      }.toSeq,
      attributes = Map.empty
    )
  }

}

/** Class presenting MetaData structure of O-DF format. */
case class OdfMetaData(
  data:                 String
) {
  /** Method to convert to scalaxb generated class. */
  implicit def asMetaData : MetaData = {
    scalaxb.fromXML[MetaData]( XML.loadString( data ) )
  }
}

/** Class presenting Value tag of O-DF format. */
case class OdfValue(
  value:                String,
  typeValue:            String,
  timestamp:            Timestamp
) {
  /** Method to convert to scalaxb generated class. */
  implicit def asValueType : ValueType = {
    ValueType(
      value,
      typeValue,
      unixTime = Some(timestamp.getTime/1000),
      attributes = Map.empty
    )
  }
}
