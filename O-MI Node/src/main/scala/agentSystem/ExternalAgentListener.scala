package agentSystem

import akka.actor.{ Actor, Props  }
import akka.io.{ IO, Tcp  }
import akka.actor.ActorLogging
import java.net.InetSocketAddress
import scala.collection.immutable
import scala.collection.JavaConverters._
import http.Settings
import http.PermissionCheck._
import parsing.OdfParser

import types._
import types.Path._ //Useless?
import types.OdfTypes._

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
      if( hasPermission( remote.getAddress() )){
        log.info(s"Agent connected from $remote to $local")
        val handler = context.actorOf(
          Props(classOf[ExternalAgentHandler], remote),
          "agent-handler-"+agentCounter
        )
        agentCounter += 1
        connection ! Register(handler)
      } else {
        log.warning(s"Unauthorized " + remote+  " tried to connect as external agent.")
      }
  }
}

/** A handler for data received from a agent.
  * @param sourceAddress Agent's adress 
  */

class ExternalAgentHandler(
    sourceAddress: InetSocketAddress
  ) extends Actor with ActorLogging {

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
        InputPusher.handleObjects(getObjects(parsedEntries))
        if(!metaDataSaved){
          InputPusher.handlePathMetaDataPairs(
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
