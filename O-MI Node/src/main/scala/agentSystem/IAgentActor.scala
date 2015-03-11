package agentSystem

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
//Agents should have their own config files
case class Config(path:String)
trait IAgentActor extends Actor with ActorLogging{
}
trait Bootable {
  def getAgentActor : ActorRef
  /** 
    * Callback run on microkernel startup.
    * Create initial actors and messages here.
    */
  def startup( system: ActorSystem, agentListener: ActorRef, configPath: String) : Boolean 

  /** 
    * Callback run on microkernel shutdown.
    * Shutdown actor systems here.
    */
  def shutdown(): Unit
}
