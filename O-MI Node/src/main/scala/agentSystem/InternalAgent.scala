package agentSystem

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
/** Parent trait for AgentActors. 
  *
  */
abstract class InternalAgent( confgiPath : String )  extends Actor with ActorLogging{
}
