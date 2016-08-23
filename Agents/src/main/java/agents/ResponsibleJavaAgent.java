package agents;

import java.lang.Exception;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Vector;
import java.sql.Timestamp;

import scala.concurrent.duration.*;
import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;
import scala.collection.immutable.HashMap;
import scala.collection.JavaConverters.*;
import scala.util.*;
import akka.actor.Props;
import akka.actor.ActorRef;
import akka.util.Timeout;
import static akka.pattern.Patterns.ask;
import akka.japi.Creator;
import akka.dispatch.Mapper;
import akka.dispatch.OnSuccess;
import akka.dispatch.OnFailure;
import akka.actor.Cancellable;

import com.typesafe.config.Config;

import agentSystem.JavaInternalAgent; 
import agentSystem.ResponsibilityRequest;
import agentSystem.*;
import types.Path;
import types.OmiTypes.*;
import types.OdfTypes.OdfValue;
import types.OdfTypes.*;
import types.OdfFactory;
import types.OmiFactory;
import types.OdfTypes.OdfInfoItem;

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 */
public class ResponsibleJavaAgent extends JavaAgent implements ResponsibleInternalAgent {
  /**
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   */
  static public Props props(final Config _config) {
    return Props.create(new Creator<ResponsibleJavaAgent>() {
      private static final long serialVersionUID = 355173L;

      @Override
      public ResponsibleJavaAgent create() throws Exception {
        return new ResponsibleJavaAgent(_config);
      }
    });
  }
  
  // Constructor
  public ResponsibleJavaAgent(Config conf) {
    super(conf);
  }

  @Override
  public void handleWrite(WriteRequest write) {
    Timeout timeout = new Timeout( write.handleTTL() );
    ActorRef senderRef = getSender();
    Future<ResponsibleAgentResponse> future = writeToNode(write, timeout);

    ExecutionContext ec = context().system().dispatcher();
    future.onSuccess(new ForwardResult(getSelf(),senderRef), ec);
    future.onFailure(new FailureWrite(getSelf(),senderRef,write), ec);
  }

  // Contains function for the asynchronous handling of write result
  protected final class ForwardResult extends OnSuccess<ResponsibleAgentResponse> {
    private ActorRef orginalSender;
    private ActorRef self;
    public ForwardResult(ActorRef _self, ActorRef _orginalSender){
      orginalSender = _orginalSender;
      self = _self;
    } 
    @Override 
    public final void onSuccess(ResponsibleAgentResponse result) {
      orginalSender.tell(result, self);
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
      ArrayList<Throwable > ts = new ArrayList<Throwable>();
      ts.add(t);
      FailedWrite fw = new FailedWrite( write.odf().paths(), OdfTreeCollection.fromJava( ts ));
      orginalSender.tell(fw, self);
    }
  }

  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
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

    }  else if( message instanceof WriteRequest ){
      WriteRequest write = (WriteRequest) message;
      handleWrite(write);
    } else if( message instanceof String) {
      String str = (String) message;
      if( str.equals("Update"))
        update();

    } else unhandled(message);
  }
}
