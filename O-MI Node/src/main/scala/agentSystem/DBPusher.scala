package agentSystem

import database._
import types._
import types.Path._
import types.OdfTypes._
import java.util.Date
import akka.actor._
import java.lang.Iterable
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable

object InputPusherCmds {
  case class HandleOdf( objects : OdfObjects)
  case class HandleObjects(objs: Iterable[OdfObject])
  case class HandleInfoItems(items: Iterable[OdfInfoItem])
  case class HandlePathValuePairs(pairs: Iterable[(Path,OdfValue)])
  case class HandlePathMetaDataPairs(pairs: Iterable[(Path,String)])
}

import InputPusherCmds._
/** Creates an object for pushing data from internal agents to db.
  *
  */
class DBPusher(val dbobject: DB) extends Actor with ActorLogging with IInputPusher{
 

  override def receive = {
    case HandleOdf( objects ) => handleOdf( objects )
    case HandleObjects(objs) => if(objs.nonEmpty) handleObjects(objs)
    case HandleInfoItems(items) => if(items.nonEmpty) handleInfoItems(items)
    case HandlePathValuePairs(pairs) => if(pairs.nonEmpty) handlePathValuePairs(pairs)
    case HandlePathMetaDataPairs(pairs) => if(pairs.nonEmpty) handlePathMetaDataPairs(pairs)
  }
  override def handleOdf( objects: OdfObjects) : Unit = {
    val data = getLeafs(objects)
    if( data.nonEmpty){
      handleInfoItems( data.collect{ case infoitem : OdfInfoItem => infoitem } )
      log.debug("Successfully saved Odfs to DB")
      val hasPaths = getOdfNodes(objects.objects.toSeq : _ *).toSet
      val des = hasPaths.collect{
        case hPath if hPath.description.nonEmpty => hPath 
      }.toIterable
      des.foreach{ hpath => dbobject.setDescription(hpath)}
    }
  }
  /** Function for handling sequences of OdfObject.
    *
    */
  override def handleObjects( objs: Iterable[OdfObject] ) : Unit = {
    handleOdf( OdfObjects( objs ) )
  }

  /** Function for handling sequences of OdfInfoItem.
    *
    */
  override def handleInfoItems( infoitems: Iterable[OdfInfoItem]) : Unit = {
    val infos = infoitems.map{ info => 
      info.values.map{ tv => (info.path, tv) }
    }.flatten[(Path,OdfValue)].toList 
    val many = dbobject.setMany(infos) 
    log.debug("Successfully saved InfoItems to DB")
    val meta = infoitems.collect{
      case info if info.metaData.nonEmpty => 
        (info.path, info.metaData.get.data) 
    }
    if(meta.nonEmpty) handlePathMetaDataPairs(meta)
    val des = infoitems.collect{
      case info if info.description.nonEmpty => info
    }
    des.foreach{ info => dbobject.setDescription(info) }
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
