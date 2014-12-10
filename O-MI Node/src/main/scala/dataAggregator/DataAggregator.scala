package dataAggregator

import akka.actor.Actor
import akka.actor.ActorRef
class DataAggregator extends Actor {

  def receive = ???
  def send(omidata:scala.xml.Elem,target:ActorRef) ={
    target ! omidata
  }
  
}