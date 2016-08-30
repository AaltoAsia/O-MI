package agents;

import akka.japi.Creator;
import akka.actor.Props;

import com.typesafe.config.Config;

import agentSystem.*;

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
  static public Props props(final Config config) {
    return Props.create(new Creator<JavaTemplateAgent>() {
      //Random serialVersionUID, for serialization.
      private static final long serialVersionUID = 35735155L;

      @Override
      public JavaTemplateAgent create() throws Exception {
        return new JavaTemplateAgent(config);
      }
    });
  }

  // Constructor
  public JavaTemplateAgent(Config config){
  }

  /**
   * Method to be called when a Start() message is received.
   */
  @Override
  public InternalAgentResponse start(){
      return new CommandSuccessful();
  }

  /**
   * Method to be called when a Stop() message is received.
   */
  @Override
  public InternalAgentResponse stop(){
      return new CommandSuccessful();
  }
}
