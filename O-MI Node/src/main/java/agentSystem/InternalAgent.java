package agentSystem;

import akka.actor.Actor;
public interface InternalAgent extends Actor{
  
  /**
   * Method to be called when a Start() message is received.
   * @deprecated
   * Use Actor's preStart method instead. Since o-mi-node-0.8.0.
   */
  @Deprecated
  public InternalAgentResponse start();

  /**
   * Method to be called when a Restart() message is received.
   * @deprecated
   * Use Actor's preRestart and postRestart methods instead. Since o-mi-node-0.8.0.
   */
  @Deprecated
  public InternalAgentResponse restart();

  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   + @deprecated
   * Use Actor's postStop method instead. Since o-mi-node-0.8.0.
   */
  @Deprecated
  public InternalAgentResponse stop();
}
