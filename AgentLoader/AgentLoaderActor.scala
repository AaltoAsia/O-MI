package agentLoader

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
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

case class AgentDied(actor: ActorRef)
case class ReloadConfig()
case class Start()
class AgentLoaderActor extends Actor with ActorLogging {
  def receive = {
    case Start => loadAndLaunch()//Todo:listen tcp socket for config update
    case AgentDied(actor: ActorRef) =>
    case ReloadConfig => loadAndLaunch() 
  }

  var agentsToLoad : Map[String, Seq[(String,String)]] = Map.empty
  val configFile = "AgentLoaderConfig.xml"
  var agents : Map[String,ActorRef] = Map.empty
  
  def loadAndLaunch() = {
    import scala.concurrent.ExecutionContext.Implicits.global
    if(parseConfig()){
      val jars = agentsToLoad.keys
      val classLoader = new URLClassLoader(
        jars.map( k => new File(k).toURI.toURL).toArray
        ,this.getClass.getClassLoader
      )
      for(jar <- agentsToLoad){
        for(agentToCreate <- jar._2){
          if(!agents.exists{case (k,v) => k == agentToCreate }){
            val actorClass  = classLoader.loadClass(agentToCreate._1)
            val agent = context.system.actorOf(
              Props(actorClass), agentToCreate._1.replace(".","-"))
            agents += Tuple2(agentToCreate._1, agent)
            agent ! Config(agentToCreate._2)
            agent ! Start  
          }
        }
      }
    }
  }
  
  /* TODO: 
   * Create schema for config and check with it
   * Handle errors
   */
  
  def parseConfig() : Boolean  = {
    val xml : NodeSeq = XML.loadFile(configFile)
    val root = xml.head
    if(root.label != "AgentLoaderConfig" || (root \ "AgentJars").isEmpty){
      println("Mallformed config")
      return false
    }
    val jars = (root \ "AgentJars").head \ "jar"
    for(jar <- jars ){
      val path = (jar \ "@path").text
      val agents = (jar \ "agent")
      agentsToLoad += Tuple2(path, agents.map(s => ((s \ "@classname").text, (s \ "@config").text ) )) 
    }
    return true
  }
}
