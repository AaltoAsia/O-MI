package agentLoader

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated }
import akka.io.{ IO, Tcp }
import akka.util.ByteString
import java.net.InetSocketAddress
import xml._
import io._
import scala.concurrent.duration._
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.ActorLogging
import java.net.URLClassLoader
import java.io.File
import agents._
import scala.collection.mutable.Map

/** Simple main object to launch agent's actors 
**/
object GenericMain {
/** Simple main function to launch agent's actors 
  * @param arguments for connecting AgentListener and sensor's path.
**/

  def main(args: Array[String]) = {

    if(args.length < 3){
      println("arguments are <address> <port> <path of this sensor>")

    } else {
      var path = args(2).split("/").filterNot(_.isEmpty)
      
      if(path.head == "Objects")
        path = path.tail

      implicit val timeout = Timeout(5.seconds)
      implicit val system = ActorSystem("on-generic-agent")
      
      val classLoader = new URLClassLoader(
        Array(
          new File("../Agents/target/scala-2.11/agents_2.11-0.1-SNAPSHOT.jar").toURI.toURL
        ),this.getClass.getClassLoader
      )
      val myClass = classLoader.loadClass("agents.GenericAgent")
      val agent = system.actorOf(
        Props(myClass), "generic-agent")
      agent ! Config(args.mkString(" ")) 
    }
  }
}
