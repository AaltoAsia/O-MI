package agents;

import java.util.Vector;
import java.util.Date;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import scala.concurrent.duration.*;
import scala.concurrent.Future;
import akka.japi.Creator;
import akka.actor.Props;
import akka.actor.ActorRef;
import akka.dispatch.*;

import com.typesafe.config.Config;

import agentSystem.*;
import types.*;
import types.OdfTypes.*;
import types.OmiTypes.*;

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
      OdfNode node = call.odf().get( new Path( "Objects/Service/Greeter" ) ).get();
      OdfInfoItem ii = (OdfInfoItem)node;
      OdfValue<Object> value = ii.values().head();
      OdfValue<Object> newValue = OdfFactory.createOdfValue(
          "Hello " + value.value().toString() + ", welcone to the Internet of Things.",
          new Timestamp(new java.util.Date().getTime())
      );  
      Vector<OdfValue<Object>> values = new Vector<OdfValue<Object>>();
      values.add( newValue );
      OdfInfoItem new_ii = OdfFactory.createOdfInfoItem(
          new Path("Objects/Service/Greeting"),
          values 
      );

      return Futures.successful( 
          Responses.Success(
            scala.Option.apply(new_ii.createAncestors()),
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

