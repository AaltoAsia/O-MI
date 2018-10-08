
package agentSystem;

import akka.actor.ActorRef;
import akka.actor.AbstractActor;
import akka.dispatch.Foreach;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.util.Timeout;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import types.OmiTypes.*;

import static akka.pattern.Patterns.ask;

public abstract class JavaInternalAgent extends AbstractActor implements InternalAgent{
  /**
   *  static public Props props(final Config _config)
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  param config Contains configuration for this agent, as given in application.conf.
   */
  // TODO: static method cannot be defined in the interface?

  protected LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  //protected Config config;

  protected ActorRef agentSystem = context().parent();
  protected ActorRef dbHandler;
  protected ActorRef requestHandler;

  protected JavaInternalAgent(ActorRef requestHandler, ActorRef dbHandler){
    this.dbHandler = dbHandler;
    this.requestHandler = requestHandler;
  }

  /**
   * Get name of the agent as was given in application.conf
   */
  protected String name(){ 
    return self().path().name();
  }


  /**
   * Wrapper for easier request execution.
   */
  final public Future<ResponseRequest> requestFromDB( OdfRequest request){
    Timeout timeout = new Timeout(request.handleTTL());
    ActorSenderInformation asi = new ActorSenderInformation( name(), self());
    OmiRequest requestWithSenderInformation = request.withSenderInformation( asi );
    Future<Object> future = ask( dbHandler, requestWithSenderInformation, timeout);
    return types.JavaHelpers.formatWriteFuture(future);
  }

  final public Future<ResponseRequest> readFromDB( ReadRequest read){ return requestFromDB(read);}
  final public Future<ResponseRequest> writeToDB( WriteRequest write){ return requestFromDB(write);}

  final public Future<ResponseRequest> requestFromNode( OdfRequest request){
    Timeout timeout = new Timeout(request.handleTTL());
    ActorSenderInformation asi = new ActorSenderInformation( name(), self());
    OmiRequest requestWithSenderInformation = request.withSenderInformation( asi );
    Future<Object> future = ask( requestHandler, requestWithSenderInformation, timeout);
    return types.JavaHelpers.formatWriteFuture(future);
  }
  /**
   * Wrapper for easier request execution.
   */
  final public Future<ResponseRequest> writeToNode( WriteRequest write, Timeout timeout ){
    return requestFromDB(write);
  }

      
  final ExecutionContext ec = context().dispatcher();
  final protected void respond(Object msg ){
    ActorRef senderRef = getSender();
    senderRef.tell(msg,getSelf());
  }
  final protected void respondFuture(Future<ResponseRequest> responseFuture ){
    ActorRef senderRef = getSender();
    responseFuture.foreach(
      new Foreach<ResponseRequest>(){
        public void each(ResponseRequest response){
          senderRef.tell(response,getSelf());
        }
      },
      ec
    ); 
  }
}
