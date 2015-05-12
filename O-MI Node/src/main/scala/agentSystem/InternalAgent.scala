package agentSystem

import akka.actor.{ ActorSystem, Actor, ActorRef, Props, Terminated, ActorLogging}
/** Parent trait for AgentActors. 
  *
  */
/*
abstract class InternalAgent( confgiPath : String )  extends Actor with ActorLogging{
}
*/
abstract class InternalAgent( val config : String )  extends Thread{
  
  private var running : java.lang.Boolean = true

  def loopOnce : Unit
  
  def finish : Unit

  def init : Unit
  final override def run() = {
    init

    while(isRunning){
      loopOnce

    }
    finish
  
  }
 
  final def isRunning = running.synchronized{ running }
  final def shutdown = running.synchronized{ running = false }
}
