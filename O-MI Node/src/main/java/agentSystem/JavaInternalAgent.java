
package agentSystem;
import akka.actor.UntypedActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;

import agentSystem.InternalAgent;
public abstract class JavaInternalAgent extends UntypedActor implements InternalAgent {
  protected LoggingAdapter log = Logging.getLogger(getContext().system(), this);
  protected Config config;
  protected ActorRef agentSystem = context().parent();
  protected String name = self().path().name();
  @Override
  public InternalAgentSuccess restart()throws StartFailed, CommandFailed {
    stop();
    return start();
  }
}
