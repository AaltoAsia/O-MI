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

import akka.actor.{Cancellable, Props, Actor}
import database.{Union, SingleStores}
import types.OdfTypes._
import types.OmiTypes.{WriteRequest, ReadRequest, OmiRequest}
import types.Path

case class AddRead(path: Path, timestamp: Long)
case class AddWrite(path: Path, timestamps: Vector[Long])
case class AddUser(path: Path, user:Option[Int], timestamp: Long)

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
                      val newDataIntervalWindow: FiniteDuration,
                      val readCountIntervalWindow: FiniteDuration,
                      val userAccessIntervalWindow: FiniteDuration,
                    //number for how long window of values we use for averaging the data
                      val readAverageCount: Int,
                      val newDataAverageCount: Int,
                      val updateFrequency: FiniteDuration) extends Actor {

  val MAX_ARRAY_LENGTH = 1024
  //start schedules
  implicit val ec = context.system.dispatcher

  var writeC: Option[Cancellable] = None
  var readC: Option[Cancellable] = None
  var userC: Option[Cancellable] = None
  override def postStop() = {
    writeC.foreach(_.cancel())
    readC.foreach(_.cancel())
    userC.foreach(_.cancel())
  }
  if(enableWriteAnalytics) {
    writeC = Some(context.system.scheduler.schedule(updateFrequency, updateFrequency)(updateWriteAnalyticsData()))
  }
  if(enableReadAnalytics) {
    readC = Some(context.system.scheduler.schedule(updateFrequency, updateFrequency)(updateReadAnalyticsData()))
  }
  if(enableUserAnalytics) {
    userC = Some(context.system.scheduler.schedule(updateFrequency, updateFrequency)(updateUserAnalyticsData()))
  }
  //context.system.scheduler.schedule()

  lazy val readAverageDescription = s"Average interval of last $readAverageCount data accesses in seconds"
  lazy val writeAverageDescription = s"Average interval of last $newDataAverageCount writes in seconds"
  lazy val readNumValueDescription = s"Amount of reads in the last ${readCountIntervalWindow.toCoarsest.toString}"
  lazy val writeNumValueDescription = s"Amount of write messages in the last ${newDataIntervalWindow.toCoarsest.toString}"
  lazy val uniqueUserDescription = s"Number of unique users in the last $userAccessIntervalWindow"

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
              Some(OdfDescription(desc)),
            None//Some(OdfMetaData(OdfTreeCollection(OdfInfoItem(path./("syntax"),OdfTreeCollection(OdfValue(desc, tt))))))
            )
          )
        )
      )
    )
  }

  def updateWriteAnalyticsData() = {
    context.system.log.info("updating write analytics")
    val tt = getCurrentTime
    val nw = numWritesInTimeWindow(tt).map{
      case (p, i) => createInfoWithMeta(p./("NumWrites"),i.toString, tt, writeNumValueDescription)}.map(_.createAncestors).reduceOption(_.union(_))
    val aw = avgIntervalWrite.map{
      case (p,i) => createInfoWithMeta(p./("freshness"), i.toString, tt, writeAverageDescription)}.map(_.createAncestors).reduceOption(_.union(_))

    (nw ++ aw).reduceOption(_.union(_)) //combine two Options and return Optional value
      .foreach(data => singleStores.hierarchyStore.execute(Union(data)))

  }
  def updateReadAnalyticsData() = {

    context.system.log.info("updating read analytics")
    val tt = getCurrentTime
    val nr = numAccessInTimeWindow(tt).map{
      case (p, i) => createInfoWithMeta(p./("NumAccess"),i.toString,tt, readNumValueDescription)}.map(_.createAncestors).reduceOption(_.union(_))

    val ar = avgIntervalAccess.map{
      case (p,i) => createInfoWithMeta(p./("popularity"), i.toString, tt, readAverageDescription)}.map(_.createAncestors).reduceOption(_.union(_))
    (nr ++ ar).reduceOption(_.union(_)) //combine two Options and return Optional value
      .foreach(data => singleStores.hierarchyStore.execute(Union(data)))
  }
  def updateUserAnalyticsData() = {
    context.system.log.info("updating user analytics")
    val tt = getCurrentTime
    val data = uniqueUsers(tt).map{
      case (p, i) => createInfoWithMeta(p./("uniqueUsers"), i.toString, tt, uniqueUserDescription)
    }.map(_.createAncestors).reduceOption(_.union(_))
    data.foreach(data=> singleStores.hierarchyStore.execute(Union(data)))
  }

  def receive = {
    case AddRead(p, t) => {
      if(enableReadAnalytics) {
        //context.system.log.debug(s"r|$p || $t")
        addRead(p, t)
      }
    }
    case AddWrite(p, t) => {
      if(enableWriteAnalytics) {
        //context.system.log.debug(s"w|$p || $t")
        addWrite(p, t)
      }
    }
    case AddUser(p, u, t) => {
      if(enableUserAnalytics) {
        //context.system.log.debug(s"u|user$u|$p||$t")
        u.foreach(addUser(p, _, t))
      }
    }
    case _ =>
  }

  val readSTM = collection.mutable.Map.empty[Path, Vector[Long]]
  val writeSTM = collection.mutable.Map.empty[Path,Vector[Long]]
  val userSTM = collection.mutable.Map.empty[Path, Vector[(Int, Long)]]

  //private val readIntervals = collection.mutable.Map.empty[Path,Vector[Long]]
  //private val writeIntervals = collection.mutable.Map.empty[Path, Vector[Long]]

  //private methods
  def getReadFrequency(path: Path): Vector[Long] = {
    readSTM.get(path).toVector.flatten
  }

  def getWriteFrequency(path: Path): Vector[Long] = {
    writeSTM.get(path).toVector.flatten
  }
  def getCurrentTime = new Date().getTime()

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

  def addUser(path: Path, user: Int, timestamp: Long) = {
    val tt = getCurrentTime - userAccessIntervalWindow.toMillis
    val temp1 = userSTM.get(path).toVector.flatten
    val temp2= temp1.filterNot(value => (value._2 < tt )||( value._1 == user))
    userSTM.put(path, temp2 :+ (user, timestamp))
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

  def uniqueUsers(currentTime:Long): Map[Path, Int] = {

    userSTM.mapValues{ values =>
      values.count(value => value._2 > (currentTime - userAccessIntervalWindow.toMillis))
    }.toMap
  }

}