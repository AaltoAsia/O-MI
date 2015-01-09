package agentSystemInterface

import akka.actor.{ Actor, ActorRef, Props  }
import akka.io.{ IO, Tcp  }
import akka.util.ByteString
import java.util.Date
import java.text.SimpleDateFormat

import sensorDataStructure.{SensorMap, SensorData}
import parsing.OdfParser


class AgentListener(dataStore: SensorMap) extends Actor {
   
  import Tcp._
   
  def receive = {
    case Bound(localAddress) =>
      // TODO: do some logging? or setup?
      println(s"Agent connected from $localAddress")
   
    case CommandFailed(_: Bind) => context stop self
   
    case Connected(remote, local) =>
      val connection = sender()
      val handler = context.actorOf(
        Props(classOf[InputDataHandler], connection, dataStore)
      )
      connection ! Register(handler)
  }
}


class InputDataHandler(connection: ActorRef, dataStore: SensorMap) extends Actor {
  import Tcp._

  val formatDate = new SimpleDateFormat ("yyyy-MM-dd'T'hh:mm:ss")

  def receive = {
    case Received(data) => 
      val dataString = data.decodeString("UTF-8")
      println(s"Got data $dataString")

      val parsedEntries = OdfParser.parse(dataString)


      for (parsed <- parsedEntries) {
        parsed match {

          case Right(
            parsing.ODFNode(
              path,
              parsing.InfoItem,
              Some(value),
              oTime,
              _  // metadata
            )
          ) =>
            val pathfix = if (path.startsWith("/")) path.tail else path
            val sensorData = oTime match {
              case Some(time) => new SensorData(pathfix, value, time)
              case None => new SensorData(pathfix, value, formatDate.format(new Date()))
            }
            println(s"Saving to path $pathfix")
            dataStore.set(pathfix, sensorData)

          case Left(error) => 
            println(s"Warning: Malformed odf received from agent ${sender()}: ${error.msg}")

          case Right(node: parsing.ODFNode) =>
            println("Warning: Throwing away node: " + node)
        }
      }
    case PeerClosed => context stop self
  }
}





