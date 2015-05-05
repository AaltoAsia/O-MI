package agentSystem

import database._
import parsing.Types._
import parsing.Types.Path._
import java.util.Date
import akka.actor._

/** Trait for pushing data from sensors to db.
  *
  */
trait InputPusher {
  /** Function for handling sequences of OdfObject.
    *
    */
  def handleObjects( objs: Seq[OdfObject] ) : Unit
  /** Function for handling sequences of OdfInfoItem.
    *
    */
  def handleInfoItems( infoitems: Seq[OdfInfoItem]) : Unit  
  /** Function for handling sequences of path and value pairs.
    *
    */
  def handlePathValuePairs( pairs: Seq[(Path,TimedValue)] ): Unit
  /** Function for handling sequences of path and MetaData pairs.
    *
    */
  def handlePathMetaDataPairs( pairs: Seq[(Path,String)] ): Unit
}

// XXX: FIXME: temp hack
object InputPusher {
  def props() : Props = Props(new InputPusherForDB(new SQLiteConnection))
  var ipdb : Option[ActorRef] = None //system.actorOf(props, "input-pusher-for-db")
  def handleObjects(objs: Seq[OdfObject]) = { 
    //new InputPusherForDB(new SQLiteConnection) handleObjects _
    if(ipdb.nonEmpty)
      ipdb.get ! HandleObjects(objs) 
  }
  def handleInfoItems(items: Seq[OdfInfoItem]) = {
    //new InputPusherForDB(new SQLiteConnection) handleInfoItems _
    if(ipdb.nonEmpty)
      ipdb.get ! HandleInfoItems(items) 
  }
  def handlePathValuePairs(pairs: Seq[(Path,TimedValue)]) = {
    //new InputPusherForDB(new SQLiteConnection) handlePathValuePairs _
    if(ipdb.nonEmpty)
      ipdb.get ! HandlePathValuePairs(pairs)
  }
  def handlePathMetaDataPairs(pairs: Seq[(Path,String)]) = {
    //new InputPusherForDB(new SQLiteConnection) handlePathMetaDataPairs _
    if(ipdb.nonEmpty)
      ipdb.get ! HandlePathMetaDataPairs(pairs)
  }
}

  case class HandleObjects(objs: Seq[OdfObject])
  case class HandleInfoItems(items: Seq[OdfInfoItem])
  case class HandlePathValuePairs(pairs: Seq[(Path,TimedValue)])
  case class HandlePathMetaDataPairs(pairs: Seq[(Path,String)])
/** Creates an object for pushing data from internal agents to db.
  *
  */
class InputPusherForDB(val dbobject: DB) extends Actor with ActorLogging with InputPusher{
 

  override def receive = {
    case HandleObjects(objs) => handleObjects(objs)
    case HandleInfoItems(items) => handleInfoItems(items)
    case HandlePathValuePairs(pairs) => handlePathValuePairs(pairs)
    case HandlePathMetaDataPairs(pairs) => handlePathMetaDataPairs(pairs)
  }
  /** Function for handling sequences of OdfObject.
    *
    */
  override def handleObjects( objs: Seq[OdfObject] ) : Unit = {
    for(obj <- objs){
      if(obj.childs.nonEmpty)
        handleObjects(obj.childs)
      if(obj.sensors.nonEmpty)
        handleInfoItems(obj.sensors)
    }
    log.debug("Successfully saved Objects to DB")
  }

  /** Function for handling sequences of OdfInfoItem.
    *
    */
  override def handleInfoItems( infoitems: Seq[OdfInfoItem]) : Unit = {
    /*
     dbobject.setMany(
      infoitems.map{ info => 
        info.timedValues.map{ tv => (info.path, tv) }
      }.flatten[(Path,TimedValue)].toList 
    ) 
  */
    for( info <- infoitems ){
      for(timedValue <- info.timedValues){
          val sensorData = timedValue.time match {
              case None =>
                val currentTime = new java.sql.Timestamp(new Date().getTime())
                new DBSensor(info.path, timedValue.value, currentTime)
              case Some(timestamp) =>
                new DBSensor(info.path, timedValue.value,  timestamp)
            }
            dbobject.set(sensorData)
      }  
    }
    //*/
    log.debug("Successfully saved InfoItems to DB")
  } 
  
  /** Function for handling sequences of path and value pairs.
    *
    */
  override def handlePathValuePairs( pairs: Seq[(Path,TimedValue)] ) : Unit ={
    dbobject.setMany(pairs.toList)
    log.debug("Successfully saved Path-TimedValue pairs to DB")    
  }
  def handlePathMetaDataPairs( pairs: Seq[(Path,String)] ): Unit ={
    pairs.foreach{ pair => dbobject.setMetaData(pair._1, pair._2)}
    log.debug("Successfully saved Path-MetaData pairs to DB")    
  }

}
