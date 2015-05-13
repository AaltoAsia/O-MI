package agentSystem

import database._
import parsing.Types._
import parsing.Types.Path._
import parsing.Types.OdfTypes._
import java.util.Date
import akka.actor._
import java.lang.Iterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable


/** Trait for pushing data from sensors to db.
  *
  */

//trait InputPusher {
  /** Function for handling sequences of OdfObject.
    *
    */
  //def handleObjects( objs: Iterable[OdfObject] ) : Unit
  /** Function for handling sequences of OdfInfoItem.
    *
    */
  //def handleInfoItems( infoitems: Iterable[OdfInfoItem]) : Unit  
  /** Function for handling sequences of path and value pairs.
    *
    */
  //def handlePathValuePairs( pairs: Iterable[(Path,OdfValue)] ): Unit
  /** Function for handling sequences of path and MetaData pairs.
    *
    */
  //def handlePathMetaDataPairs( pairs: Iterable[(Path,String)] ): Unit
//}

/*
// XXX: FIXME: temp hack
object InputPusher extends InputPusher {
  def props() : Props = Props(new DBPusher(new SQLiteConnection))
  var ipdb : Option[ActorRef] = None //system.actorOf(props, "input-pusher-for-db")
  def handleObjects(objs: Iterable[OdfObject]) = { 
    //new InputPusherForDB(new SQLiteConnection) handleObjects _
    if(ipdb.nonEmpty)
      ipdb.get ! HandleObjects(objs) 
  }
  def handleInfoItems(items: Iterable[OdfInfoItem]) = {
    //new InputPusherForDB(new SQLiteConnection) handleInfoItems _
    if(ipdb.nonEmpty)
      ipdb.get ! HandleInfoItems(items) 
  }
  def handlePathValuePairs(pairs: Iterable[(Path,OdfValue)]) = {
    //new InputPusherForDB(new SQLiteConnection) handlePathValuePairs _
    if(ipdb.nonEmpty)
      ipdb.get ! HandlePathValuePairs(pairs)
  }
  def handlePathMetaDataPairs(pairs: Iterable[(Path,String)]) = {
    //new InputPusherForDB(new SQLiteConnection) handlePathMetaDataPairs _
    if(ipdb.nonEmpty)
      ipdb.get ! HandlePathMetaDataPairs(pairs)
  }
}*/

  case class HandleObjects(objs: Iterable[OdfObject])
  case class HandleInfoItems(items: Iterable[OdfInfoItem])
  case class HandlePathValuePairs(pairs: Iterable[(Path,OdfValue)])
  case class HandlePathMetaDataPairs(pairs: Iterable[(Path,String)])
/** Creates an object for pushing data from internal agents to db.
  *
  */
class DBPusher(val dbobject: DB) extends Actor with ActorLogging with IInputPusher{
 

  override def receive = {
    case HandleObjects(objs) => handleObjects(objs)
    case HandleInfoItems(items) => handleInfoItems(items)
    case HandlePathValuePairs(pairs) => handlePathValuePairs(pairs)
    case HandlePathMetaDataPairs(pairs) => handlePathMetaDataPairs(pairs)
  }
  /** Function for handling sequences of OdfObject.
    *
    */
  override def handleObjects( objs: Iterable[OdfObject] ) : Unit = {
    for(obj <- objs){
      if(obj.objects.nonEmpty)
        handleObjects(obj.objects)
      if(obj.infoItems.nonEmpty)
        handleInfoItems(obj.infoItems)
    }
    log.debug("Successfully saved Objects to DB")
  }

  /** Function for handling sequences of OdfInfoItem.
    *
    */
  override def handleInfoItems( infoitems: Iterable[OdfInfoItem]) : Unit = {
    
      val infos = infoitems.map{ info => 
        info.values.map{ tv => (info.path, tv) }
      }.flatten[(Path,OdfValue)].toList 
     // println(infos.mkString("\n"))
     dbobject.setMany(infos) 
  /*
    for( info <- infoitems ){
      for(value <- info.values){
          val sensorData = value.timestamp match {
              case None =>
                val currentTime = new java.sql.Timestamp(new Date().getTime())
                new DBSensor(info.path, value.value, currentTime)
              case Some(timestamp) =>
                new DBSensor(info.path, value.value,  timestamp)
            }
            dbobject.set(sensorData)
      }  
    }
    */
    log.debug("Successfully saved InfoItems to DB")
  } 
  
  /** Function for handling sequences of path and value pairs.
    *
    */
  override def handlePathValuePairs( pairs: Iterable[(Path,OdfValue)] ) : Unit ={
    dbobject.setMany(pairs.toList)
    log.debug("Successfully saved Path-TimedValue pairs to DB")    
  }
  def handlePathMetaDataPairs( pairs: Iterable[(Path,String)] ): Unit ={
    pairs.foreach{ pair => dbobject.setMetaData(pair._1, pair._2)}
    log.debug("Successfully saved Path-MetaData pairs to DB")    
  }

}
