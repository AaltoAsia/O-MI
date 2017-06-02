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
import akka.dispatch.*;
import akka.dispatch.OnSuccess;
import akka.dispatch.OnFailure;
import akka.actor.Cancellable;

import com.typesafe.config.Config;

import agentSystem.JavaInternalAgent; 
import agentSystem.*;
import types.Path;
import types.OmiTypes.*;
import types.OdfTypes.OdfValue;
import types.OdfTypes.*;
import types.OdfFactory;
import types.OmiFactory;
import types.OmiTypes.OmiResult;
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
  static public Props props(final Config _config, final ActorRef requestHandler, final ActorRef dbHandler) {
    return Props.create(new Creator<ResponsibleJavaAgent>() {
      private static final long serialVersionUID = 355173L;

      @Override
      public ResponsibleJavaAgent create() throws Exception {
        return new ResponsibleJavaAgent(_config, requestHandler, dbHandler);
      }
    });
  }
  
  // Constructor
  public ResponsibleJavaAgent(
      Config conf,
      final ActorRef requestHandler, 
      final ActorRef dbHandler) {
    super(conf, requestHandler, dbHandler);
  }

  @Override
  public Future<ResponseRequest> handleWrite(WriteRequest write) {
    
    Future<ResponseRequest> future = writeToDB(write);

    ExecutionContext ec = context().system().dispatcher();
    future.onSuccess(new LogResult(), ec);
    future.onFailure(new LogFailure(), ec);
    return future;
  }

  // Contains function for the asynchronous handling of write result
  public final class LogResult extends OnSuccess<ResponseRequest> {
      @Override public final void onSuccess(ResponseRequest response) {
        Iterable<OmiResult> results = response.resultsAsJava() ;
        for( OmiResult result : results ){
          if( result instanceof Results.Success ){
            // This sends debug log message to O-MI Node logs if
            // debug level is enabled (in logback.xml and application.conf)
            log.debug(name() + " wrote paths successfully.");
          } else {
            log.warning(
                "Something went wrong when " + name() + " writed, " + result.toString()
                );
          }
        }
      }
  }
  // Contains function for the asynchronous handling of write failure
  public final class LogFailure extends OnFailure{
      @Override public final void onFailure(Throwable t) {
          log.warning(
            name() + "'s write future failed, error: " + t.getMessage()
          );
      }
  }

  @Override
  public Future<ResponseRequest> handleCall(CallRequest call){
    return Futures.successful( 
        Responses.NotImplemented(Duration.apply(10,TimeUnit.SECONDS))
    );
  };

  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public void onReceive(Object message){
    if( message instanceof WriteRequest ){
      WriteRequest write = (WriteRequest) message;
      respondFuture(handleWrite(write));
    }  else if( message instanceof CallRequest ){

      CallRequest call = (CallRequest) message;
      respondFuture(handleCall(call));

    } else if( message instanceof String) {
      String str = (String) message;
      if( str.equals("Update"))
        update();
      else super.onReceive(message);
    } else super.onReceive(message);
  }
}
