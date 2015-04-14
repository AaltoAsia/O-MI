package agentSystem

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import akka.actor.ActorLogging
import java.net.InetSocketAddress


import parsing.OdfParser
import database.SQLiteConnection


import parsing.Types._
import parsing.Types.Path._

/** AgentListener handles connections from agents.
  */
class AgentListener extends Actor with ActorLogging {
   
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
        Props(classOf[InputDataHandler], remote),
        "agent-handler-"+agentCounter
      )
      agentCounter += 1
      connection ! Register(handler)
  }

}

/** A handler for data received from a agent.
  * @param Agent's adress 
  */

class InputDataHandler(
    sourceAddress: InetSocketAddress
  ) extends Actor with ActorLogging {

  val inputPusher = new InputPusherForDB(new SQLiteConnection)

  import Tcp._

  private var metaDataSaved: Boolean = false
  /** Partial function for handling received messages.
    */
  def receive = {
    case Received(data) => 
      val dataString = data.decodeString("UTF-8")

      log.debug(s"Got data from $sender")

      val parsedEntries = OdfParser.parse(dataString)
      val errors = parsedEntries.filter( _.isLeft ).map( e => e.left.get) 
      val corrects = parsedEntries.filter( _.isRight ).map( c => c.right.get) 

      for (error <- errors) {
        log.warning(s"Malformed odf received from agent ${sender()}: ${error.msg}")
      }

     inputPusher.handleObjects(corrects)
     if(!metaDataSaved){
     inputPusher.handlePathMetaDataPairs(
       corrects.flatten{
         o =>
           o.sensors ++ getSensors(o.childs)
         }.filter{
          info => info.metadata.nonEmpty 
         }.map{
          info  => (info.path, info.metadata.get.data)
         }   

      )
    metaDataSaved = true
    }


    case PeerClosed =>
      log.info(s"Agent disconnected from $sourceAddress")
      context stop self
  }
  def getSensors(o:Seq[OdfObject]) : Seq[OdfInfoItem] = { 
    o.flatten{ o =>
    o.sensors ++ getSensors(o.childs)
  }   
}
}
