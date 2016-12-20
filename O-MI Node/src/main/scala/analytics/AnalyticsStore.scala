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


import java.sql.Timestamp
import java.util.Date

import scala.concurrent.duration.FiniteDuration

import akka.actor.{Props, Actor}
import database.{Union, SingleStores}
import types.OdfTypes.{OdfValue, OdfTreeCollection, OdfMetaData, OdfInfoItem}
import types.OmiTypes.{WriteRequest, ReadRequest, OmiRequest}
import types.Path

case class AddRead(path: Path, timestamp: Long)
case class AddWrite(path: Path, timestamps: Vector[Long])
object AnalyticsStore {
  def props(
            singleStores: SingleStores,
             enableWriteAnalytics: Boolean,
            enableReadAnalytics: Boolean, 
            enableUserAnalytics: Boolean,
            newDataIntervalWindow: FiniteDuration, 
            readCountIntervalWindow: FiniteDuration, 
            userAccessIntervalWindow: FiniteDuration, 
            readAverageCount: Int, 
            newDataAverageCount: Int,
            updateFrequency: FiniteDuration): Props = {
    Props(
      new AnalyticsStore(
        singleStores,
        enableWriteAnalytics,
        enableReadAnalytics,
        enableUserAnalytics,
        newDataIntervalWindow,
        readCountIntervalWindow,
        userAccessIntervalWindow,
        readAverageCount,
        newDataAverageCount,
        updateFrequency
      )
    )
  }
}
class AnalyticsStore(
                      val singleStores: SingleStores,
                      val enableWriteAnalytics: Boolean,
                      val enableReadAnalytics: Boolean,
                      val enableUserAnalytics: Boolean,
                    //config parameters for window sizes and average window sizes
                      private val newDataIntervalWindow: FiniteDuration,
                      private val readCountIntervalWindow: FiniteDuration,
                      private val userAccessIntervalWindow: FiniteDuration,
                    //number for how long window of values we use for averaging the data
                      private val readAverageCount: Int,
                      private val newDataAverageCount: Int,
                      private val updateFrequency: FiniteDuration) extends Actor {

  private val MAX_ARRAY_LENGTH = 30
  //start schedules
  implicit val ec = context.system.dispatcher
  if(enableWriteAnalytics) context.system.scheduler.schedule(updateFrequency,updateFrequency)(updateWriteAnalyticsData())
  if(enableReadAnalytics) context.system.scheduler.schedule(updateFrequency,updateFrequency)(updateReadAnalyticsData())
  if(enableUserAnalytics) context.system.scheduler.schedule(updateFrequency,updateFrequency)(updateUserAnalyticsData())
  //context.system.scheduler.schedule()

  lazy val readAverageDescription = s"Average interval of last $readAverageCount data accesses in seconds"
  lazy val writeAverageDescription = s"Average interval of last $newDataAverageCount writes in seconds"
  lazy val readNumValueDescription = s"Amount of reads in the last ${readCountIntervalWindow.toCoarsest.toString}"
  lazy val writeNumValueDescription = s"Amount of write messages in the last ${newDataIntervalWindow.toCoarsest.toString}"

  def createInfoWithMeta(path: Path, value: String, timestamp: Long, desc: String): OdfInfoItem = {
    val tt = new Timestamp(timestamp)
    OdfInfoItem(
      path.init, //parent path
      Vector.empty,
      None,
      Some(
        OdfMetaData(
          OdfTreeCollection(
            OdfInfoItem(
              path,
              OdfTreeCollection(
                OdfValue(value, tt)
              ),
              None,
            Some(OdfMetaData(OdfTreeCollection(OdfInfoItem(path./("syntax"),OdfTreeCollection(OdfValue(desc, tt))))))
            )
          )
        )
      )
    )
  }

  def updateWriteAnalyticsData() = {
    context.system.log.info("updating write analytics")
    val tt = new Date().getTime()
    val nw = numWritesInTimeWindow(tt).map{
      case (p, i) => createInfoWithMeta(p./("NumWrites"),i.toString, tt, writeNumValueDescription)}.map(_.createAncestors).reduceOption(_.union(_))
    val aw = avgIntervalWrite.map{
      case (p,i) => createInfoWithMeta(p./("freshness"), i.toString, tt, writeAverageDescription)}.map(_.createAncestors).reduceOption(_.union(_))

    (nw ++ aw).reduceOption(_.union(_)) //combine two Options and return Optional value
      .foreach(data => singleStores.hierarchyStore.execute(Union(data)))

  }
  def updateReadAnalyticsData() = {
    context.system.log.info("updating read analytics")
    val tt = new Date().getTime()
    val nr = numAccessInTimeWindow(tt).map{
      case (p, i) => createInfoWithMeta(p./("NumAccess"),i.toString,tt, readNumValueDescription)}.map(_.createAncestors).reduceOption(_.union(_))

    val ar = avgIntervalAccess.map{
      case (p,i) => createInfoWithMeta(p./("popularity"), i.toString, tt, readAverageDescription)}.map(_.createAncestors).reduceOption(_.union(_))
    (nr ++ ar).reduceOption(_.union(_)) //combine two Options and return Optional value
      .foreach(data => singleStores.hierarchyStore.execute(Union(data)))
  }
  def updateUserAnalyticsData() = ???

  def receive = {
    case AddRead(p, t) => {
      context.system.log.info(s"r|$p || $t")
      addRead(p, t)
    }
    case AddWrite(p, t) => {
      context.system.log.info(s"w|$p || $t")
      addWrite(p, t)
    }
    case _ =>
  }

  private val readSTM = collection.mutable.Map.empty[Path, Vector[Long]]
  private val writeSTM = collection.mutable.Map.empty[Path,Vector[Long]]
  private val userSTM = collection.mutable.Map.empty[Long, Long]
  private val readIntervals = collection.mutable.Map.empty[Path,Vector[Long]]
  private val writeIntervals = collection.mutable.Map.empty[Path, Vector[Long]]

  //private methods
  private def getReadFrequency(path: Path): Vector[Long] = {
    readSTM.get(path).toVector.flatten
  }

  private def getWriteFrequency(path: Path): Vector[Long] = {
    writeSTM.get(path).toVector.flatten
  }

 //public
  def addRead(path: Path, timestamp: Long) = {
   val temp = readSTM.get(path).toVector.flatten
   if((temp.length+1) > MAX_ARRAY_LENGTH){
     readSTM.put(path, (temp :+ timestamp).tail)
   } else {
     readSTM.put(path, temp :+ timestamp)
   }
   //addReadInterval(path,timestamp)
  }

  def addWrite(path: Path, timestamps: Vector[Long]) = { //requires timestamps to be in order
    val temp = writeSTM.get(path).toVector.flatten
    val len = temp.length + timestamps.length
    if(len > MAX_ARRAY_LENGTH){
      writeSTM.put(path, (temp ++ timestamps).drop(len - MAX_ARRAY_LENGTH))
    } else{
      writeSTM.put(path, temp ++ timestamps)
    }
  }

  /*
  // Last value is timestamp of the previous value if found
  def addReadInterval(path: Path, timestamp: Long) = {
    val temp = readIntervals.get(path).toVector.flatten
    if(temp.isEmpty) {
      readIntervals.put(path, Vector(timestamp))
    } else {
      val updated = (temp.init :+ (timestamp - temp.last)) :+ timestamp

      if(updated.length <= readAverageCount) {
        readIntervals.put(path,updated)
      } else {
        readIntervals.put(path, updated.tail)
      }
    }
  }
  def addWriteInterval(path: Path, timestamps: Vector[Long]) = {
    val temp = writeIntervals.get(path).toVector.flatten
    
  }
*/
  def avgIntervalAccess: Map[Path, Double] = {
    readSTM.mapValues { values =>
      val temp = values.takeRight(readAverageCount).sorted
      if (temp.length > 1) {
        temp.tail.zip(temp.init) // calculate difference between adjacent values
         .map(a => a._1 - a._2)
         .reduceLeft(_ + _) //sum the intervals
         ./((temp.length - 1) * 1000.0) //convert to seconds
      } else 0
    }.toMap
  }

  def avgIntervalWrite: Map[Path, Double] = {
    writeSTM.mapValues{ values =>
      val temp = values.takeRight(newDataAverageCount).sorted
      if(temp.length > 1) {
        temp.tail.zip(temp.init)
          .map(a=> a._1 - a._2)
          .reduceLeft(_+_)
          ./((temp.length - 1) * 1000.0) //convert to seconds
      } else 0
    }.toMap
  }

  def numAccessInTimeWindow(currentTime: Long): Map[Path,Int] = {
    readSTM.mapValues{ values =>
      values.count(time => (currentTime - time) < readCountIntervalWindow.toMillis)
    }.toMap
  }

  def numWritesInTimeWindow(currentTime: Long): Map[Path, Int] = {
    writeSTM.mapValues{ values =>
      values.count(time => (currentTime - time) < newDataIntervalWindow.toMillis)
    }.toMap
  }

}