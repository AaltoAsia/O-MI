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


import scala.concurrent.stm._

import types.Path


class AnalyticsStore {

  //data window in seconds
  private val dataWindow = ???

  private val readSTM = TMap.empty[Path, Vector[Long]]
  private val writeSTM = TMap.empty[Path,Vector[Long]]

  //private methods
  private def getReadFrequency(path: Path): Vector[Long] = {
    readSTM.single.get(path).toVector.flatten
  }

  private def getWriteFrequency(path: Path): Vector[Long] = {
    writeSTM.single.get(path).toVector.flatten
  }

 //public
  def addRead(path: Path, timestamp: Long) = {
    atomic{ implicit txn =>
      val updated = readSTM.get(path).toVector.flatten :+ timestamp
      readSTM.put(path, updated)
    }
  }
  def addWrite(path: Path, timestamp: Long) = {
    atomic{ implicit txn =>
      val updated = readSTM.get(path).toVector.flatten :+ timestamp
      readSTM.put(path, updated)
    }
  }





}