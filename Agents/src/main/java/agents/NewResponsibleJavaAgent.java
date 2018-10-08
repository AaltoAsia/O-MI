package agents;

import agentSystem.ResponsibleJavaInternalAgent;
import akka.actor.ActorRef;
import akka.actor.AbstractActor.Receive;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.japi.Creator;
import com.typesafe.config.Config;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Random;
import types.OdfFactory;
import types.odf.Node;
import types.odf.ImmutableODF;
import types.odf.InfoItem;
import types.odf.Objects;
import types.odf.Value;
import types.odf.Description;
import types.odf.MetaData;
import types.OmiFactory;
import types.OmiTypes.OmiResult;
import types.OmiTypes.WriteRequest;
import types.OmiTypes.CallRequest;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.Responses;
import types.OmiTypes.Results;
import types.Path;

import java.sql.Timestamp;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 */
public class NewResponsibleJavaAgent extends ResponsibleJavaInternalAgent {
  /**
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   */
  static public Props props(final Config _config, final ActorRef requestHandler, final ActorRef dbHandler) {
    return Props.create(NewResponsibleJavaAgent.class, () -> new NewResponsibleJavaAgent(_config, requestHandler, dbHandler));
  }

  protected Config config;
  protected FiniteDuration interval;
  protected Path path;
  protected Cancellable intervalJob;

  // Constructor
  public NewResponsibleJavaAgent(Config conf, final ActorRef requestHandler, final ActorRef dbHandler){
    super(requestHandler,dbHandler);
    config = conf;

    path = new Path(config.getString("path"));
    // Parse configuration for interval
    interval = new FiniteDuration(
            config.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	


    intervalJob = context().system().scheduler().schedule(
        Duration.Zero(),                //Delay start
        interval,                       //Interval between messages
        self(),                         //To 
        "Update",                       //Message, preferably immutable.
        context().system().dispatcher(),//ExecutionContext, Akka
        null                            //Sender?
      );
  }
  
  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public Receive createReceive(){
    return receiveBuilder()
      .matchEquals("Update",s -> update())
      .build()
      .orElse(super.createReceive());
  }


  //Random for generating new values for path.
  protected Random rnd = new Random();

  /**
   * Method to be called when a "Update" message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  public void update() {

    // Generate new Value<Object> 

    // timestamp for the value
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    // type metadata, default is xs:string
    String typeStr = "xs:double";
    // value as String
    String newValueStr = rnd.nextDouble() +""; 

    // Multiple values can be added at the same time but we add one
    Vector<Value<java.lang.Object>> values = new Vector<>();

    //Values value can be stored as: string, short, int, long, float or double
    Value<java.lang.Object> value = OdfFactory.createValue(
        newValueStr, typeStr, timestamp
    );
    values.add(value);
    //Create description
    Vector<Description> descriptions = new Vector<Description>();
    Description description = OdfFactory.createDescription( "Temperature sensor in SensorBox", "english");
    descriptions.add(description);

    // Create O-DF MetaData
    Vector<InfoItem> metaItems = new Vector<InfoItem>();
    Vector<Value<java.lang.Object>> metaValues = new Vector<Value<java.lang.Object>>();
    Value<java.lang.Object> metaValue = OdfFactory.createValue(
        "Celsius", "xs:string", timestamp
    );
    metaValues.add(metaValue);
    InfoItem metaItem = OdfFactory.createInfoItem(
        new Path( path + "/MetaData/Units"),
        metaValues
    );
    metaItems.add(metaItem);
    MetaData metaData = OdfFactory.createMetaData(metaItems);


    // Create InfoItem to contain the value. 
    InfoItem infoItem = OdfFactory.createInfoItem(
        path,
        null,
        descriptions,
        values,
        metaData
    );

    Vector<Node> nodes = new Vector<Node>();
    nodes.add(infoItem);
    // createAncestors generates O-DF structure from the path of an Node 
    // and returns the root, Objects
    ImmutableODF odf = OdfFactory.createImmutableODF(nodes);

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(name() + " pushing data...");

    // Create O-MI write request
    // interval as time to live
    WriteRequest write = OmiFactory.createWriteRequest(
        interval, // ttl
        odf   // O-DF
    );
    

    // Execute the request, execution is asynchronous (will not block)
    Future<ResponseRequest> result = writeToDB(write);

    ExecutionContext ec = context().system().dispatcher();
    // Call LogResult function (below) when write was successful.
    result.onSuccess(new LogResult(), ec);
    result.onFailure(new LogFailure(), ec);

  }

  // Contains function for the asynchronous handling of write result
  public final class LogResult extends OnSuccess<ResponseRequest> {
      @Override public final void onSuccess(ResponseRequest response) {
        Iterable<OmiResult> results = response.resultsAsJava() ;
        for( OmiResult result : results ){
          if( result instanceof Results.Success ){
            // This sends debug log message to O-MI Node logs if
            // debug level is enabled (in logback.xml and application.conf)
            log.info(name() + " wrote paths successfully.");
          } else {
            log.warning(
                "Something went wrong when " + name() + " wrote, " + result.toString()
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
  public Future<ResponseRequest> handleWrite(WriteRequest write) {
    
    Future<ResponseRequest> future = writeToDB(write);

    ExecutionContext ec = context().system().dispatcher();
    future.onSuccess(new LogResult(), ec);
    future.onFailure(new LogFailure(), ec);
    return future;
  }

  @Override
  public Future<ResponseRequest> handleCall(CallRequest call){
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    Vector<Value<java.lang.Object>> values = new Vector<>();

    //Values value can be stored as: string, short, int, long, float or double
    Value<java.lang.Object> value = OdfFactory.createValue(
        "Hello O-MI user", "xs:string", timestamp
    );
    values.add(value);
    InfoItem infoItem = OdfFactory.createInfoItem(
        path,
        values
    );

    Vector<Node> nodes = new Vector<Node>();
    nodes.add(infoItem);
    // createAncestors generates O-DF structure from the path of an Node 
    // and returns the root, Objects
    ImmutableODF odf = OdfFactory.createImmutableODF(nodes);
    return Futures.successful( 
        Responses.Success(scala.Option.apply(odf),Duration.apply(10,TimeUnit.SECONDS))
    );
  }


  /**
   * Method to be called when actor is stopped.
   * This should gracefully stop all activities that the agent is doing.
   */
  @Override
  public void postStop(){
      intervalJob.cancel();  //Cancel intervalJob
  }
}
