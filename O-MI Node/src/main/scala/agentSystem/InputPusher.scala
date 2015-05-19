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
     dbobject.setMany(infos) 
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
