package agentSystem;

import java.util.concurrent.TimeUnit;


import akka.util.Timeout;
import akka.dispatch.*;
import akka.dispatch.OnFailure;
import static akka.pattern.Patterns.ask;

import akka.actor.ActorRef;

import scala.concurrent.duration.Duration;
import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;

import types.OmiTypes.WriteRequest;
import types.OmiTypes.CallRequest;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.Responses;

public abstract class ResponsibleJavaInternalAgent extends JavaInternalAgent implements ResponsibleInternalAgent {

  protected ResponsibleJavaInternalAgent(ActorRef requestHandler, ActorRef dbHandler){
    super(requestHandler, dbHandler);
  }
  public Future<ResponseRequest> handleWrite(WriteRequest write){
    return writeToDB(write);
  }

    public Future<ResponseRequest> handleCall(CallRequest call){
    return Futures.successful( 
        Responses.NotImplemented(Duration.apply(10,TimeUnit.SECONDS))
    );
  }

    //public abstract void handleCall(CallRequest call);
  @Override
  public void onReceive(Object message) {
    if( message instanceof WriteRequest ){
      WriteRequest write = (WriteRequest) message;
      respondFuture(handleWrite(write));
    } else if( message instanceof CallRequest ){
      CallRequest call = (CallRequest) message;
      respondFuture(handleCall(call));
    } else unhandled(message);
  }
  
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
    private ActorRef originalSender;
    private ActorRef self;
    public ForwardResult(ActorRef _self, ActorRef _originalSender){
      originalSender = _originalSender;
      self = _self;
    } 
    @Override 
    public final void onSuccess(ResponseRequest response) {
      originalSender.tell(response, self);
    }
  }

  protected final class FailureWrite extends OnFailure{
    private ActorRef originalSender;
    private ActorRef self;
    private WriteRequest write;
    public FailureWrite(ActorRef _self, ActorRef _originalSender, WriteRequest _write){
      originalSender = _originalSender;
      self = _self;
      write = _write;
    } 
    @Override 
    public final void onFailure(Throwable t) {
      ResponseRequest fw = Responses.InternalError(t);
      originalSender.tell(fw, self);
    }
  }

}
