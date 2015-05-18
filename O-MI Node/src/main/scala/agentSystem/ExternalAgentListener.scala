package agentSystem

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import akka.actor.ActorLogging
import java.net.InetSocketAddress


import parsing.OdfParser
import parsing.OmiParser
import database.SQLiteConnection


import parsing.Types._
import parsing.Types.Path._
import parsing.Types.OdfTypes._
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable

/** AgentListener handles connections from agents.
  */
class ExternalAgentListener extends Actor with ActorLogging {
   
  import Tcp._
  //Orginally a hack for getting different names for actors.
  private var agentCounter : Int = 0 
  /** Get function for count of all ever connected agents.
    * Check that can't be modified via this.
    */
  def agentCount = agentCounter
  /** Partial function for handling received messages.
    */
  def receive = {
    case Bound(localAddress) =>
      // TODO: do something?
      // It seems that this branch was not executed?
   
    case CommandFailed(b: Bind) =>
      log.warning(s"Agent connection failed: $b")
      context stop self
   
    case Connected(remote, local) =>
      val connection = sender()
      log.info(s"Agent connected from $remote to $local")

      val handler = context.actorOf(
        Props(classOf[ExternalAgentHandler], remote),
        "agent-handler-"+agentCounter
      )
      agentCounter += 1
      connection ! Register(handler)
  }

}

/** A handler for data received from a agent.
  * @param sourceAddress Agent's adress 
  */

class ExternalAgentHandler(
    sourceAddress: InetSocketAddress
  ) extends Actor with ActorLogging {

  val inputPusher = new DBPusher(new SQLiteConnection)

  import Tcp._

  private var metaDataSaved: Boolean = false
  /** Partial function for handling received messages.
    */
  def receive = {
    case Received(data) =>{ 
      val dataString = data.decodeString("UTF-8")

      log.debug(s"Got data from $sender")
      val parsedEntries = OdfParser.parse(dataString)
      val errors = getErrors(parsedEntries)
      if(errors.nonEmpty){
        log.warning(s"Malformed odf received from agent ${sender()}: ${errors.mkString("\n")}")
        
      } else {
        inputPusher.handleObjects(getObjects(parsedEntries))
        if(!metaDataSaved){
          inputPusher.handlePathMetaDataPairs(
            getInfoItems(getObjects(parsedEntries)).filter{
              info => info.metaData.nonEmpty 
            }.map{
              info  => (info.path, info.metaData.get.data)
            }   

          )
          metaDataSaved = true
        }
      }
  }
  case PeerClosed =>{
    log.info(s"Agent disconnected from $sourceAddress")
    context stop self
  }
  }
  
  /**
   * Recursively gets all sensors from given objects
   * @param o Sequence of OdfObjects to process
   * @return Sequence of OdfInfoitems(sensors)
   */
  def getInfoItems(o:Iterable[OdfObject]) : Iterable[OdfInfoItem] = { 
    o.flatten{ o =>
    o.infoItems ++ getInfoItems(o.objects)
  }   
}
}
