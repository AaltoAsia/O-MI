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

package database

import java.sql.Timestamp

import scala.concurrent.Future

import spray.json._
import types.OdfTypes._
import types.Path

//serializer and deserializer for warp10 json formats
object Warp10JsonProtocol extends DefaultJsonProtocol{
  implicit object Warp10JsonFormat extends RootJsonFormat[OdfInfoItem] {
    private val createOdfValue: PartialFunction[JsArray, OdfValue] = {
      case JsArray(Vector(JsNumber(timestamp), JsNumber(value))) =>
        OdfValue(value.toString(), timestamp = new Timestamp((timestamp / 1000).toLong))
      case JsArray(Vector(JsNumber(timestamp), JsNumber(elev), JsNumber(value))) =>
        OdfValue(s"$elev $value", timestamp = new Timestamp((timestamp / 1000).toLong))
      case JsArray(Vector(JsNumber(timestamp), JsNumber(lat), JsNumber(lon), JsNumber(value))) =>
        OdfValue(s"$lat:$lon $value", timestamp = new Timestamp((timestamp / 1000).toLong))
      case JsArray(Vector(JsNumber(timestamp), JsNumber(lat), JsNumber(lon), JsNumber(elev), JsNumber(value))) =>
        OdfValue(s"$lat:$lon/$elev $value", timestamp = new Timestamp((timestamp / 1000).toLong))
    }


    def write(o: OdfInfoItem): JsValue = ???



    def read(v: JsValue): OdfInfoItem = v.convertTo[List[JsObject]] match {

      case first :: Nil => {
        first.getFields("c", "v") match {
          case Seq(JsString(c), JsArray(valueVectors: Vector[JsArray])) =>{
            val values: OdfTreeCollection[OdfValue] = valueVectors.collect(createOdfValue)

            OdfInfoItem(Path(c),values, None,None)
          }
        }
      }

      case first :: rest =>{
       val (path,values) = first.getFields("c", "v") match {
          case Seq(JsString(c), JsArray(valueVectors: Vector[JsArray])) =>{
            val values: OdfTreeCollection[OdfValue] = valueVectors collect(createOdfValue)

            (Path(c),values)
          }
        }
        val restValues:OdfTreeCollection[OdfValue] = rest.flatMap{ jsobj =>
          jsobj.getFields("v") match {
            case Seq(JsArray(valueVectors: Vector[JsArray])) => {
              val values: OdfTreeCollection[OdfValue] = valueVectors collect(createOdfValue)
              values
            }
          }
        }(collection.breakOut)

        OdfInfoItem(path, values ++ restValues)
      }
    }
  }
}

class Warp10Wrapper extends DB {

 def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Future[Option[OdfObjects]] = ???

 def writeMany(data: Seq[(Path, OdfValue)]): Future[Seq[(Path, Int)]] = ???

}
