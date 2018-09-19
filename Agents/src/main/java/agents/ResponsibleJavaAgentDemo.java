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
import types.odf.NewTypeConverter;
import types.odf.OldTypeConverter;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.CallRequest;
import types.OmiTypes.Responses;
import types.OdfTypes.OdfInfoItem;
import types.OdfTypes.OdfNode;
import types.OdfTypes.OdfValue;

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
      OdfNode node = NewTypeConverter.convertODF(call.odf()).get( new Path( "Objects/Service/Greeter" ) ).get();
      OdfInfoItem ii = (OdfInfoItem)node;
      OdfValue<Object> value = ii.values().head();
      OdfValue<Object> newValue = OdfFactory.createOdfValue(
          "Hello " + value.value().toString() + ", welcome to the Internet of Things.",
          new Timestamp(new java.util.Date().getTime())
      );  
      Vector<OdfValue<Object>> values = new Vector<>();
      values.add( newValue );
      OdfInfoItem new_ii = OdfFactory.createOdfInfoItem(
          new Path("Objects/Service/Greeting"),
          values 
      );

      return Futures.successful( 
          Responses.Success(
            scala.Option.apply(OldTypeConverter.convertOdfObjects(new_ii.createAncestors())),
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

