package agents

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}

case class Start(seqpath:Seq[String], address : String, port: Int)
abstract trait IAgentActor extends Actor with ActorLogging{


}
