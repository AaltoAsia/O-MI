/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2019 Aalto University.                                        +
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

package types
import java.sql.Timestamp
import akka.stream.alpakka.xml._

trait EventBuilder[T] {
  def parse(event: ParseEvent): EventBuilder[_]
  def previous: Option[EventBuilder[_]]
  def isComplete: Boolean
  def build: T
}
trait SpecialEventHandling {
  def write: omi.WriteRequest
  def valueShouldBeUpdated: (odf.Value[Any], odf.Value[Any]) => Boolean
  def triggerEvent: database.InfoItemEvent => Boolean
}
case class WithoutEvents(write: omi.WriteRequest) extends SpecialEventHandling {
  def valueShouldBeUpdated = database.SingleStores.valueShouldBeUpdated _
  def triggerEvent = {_ => false}
}

