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
import akka.actor.{Actor, Cancellable, Props}
import database.{SingleStores, Union}
import http.OmiConfigExtension
import types.odf._
import types.OmiTypes.{OmiRequest, ReadRequest, WriteRequest}
import types.Path

import scala.concurrent.ExecutionContextExecutor

case class AddRead(path: Path, timestamp: Long)
case class AddWrite(path: Path, timestamps: Vector[Long])
case class AddUser(path: Path, user:Option[Int], timestamp: Long)

object AnalyticsStore {
  def props(
            singleStores: SingleStores,
            settings: OmiConfigExtension): Props = {
    Props(
      new AnalyticsStore(
        singleStores,
      settings)
    )
  }
}
class AnalyticsStore(
                      val singleStores: SingleStores,
                    settings: OmiConfigExtension
                    //config parameters for window sizes and average window sizes
                    //number for how long window of values we use for averaging the data
                      ) extends Actor {

  val enableWriteAnalytics: Boolean = settings.enableWriteAnalytics
  val enableReadAnalytics: Boolean = settings.enableReadAnalytics
  val enableUserAnalytics: Boolean = settings.enableUserAnalytics
  val newDataIntervalWindow: FiniteDuration = settings.numWriteSampleWindowLength
  val readCountIntervalWindow: FiniteDuration = settings.numReadSampleWindowLength
  val userAccessIntervalWindow: FiniteDuration = settings.numUniqueUserSampleWindowLength
  val readAverageCount: Int = settings.readAvgIntervalSampleSize
  val newDataAverageCount: Int = settings.writeAvgIntervalSampleSize
  val updateFrequency: FiniteDuration = settings.updateInterval
  val averageWriteInfoName: String = settings.averageWriteInfoName
  val averageReadInfoName: String = settings.averageReadIAnfoName
  val numWriteInfoName: String = settings.numberWritesInfoName
  val numReadInfoName: String = settings.numberReadsInfoName
  val numUserInfoName: String = settings.numberUsersInfoName
  val MAX_ARRAY_LENGTH: Int = settings.analyticsMaxHistoryLength
  //start schedules
  implicit val ec: ExecutionContextExecutor = context.system.dispatcher

  var writeC: Option[Cancellable] = None
  var readC: Option[Cancellable] = None
  var userC: Option[Cancellable] = None
  override def postStop(): Unit = {
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

  def createInfoWithMeta(path: Path, value: String, timestamp: Long, desc: String): InfoItem = {
    val tt = new Timestamp(timestamp)
    InfoItem(
      path.init.last,
      path.init, //parent path
      metaData = Some(
        MetaData(
          Vector(
            InfoItem(
              path.last,
              path,
              values = Vector(
                Value(value, tt)
              ),
              descriptions = Vector(Description(desc))
              //None//Some(MetaData(Vector(InfoItem(path./("syntax"),Vector(Value(desc, tt))))))
            )
          )
        )
      )
    )
  }

  def updateWriteAnalyticsData(): Unit = {
    context.system.log.info("updating write analytics")
    val tt = currentTime
    val nw = numWritesInTimeWindow(tt).collect{
      case (path:Path,value:Int) if path.length > 2 => 
        createInfoWithMeta(path./(numWriteInfoName),value.toString, tt, writeNumValueDescription)
    }
    val aw = avgIntervalWrite.collect{
      case (path:Path,value:Double)  if path.length > 2 => 
        createInfoWithMeta(path./(averageWriteInfoName), value.toString, tt, writeAverageDescription)
    }
    val data = ImmutableODF(aw ++ nw)

    singleStores.hierarchyStore.execute(Union(data))

  }
  def updateReadAnalyticsData(): Unit = {

    context.system.log.info("updating read analytics")
    val tt = currentTime
    val nr = numAccessInTimeWindow(tt).collect{
      case (path:Path,value:Int) if path.length > 2 => 
        createInfoWithMeta(path./(numReadInfoName),value.toString,tt, readNumValueDescription)
    }//.map(_.createAncestors).reduceOption(_.union(_))

    val ar = avgIntervalAccess.collect{
      case (path:Path,value:Double) if path.length > 2 => 
         createInfoWithMeta(path./(averageReadInfoName), value.toString, tt, readAverageDescription)
    }//.map(_.createAncestors).reduceOption(_.union(_))
    val data = ImmutableODF(nr ++ ar)
    singleStores.hierarchyStore.execute(Union(data))
    //(nr ++ ar).reduceOption(_.union(_)) //combine two Options and return Optional value
    //  .foreach(data => singleStores.hierarchyStore.execute(Union(data)))
  }
  def updateUserAnalyticsData(): Unit = {
    context.system.log.info("updating user analytics")
    val tt = currentTime
    val uua = uniqueUsers(tt).collect{
      case (path:Path,value:Int) if path.length > 2 => 
        createInfoWithMeta(path./(numUserInfoName), value.toString, tt, uniqueUserDescription)
    }//.map(_.createAncestors).reduceOption(_.union(_))
    //data.foreach(data=> singleStores.hierarchyStore.execute(Union(data)))
    val data = ImmutableODF(uua)
    singleStores.hierarchyStore.execute(Union(data))
  }

  def receive: PartialFunction[Any, Unit] = {
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
  def readFrequency(path: Path): Vector[Long] = {
    readSTM.get(path).toVector.flatten
  }

  def writeFrequency(path: Path): Vector[Long] = {
    writeSTM.get(path).toVector.flatten
  }
  def currentTime: Long = new Date().getTime()

 //public
  def addRead(path: Path, timestamp: Long): Option[Vector[Long]] = {
   val temp = readSTM.get(path).toVector.flatten
   if((temp.length+1) > MAX_ARRAY_LENGTH){
     readSTM.put(path, (temp :+ timestamp).tail)
   } else {
     readSTM.put(path, temp :+ timestamp)
   }
   //addReadInterval(path,timestamp)
  }

  def addWrite(path: Path, timestamps: Vector[Long]): Option[Vector[Long]] = { //requires timestamps to be in order
    val temp = writeSTM.get(path).toVector.flatten
    val len = temp.length + timestamps.length
    if(len > MAX_ARRAY_LENGTH){
      writeSTM.put(path, (temp ++ timestamps).drop(len - MAX_ARRAY_LENGTH))
    } else{
      writeSTM.put(path, temp ++ timestamps)
    }
  }

  def addUser(path: Path, user: Int, timestamp: Long): Option[Vector[(Int, Long)]] = {
    val tt = currentTime - userAccessIntervalWindow.toMillis
    val temp1: Vector[(Int, Long)] = userSTM.get(path).toVector.flatten
    val temp2: Vector[(Int, Long)] = temp1.filterNot{case (foundUser, _timestamp) => (_timestamp < tt) || (foundUser == user)}//(value => (value._2 < tt )||( value._1 == user))
    userSTM.put(path, temp2.:+((user, timestamp): (Int,Long)))
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
          .sum //sum the intervals
         ./((temp.length - 1) * 1000.0) //convert to seconds
      } else 0
    }.toMap
  }

  def avgIntervalWrite: Map[Path, Double] = {
    writeSTM.mapValues{ values =>
      val temp = values.takeRight(newDataAverageCount).sorted
      if(temp.length > 1) {
        temp.tail.zip(temp.init)
          .map(a => a._1 - a._2)
          .sum
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
