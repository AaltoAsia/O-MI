package agentSystem;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.typesafe.config.Config;

public interface PropsCreator{
  Props props(Config config, ActorRef requestHandler, ActorRef dbHandler);
}
