package agentSystem;

import akka.actor.Actor;
public interface InternalAgent extends Actor{
  
  /**
   * Method to be called when a Start() message is received.
   */
  public InternalAgentResponse start();

  /**
   * Method to be called when a Restart() message is received.
   */
  public InternalAgentResponse restart();

  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   */
  public InternalAgentResponse stop();
}
