package agentSystem

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import akka.actor.ActorLogging
import java.net.InetSocketAddress
import java.net.InetAddress

import scala.collection.mutable.Map
import scala.collection.immutable
import scala.collection.JavaConverters._
import http.Settings
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
  
  import http.Boot.settings
  val whiteIPs = settings.externalAgentIps.asScala.map{
    case s: String => 
    val ip = inetAddrToBytes(InetAddress.getByName(s)) 
    log.debug("IPv" + ip.length + " : " + ip.mkString(".")) 
    ip
  }.toArray 
  log.debug("Totally " + whiteIPs.length + "IPs")
  val whiteMasks = settings.externalAgentSubnets.unwrapped().asScala.map{ 
    case (s: String, bits: Object ) => 
    val ip = inetAddrToBytes(InetAddress.getByName(s)) 
    log.debug("Mask IPv" + ip.length + " : " + ip.mkString(".")) 
    (ip, bits.toString.toInt )
  }.toMap 
  log.debug("Totally " + whiteMasks.keys.size + "masks")
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
      if(whiteIPs.contains( inetAddrToBytes(remote.getAddress()) ) ||
        whiteMasks.exists{ case (subnet : Array[Byte], bits : Int) =>
         isInSubnet(subnet, bits, inetAddrToBytes(remote.getAddress()))
        }
      ){
        log.info(s"Agent connected from $remote to $local")
        val handler = context.actorOf(
          Props(classOf[ExternalAgentHandler], remote),
          "agent-handler-"+agentCounter
        )
        agentCounter += 1
        connection ! Register(handler)
      } else {
        log.warning(s"Unauthorized " + inetAddrToBytes(remote.getAddress).mkString(":") + " tried to connect as external agent.")
      }
  }

  def inetAddrToBytes(addr: InetAddress) : Array[Byte] = {
    addr.getAddress()
  }
  def isInSubnet(subnet: Array[Byte], bits: Int, ip: Array[Byte]) : Boolean = {
    if( subnet.length == ip.length){
      log.debug("Whitelist check for IPv" + ip.length + " address: " + ip.mkString(":") )
      ip.length match{
        case 4 =>{
          val mask = -1 << (32 - bits)  
          (bytesToInt(subnet) & mask) == (bytesToInt(ip) & mask)
        }
        case 16 =>{
          val mask = if( bits > 56 )
            Array[Byte]( 0xFF.toByte, (0xFF << ( 64 - bits)).toByte)
          else 
            Array[Byte]( (0xFF << ( 56 - bits)).toByte , 0x00.toByte )

          subnet(0) == ip(0) && 
          subnet(1) == ip(1) &&
          subnet(2) == ip(2) && 
          subnet(3) == ip(3) &&
          subnet(4) == ip(4) && 
          subnet(5) == ip(5) &&
          (subnet(6) & mask(0) )== (ip(6) & mask(0)) && 
          (subnet(7) & mask(1) )== (ip(7) & mask(1))
        }
      }
    }
    log.debug("Tried to compare IPv4 with IPv6, address: " + ip.mkString(":") )
    false
  }
  def bytesToInt(bytes: Array[Byte]) : Int = {
    val ip : Int = ((bytes(0) & 0xFF) << 24) |
      ((bytes(1) & 0xFF) << 16) |
      ((bytes(2) & 0xFF) << 8)  |
      ((bytes(3) & 0xFF) << 0);
    ip
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
