package agents

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
//Agents should have their own config files
case class Config(path:String)
abstract trait IAgentActor extends Actor with ActorLogging{


}
