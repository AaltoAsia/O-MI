
package agentSystem;
import akka.actor.UntypedActor;
import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.typesafe.config.Config;

import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;
import akka.dispatch.Foreach;
import akka.util.Timeout;

import static akka.pattern.Patterns.ask;
import agentSystem.InternalAgent;
import types.OmiTypes.OmiRequest;
import types.OmiTypes.OdfRequest;
import types.OmiTypes.WriteRequest;
import types.OmiTypes.ReadRequest;
import types.OmiTypes.ActorSenderInformation;
import types.OmiTypes.ResponseRequest;

public abstract class JavaInternalAgent extends UntypedActor implements InternalAgent{
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
  };


  /**
   * Wrapper for easier request execution.
   */
  final public Future<ResponseRequest> requestFromDB( OdfRequest request){
    Timeout timeout = new Timeout(request.handleTTL());
    ActorSenderInformation asi = new ActorSenderInformation( name(), self());
    OmiRequest requestWithSenderInformation = request.withSenderInformation( asi );
    Future<Object> future = ask( dbHandler, requestWithSenderInformation, timeout);
    Future<ResponseRequest> responseFuture = types.JavaHelpers.formatWriteFuture(future);
    return responseFuture;
  }

  final public Future<ResponseRequest> readFromDB( ReadRequest read){ return requestFromDB(read);}
  final public Future<ResponseRequest> writeToDB( WriteRequest write){ return requestFromDB(write);}

  final public Future<ResponseRequest> requestFromNode( OdfRequest request){
    Timeout timeout = new Timeout(request.handleTTL());
    ActorSenderInformation asi = new ActorSenderInformation( name(), self());
    OmiRequest requestWithSenderInformation = request.withSenderInformation( asi );
    Future<Object> future = ask( requestHandler, requestWithSenderInformation, timeout);
    Future<ResponseRequest> responseFuture = types.JavaHelpers.formatWriteFuture(future);
    return responseFuture;
  }
  /**
   * Wrapper for easier request execution.
   */
  final public Future<ResponseRequest> writeToNode( WriteRequest write, Timeout timeout ){
    return requestFromDB(write);
  }

  @Override
  public void onReceive(Object message) {
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
