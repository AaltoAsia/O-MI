package agentSystem;
import com.typesafe.config.Config;
import akka.actor.Props;

public interface PropsCreator{
  public Props props( Config config );
}
