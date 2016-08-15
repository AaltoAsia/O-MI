package agentSystem;

import akka.actor.Actor;
public interface InternalAgent extends Actor{
  
  public InternalAgentResponse start()throws StartFailed;
  public InternalAgentResponse restart()throws StartFailed, CommandFailed ;
  public InternalAgentResponse stop()throws CommandFailed ;
}
