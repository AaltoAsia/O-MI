package agentSystemInterface

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import akka.actor.ActorLogging
import java.net.InetSocketAddress
import java.util.Date
import java.text.SimpleDateFormat

import sensorDataStructure.{SensorMap, SensorData}
import parsing.OdfParser


class AgentListener(dataStore: SensorMap) extends Actor with ActorLogging {
   
  import Tcp._
   
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
        Props(classOf[InputDataHandler], remote, dataStore),
        "agent-handler"
      )
      connection ! Register(handler)
  }
}


class InputDataHandler(
    sourceAddress: InetSocketAddress,
    dataStore: SensorMap
  ) extends Actor with ActorLogging {

  import Tcp._

  // timestamp format to use when data doesn't have its own
  val dateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss")

  def receive = {
    case Received(data) => 
      val dataString = data.decodeString("UTF-8")

      log.debug("Got data \n" + dataString)

      val parsedEntries = OdfParser.parse(dataString)


      for (parsed <- parsedEntries) {
        parsed match {

          case Right(
            parsing.ODFNode(
              path,
              parsing.InfoItem,
              Some(value),
              oTime,
              _  // TODO: FIXME: don't ignore metadata
            )
          ) =>
            val pathfix = if (path.startsWith("/")) path.tail else path
            val sensorData = oTime match {
              case Some(time) => new SensorData(pathfix, value, time)
              case None =>
                val currentTime = dateFormat.format(new Date())
                new SensorData(pathfix, value, currentTime)
            }
            log.debug(s"Saving to path $pathfix")

            dataStore.set(pathfix, sensorData)

          case Left(error) => 
            log.warning(s"Malformed odf received from agent ${sender()}: ${error.msg}")

          case Right(node: parsing.ODFNode) =>
            log.warning("Throwing away node: " + node)
        }
      }
    case PeerClosed =>
      log.info(s"Agent disconnected from $sourceAddress")
      context stop self
  }
}





