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

package analytics

import org.prevayler.PrevaylerFactory
import types.Path

/**
 * Created by satsuma on 22.11.2016.
 */

case class ReadFrequency(data: Map[Path, Long])
case class WriteFrequency()
class AnalyticsStore {
  val writeStore = PrevaylerFactory.createTransientPrevayler(WriteFrequency())
  val readStore = PrevaylerFactory.createTransientPrevayler(ReadFrequency())
}
