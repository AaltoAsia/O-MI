package agentSystem;

import akka.actor.Actor;
public interface InternalAgent extends Actor{
  
  public InternalAgentSuccess start()throws StartFailed;
  public InternalAgentSuccess restart()throws StartFailed, CommandFailed ;
  public InternalAgentSuccess stop()throws CommandFailed ;
}
