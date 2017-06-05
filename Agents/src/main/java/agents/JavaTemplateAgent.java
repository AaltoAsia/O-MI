package agents;

import akka.japi.Creator;
import akka.actor.Props;
import akka.actor.ActorRef;
import akka.dispatch.Mapper;
import akka.dispatch.OnSuccess;
import akka.dispatch.OnFailure;

import com.typesafe.config.Config;

import agentSystem.*;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.OmiResult;
import types.OmiTypes.Results;

/**
 * Template class for Java agents.
 */
public class JavaTemplateAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC FACTORY METHOD MUST EXISTS FOR JavaInternalAgent 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   *  <a href="https://github.com/typesafehub/config">Typesafe config</a>.
   */
  static public Props props(final Config config, final ActorRef requestHandler, final ActorRef dbHandler) {
    return Props.create(new Creator<JavaTemplateAgent>() {
      //Random serialVersionUID, for serialization.
      private static final long serialVersionUID = 35735155L;

      @Override
      public JavaTemplateAgent create() throws Exception {
        return new JavaTemplateAgent(config, requestHandler, dbHandler);
      }
    });
  }

  // Constructor
  public JavaTemplateAgent(Config config, final ActorRef requestHandler, final ActorRef dbHandler){
    super(requestHandler,dbHandler);
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

}
