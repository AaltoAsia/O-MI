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

import java.util.Date

import org.prevayler.{Query, Transaction, PrevaylerFactory}
import types.Path

/**
 * Created by satsuma on 22.11.2016.
 */

//Storage case classes

/**
 * Storage class for the access frequency data for the items in the O-DF tree
 * @param data Map from value paths to the access timestamps in milliseconds since epoch,
 *             length tells us the number of accesses and from timestamps we can calculate the average interval
 */
case class ReadFrequency(var data: Map[Path, Vector[Long]])
object ReadFrequency {
  def empty = ReadFrequency(Map.empty())
}

/**
 * Storage class for the write frequency data for the items in the O-DF tree
 * @param data Map from the value paths to the write timestamps in milliseconds since epoch,
 *             length tells us the number of writes and from timestamps we can calculate the average interval
 */
case class WriteFrequency(var data: Map[Path,Vector[Long]])
object WriteFrequency {
  def empty = WriteFrequency(Map.empty())
}







//Prevaylers
class AnalyticsStore {
  val writeStore = PrevaylerFactory.createTransientPrevayler(WriteFrequency.empty)
  val readStore = PrevaylerFactory.createTransientPrevayler(ReadFrequency.empty)
}







//Operations

//update
case class NewWrite(path: Path, timestamp: Long) extends Transaction[WriteFrequency] {
  def executeOn(p: WriteFrequency, d: Date) = {
    val newVal: Vector[Long] = p.data.get(path).fold(Vector(timestamp))(old => old :+ timestamp)
    p.data = p.data.updated(path, newVal)
  }
}
case class NewRead(path: Path, timestamp: Long) extends Transaction[ReadFrequency] {
  def executeOn(p: ReadFrequency, d: Date) = {
    val newVal: Vector[Long] = p.data.get(path).fold(Vector(timestamp))(old => old :+ timestamp)
    p.data = p.data.updated(path, newVal)
  }
}

//read
case class GetReadFrequency(path: Path) extends Query[ReadFrequency, Vector[Long]] {
  def query(p: ReadFrequency, d: Date): Vector[Long] = {
    p.data.get(path).getOrElse(Vector.empty)
  }
}
case class GetWriteFrequency(path: Path) extends Query[WriteFrequency, Vector[Long]] {
  def query(p:WriteFrequency, d: Date): Vector[Long] = {
    p.data.get(path).getOrElse(Vector.empty)
  }
}

