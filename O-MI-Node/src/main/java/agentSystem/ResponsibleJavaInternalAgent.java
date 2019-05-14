package agentSystem;

import akka.actor.ActorRef;
import akka.dispatch.Futures;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.util.Timeout;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import types.OmiTypes.CallRequest;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.Responses;
import types.OmiTypes.WriteRequest;
import types.OmiTypes.ReadRequest;
import types.OmiTypes.DeleteRequest;

import java.util.concurrent.TimeUnit;

public abstract class ResponsibleJavaInternalAgent extends JavaInternalAgent implements ResponsibleInternalAgent {

  protected ResponsibleJavaInternalAgent(ActorRef requestHandler, ActorRef dbHandler){
    super(requestHandler, dbHandler);
  }
  public Future<ResponseRequest> handleWrite(WriteRequest write){
    return writeToDB(write);
  }
  public Future<ResponseRequest> handleRead(ReadRequest read){
    return readFromDB(read);
  }

  public Future<ResponseRequest> handleDelete(DeleteRequest delete){
    return requestFromDB(delete);
  }
  public Future<ResponseRequest> handleCall(CallRequest call){
    return Futures.successful( 
        Responses.NotImplemented(Duration.apply(10,TimeUnit.SECONDS))
    );
  }
  
  @Override
  public Receive createReceive(){
    return receiveBuilder()
      .match( WriteRequest.class, write -> respondFuture(handleWrite(write)))
      .match( CallRequest.class, call -> respondFuture(handleCall(call)))
      .build().orElse(super.createReceive());
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
