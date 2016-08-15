
package agentSystem;
import akka.actor.UntypedActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;

import agentSystem.ResponsibilityRequest;
import scala.concurrent.Future;
import akka.util.Timeout;
import static akka.pattern.Patterns.ask;
import agentSystem.InternalAgent;
import types.OmiTypes.WriteRequest;

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

  final public Future<ResponsibleAgentResponse> writeToNode( WriteRequest write, Timeout timeout ){
    ResponsibilityRequest rw = new ResponsibilityRequest(name, write);
    Future<Object> future = ask( agentSystem,rw, timeout);
    Future<ResponsibleAgentResponse> result = types.OmiTypes.JavaHelpers.formatWriteFuture(future);
    return result;
  }
}
