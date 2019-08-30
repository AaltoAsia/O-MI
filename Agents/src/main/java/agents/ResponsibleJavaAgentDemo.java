package agents;

import agentSystem.ResponsibleJavaInternalAgent;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.japi.Creator;
import com.typesafe.config.Config;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import types.OdfFactory;
import types.Path;
import types.odf.*;
import types.omi.ResponseRequest;
import types.omi.CallRequest;
import types.omi.Responses;

import java.sql.Timestamp;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class ResponsibleJavaAgentDemo extends ResponsibleJavaInternalAgent {
  static public Props props(final Config config, final ActorRef requestHandler,final ActorRef dbHandler){
    return Props.create(
        new Creator<ResponsibleJavaAgentDemo>() {
          private static final long serialVersionUID = 35735155L;

          @Override
          public ResponsibleJavaAgentDemo create() throws Exception {
            return new ResponsibleJavaAgentDemo(config, requestHandler, dbHandler);
          }} 
        );
      }
  public ResponsibleJavaAgentDemo(
      Config config,
      final ActorRef requestHandler,
      final ActorRef dbHandler
      ){
    super(requestHandler,dbHandler);
      }

  public Future<ResponseRequest> handleCall(CallRequest call){
    try{
      Node node = call.odf().get( new Path( "Objects/Service/Greeter" ) ).get();
      InfoItem ii = (InfoItem)node;
      Value<java.lang.Object> value = ii.values().head();
      Value<java.lang.Object> newValue = OdfFactory.createValue(
          "Hello " + value.value().toString() + ", welcome to the Internet of Things.",
          new Timestamp(new java.util.Date().getTime())
      );  
      Vector<Value<java.lang.Object>> values = new Vector<>();
      values.add( newValue );

      Vector<Node> nodes = new Vector<>();
      InfoItem new_ii = OdfFactory.createInfoItem(
          new Path("Objects/Service/Greeting"),
          values 
      );
      nodes.add(new_ii);

      return Futures.successful( 
          Responses.Success(
            scala.Option.apply(OdfFactory.createImmutableODF(nodes)),
            Duration.apply(10,TimeUnit.SECONDS)
          )
      );
    } catch(Exception exp ) {
      return Futures.successful( 
          Responses.InternalError(exp)
          );
    }
  }
}

