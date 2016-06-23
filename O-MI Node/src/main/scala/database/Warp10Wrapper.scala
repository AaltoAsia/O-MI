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


    def write(o: OdfInfoItem): JsValue = ??? //no use



    def read(v: JsValue): OdfInfoItem = v.convertTo[List[JsObject]] match {

      case first :: Nil => {
        first.getFields("c", "v") match {
          case Seq(JsString(c), JsArray(valueVectors: Vector[JsArray])) => {
            val values: OdfTreeCollection[OdfValue] = valueVectors.collect(createOdfValue)

            OdfInfoItem(Path(c),values.sortBy(_.timestamp.getTime()))
          }
        }
      }

      case first :: rest =>{
       val (path,values) = first.getFields("c", "v") match {
          case Seq(JsString(c), JsArray(valueVectors: Vector[JsArray])) => {
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

        OdfInfoItem(path, (values ++ restValues).sortBy(_.timestamp.getTime()))
      }
    }
  }
}

class Warp10Wrapper extends DB {
  /**
   * Used to get result values with given constrains in parallel if possible.
   * first the two optional timestamps, if both are given
   * search is targeted between these two times. If only start is given,all values from start time onwards are
   * targeted. Similiarly if only end is given, values before end time are targeted.
   *    Then the two Int values. Only one of these can be present. fromStart is used to select fromStart number
   * of values from the begining of the targeted area. Similiarly from ends selects fromEnd number of values from
   * the end.
   * All parameters except the first are optional, given only the first returns all requested data
   *
   * @param requests SINGLE requests in a list (leafs in request O-DF); InfoItems, Objects and MetaDatas
   * @param begin optional start Timestamp
   * @param end optional end Timestamp
   * @param newest number of values to be returned from start
   * @param oldest number of values to be returned from end
   * @return Combined results in a O-DF tree
   */
 def getNBetween(
    requests: Iterable[OdfNode],
    begin: Option[Timestamp],
    end: Option[Timestamp],
    newest: Option[Int],
    oldest: Option[Int]): Future[Option[OdfObjects]] = ???

  /**
   * Used to set many values efficiently to the database.
   * @param data list item to be added consisting of Path and OdfValue tuples.
   */
 def writeMany(data: Seq[(Path, OdfValue)]): Future[Seq[(Path, Int)]] = ???

 private def warpWriteMsg( objects : OdfObjects ) = {
   val infos = getInfoItems( objects )
   infos.map{
     info =>
       info.values.map{
         odfValue => 
           val unixEpochTime = odfValue.timestamp.getTime
           val path = info.path.mkString(".") 
           val labels = "{}"
           val value = odfValue.value
            s"$unixEpochTime// $path$labels $value\n" 
       }
   }.mkString("\n")
 }
 private def odfToReadPathSelector( objects: OdfObjects ) = {
   val leafs = getLeafs( objects)
   val paths = leafs.map{ 
     case obj : OdfObject => obj.path.mkString(".") + ".*"
     case objs : OdfObjects => objs.path.mkString(".") + ".*" 
     case info : OdfInfoItem => info.path.mkString(".") 
   }
   "(" + paths.mkString("|") + ")"
 }
 type Warp10ReadToken = String
 private def warpReadNBeforeMsg(
   pathSelector: String,
   sticks: Long,
   before: Timestamp )(implicit readToken: Warp10ReadToken ): String = {
   val epoch = before.getTime * 1000
   s"""[
   '$readToken'
   '$pathSelector'
   {}
   $epoch
   -$sticks
   ] FETCH"""

 }
 private def warpReadBetweenMsg( pathSelector: String,
   begin: Timestamp,
   end: Timestamp )(implicit readToken: Warp10ReadToken ): String = {
   val start = end.getTime * 1000
   val timespan = (end.getTime - begin.getTime) * 1000
   s"""[
   '$readToken'
   '$pathSelector'
   {}
   $start
   $timespan
   ] FETCH"""
 }
}
