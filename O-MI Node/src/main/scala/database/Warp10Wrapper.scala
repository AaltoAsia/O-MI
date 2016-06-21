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

import spray.json.{JsValue, RootJsonFormat, DefaultJsonProtocol}
import types.OdfTypes.{OdfNode, OdfObjects, OdfValue}
import types.Path

//serializer and deserializer for warp10 json formats
object Warp10JsonProtocol extends DefaultJsonProtocol{
  implicit object Warp10JsonFormat extends RootJsonFormat[OdfObjects] {
    def write(o: OdfObjects): JsValue = ???
    def read(v: JsValue): OdfObjects = ???
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
