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

import types.OmiTypes.{WriteRequest, ReadRequest, OmiRequest}
import types.Path


object AnalyticsStore {

  //config parameters for window sizes and average window sizes
  //windows either in milliseconds or seconds
  val enableWriteAnalytics: Boolean = ???
  val enableReadAnalytics: Boolean = ???
  val enableUserAnalytics: Boolean = ???


  private val newDataIntervalWindow: Long = ???
  private val readCountIntervalWindow: Long = ???
  private val userAccessIntervalWindow: Long = ???

  if(enableWriteAnalytics || enableReadAnalytics || enableUserAnalytics) {

  }

  //integer, used for average of latest *count* values.
  private val readAverageCount: Int = ???
  private val newDataAverageCount: Int = ???

  private val readSTM = TMap.empty[Path, Vector[Long]]
  private val writeSTM = TMap.empty[Path,Vector[Long]]
  private val userSTM = TMap.empty[Long, Long]

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
  def addWrite(path: Path, timestamps: Vector[Long]) = {
    atomic{ implicit txn =>
      val updated = readSTM.get(path).toVector.flatten ++ timestamps
      readSTM.put(path, updated)
    }
  }

  def avgIntervalAccess: Map[Path, Double] = {
    readSTM.snapshot.mapValues { values =>
      val temp = values.takeRight(readAverageCount)
      if (temp.length > 1) {
        temp.tail.zip(temp.init) // calculate difference between adjacent values
         .map(a => a._1 - a._2)
         .reduceLeft(_ + _) //sum the intervals
         ./(temp.length.toDouble)
      } else 0
    }
  }

  def avgIntervalWrite: Map[Path, Double] = {
    writeSTM.snapshot.mapValues{ values =>
      val temp = values.takeRight(newDataAverageCount)
      if(temp.length > 1) {
        temp.tail.zip(temp.init)
          .map(a=> a._1 - a._2)
          .reduceLeft(_+_)
          ./(temp.length.toDouble)
      }else 0
    }
  }

  def numAccessInTimeWindow(currentTime: Long): Map[Path,Int] = {
    readSTM.snapshot.mapValues{ values =>
      values
        .filter( time => (currentTime - time) < readCountIntervalWindow)
        .length

    }
  }

  def numWritesInTimeWindow(currentTime: Long): Map[Path, Int] = {
    writeSTM.snapshot.mapValues{ values =>
      values
        .filter( time => (currentTime - time) < newDataIntervalWindow)
        .length
    }
  }

  def newRequest(req: OmiRequest, User: Long): Unit = {
    val time = System.currentTimeMillis()
    req match {
      case r: ReadRequest =>{
        r.odf.paths
      }
      case r: WriteRequest =>
      case _ =>
    }

  }

  def updateReadAnalyticsData() = {
    ???
  }
  def updateWriteAnalyticsData() = {
    ???
  }
  def updateUserAnalyticsData() = {
    ???
  }





}