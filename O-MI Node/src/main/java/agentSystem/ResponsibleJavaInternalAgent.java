package agentSystem;

import java.util.ArrayList;

import akka.util.Timeout;
import akka.dispatch.OnSuccess;
import akka.dispatch.OnFailure;
import static akka.pattern.Patterns.ask;
import akka.actor.UntypedActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import com.typesafe.config.Config;

import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;

import agentSystem.ResponsibilityRequest;
import types.OmiTypes.WriteRequest;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.Responses;
import types.OdfTypes.OdfTreeCollection;

public abstract class ResponsibleJavaInternalAgent extends JavaInternalAgent implements ResponsibleInternalAgent {

  public abstract void handleWrite(WriteRequest write);
  
  final protected void passWrite(WriteRequest write){
    Timeout timeout = new Timeout( write.handleTTL() );
    ActorRef senderRef = getSender();
    Future<ResponseRequest> future = writeToNode(write, timeout);

    ExecutionContext ec = context().system().dispatcher();
    future.onSuccess(new ForwardResult(getSelf(),senderRef), ec);
    future.onFailure(new FailureWrite(getSelf(),senderRef,write), ec);
  }

  // Contains function for the asynchronous handling of write result
  protected final class ForwardResult extends OnSuccess<ResponseRequest> {
    private ActorRef orginalSender;
    private ActorRef self;
    public ForwardResult(ActorRef _self, ActorRef _orginalSender){
      orginalSender = _orginalSender;
      self = _self;
    } 
    @Override 
    public final void onSuccess(ResponseRequest response) {
      orginalSender.tell(response, self);
    }
  }

  protected final class FailureWrite extends OnFailure{
    private ActorRef orginalSender;
    private ActorRef self;
    private WriteRequest write;
    public FailureWrite(ActorRef _self, ActorRef _orginalSender, WriteRequest _write){
      orginalSender = _orginalSender;
      self = _self;
      write = _write;
    } 
    @Override 
    public final void onFailure(Throwable t) {
      ResponseRequest fw = Responses.InternalError(t);
      orginalSender.tell(fw, self);
    }
  }

  @Override
  public void onReceive(Object message) throws StartFailed, CommandFailed {
    if( message instanceof Start) {
      // Start is received when this agent should start it's functionality
      getSender().tell(start(),getSelf());

    } else if( message instanceof Stop) {
      // Stop is received when this agent should stop it's functionality
      getSender().tell(stop(),getSelf());

    } else if( message instanceof Restart) {
      // Restart is received when this agent should restart
      // default behaviour is to call stop() and then start()
      getSender().tell(restart(),getSelf());
    } else if( message instanceof WriteRequest ){
      WriteRequest write = (WriteRequest) message;
      handleWrite(write);
    } else unhandled(message);
  }
}
