package agentSystem;

import com.typesafe.config.Config;
import akka.actor.ActorRef;
import akka.actor.Props;

class JavaTestAgent extends JavaInternalAgent{
  protected JavaTestAgent(ActorRef requestHandler, ActorRef dbHandler){
    super(requestHandler,dbHandler);
  }
  public static Props props(Config conf,ActorRef requestHandler, ActorRef dbHandler){
    return Props.create(JavaTestAgent.class, () -> new JavaTestAgent(requestHandler, dbHandler));
  }
} 

class JavaWrongPropsAgent extends JavaInternalAgent{
  protected JavaWrongPropsAgent(ActorRef requestHandler, ActorRef dbHandler){
    super(requestHandler,dbHandler);
  }
  public static Props props(Config conf,ActorRef requestHandler, ActorRef dbHandler){
    return Props.create(JavaTestAgent.class, () -> new JavaTestAgent(requestHandler, dbHandler));
  }
} 
class JavaNoPropsAgent extends JavaInternalAgent{
  protected JavaNoPropsAgent(ActorRef requestHandler, ActorRef dbHandler){
    super(requestHandler,dbHandler);
  }
} 
