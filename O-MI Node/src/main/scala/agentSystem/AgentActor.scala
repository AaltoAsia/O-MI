package agentSystem

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
/** Parent trait for AgentActors. 
  *
  */
trait AgentActor extends Actor with ActorLogging{
}

/** Parent trait for Bootables handling of creation of an AgentActor.
  * Internal agents are modelled to be like Akka's microkernels.
  *
  */
trait Bootable {
  /** Getter function for created AgentActors.
    * @return Sequence of AgentAtors' ActorRefs.
    *
    */
  def getAgentActor : Seq[ActorRef]
  /** 
    * Callback run on AgentActor startup.
    * Create initial AgentActors here.
    * @param system ActorSystem were AgentActors will live.
    * @param configPath Path to config file of AgentActors.
    * @return Boolean indicating successfullness of startup.
    */
  def startup( system: ActorSystem, configPath: String) : Boolean 

  /** 
    * Callback run on AgentActor shutdown.
    * Shutdown actor systems here.
    */
  def shutdown(): Unit
}
