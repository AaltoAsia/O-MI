package agents;

import java.lang.Object;
import java.lang.Exception;
import java.lang.Number;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.Vector;
import java.util.Collection;
import java.io.File;
import java.sql.Timestamp;

import scala.concurrent.duration.*;
import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;
import scala.collection.JavaConversions;
import scala.util.*;
import akka.actor.Props;
import akka.util.Timeout;
import static akka.pattern.Patterns.ask;
import akka.japi.Creator;
import akka.dispatch.Mapper;
import akka.dispatch.OnSuccess;
import akka.dispatch.OnFailure;
import akka.actor.Cancellable;

import com.typesafe.config.Config;

import parsing.OdfParser;
import agentSystem.JavaInternalAgent; 
import agentSystem.ResponsibilityRequest;
import agentSystem.*;
import types.*;
import types.OmiTypes.*;
import types.OdfTypes.OdfValue;
import types.OdfTypes.*;
import types.OdfFactory;
import types.OmiFactory;
import types.OdfTypes.OdfInfoItem;

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 */
public class JavaFileAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   */
  static public Props props(final Config _config) {
    return Props.create(new Creator<JavaFileAgent>() {
      private static final long serialVersionUID = 3573L;

      @Override
      public JavaFileAgent create() throws Exception {
        return new JavaFileAgent(_config);
      }
    });
  }

  protected Config config;

  protected FiniteDuration interval;

  protected String pathToFile;
  protected File file;
  protected OdfObjects odf;

  protected boolean metaDatasAndDescriptionsNotWriten = true;

  protected Cancellable intervalJob = null;

  //Random for generating new values for path.
  protected Random rnd = new Random();


  // Constructor
  public JavaFileAgent(Config conf){
    config = conf;

    // Parse configuration for interval
    interval = new FiniteDuration(
            config.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    // Parse configuration for target O-DF path
    pathToFile = config.getString("file");
    file = new File(pathToFile);
    metaDatasAndDescriptionsNotWriten = true;
  }


  /**
   * Method to be called when a Start() message is received.
   */
  @Override
  public InternalAgentResponse start(){
    try{
      if( file.exists()  && file.isFile() && file.canRead() ){
        //Lets schelude a messge to us on every interval
        //and save the reference so we can stop the agent.
        intervalJob = context().system().scheduler().schedule(
          Duration.Zero(),                //Delay start
          interval,                       //Interval between messages
          self(),                         //To 
          "Update",                       //Message, preferably immutable.
          context().system().dispatcher(),//ExecutionContext, Akka
          null                            //Sender?
        );

        Either<Iterable<ParseError>,OdfObjects> parseResult = OdfParser.parse(file);
        if( parseResult.isLeft() ){
          return new StartFailed(
            "Invalid O-DF structure.", 
            scala.Option.empty()
            );
        } else {
          odf = parseResult.right().get();
          return new CommandSuccessful();
        }
      } else {
        return new StartFailed(
            "File to be read for O-DF structure, does not exist or is not file or can not be read", 
            scala.Option.empty()
            );
      }
    } catch (Throwable t) {
      //Normally in Akka if exception is thrown in child actor, it is 
      //passed to its parent. That uses {@link SupervisorStrategy} to decide 
      //what to do. With {@link StartFailed} we can tell AgentSystem that an 
      //Exception was thrown during handling of Start() message.
      return new StartFailed(t.getMessage(), scala.Option.apply(t) );
    }
  }


  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   */
  @Override
  public InternalAgentResponse stop(){

    if (intervalJob != null){//is defined? 
      intervalJob.cancel();  //Cancel intervalJob
      
      // Check if intervalJob was cancelled
      if( intervalJob.isCancelled() ){
        intervalJob = null;
      } else {
        return new StartFailed("Failed to stop agent.", scala.Option.apply(null));
      }
    } 
    return new CommandSuccessful();
  }


  /**
   * Method to be called when a "Update" message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  public void update() {

    // Generate new OdfValue<Object> 
    NumberFormat nf = NumberFormat.getInstance();
    Collection<OdfInfoItem> infoItems = JavaConversions.asJavaCollection(odf.infoItems());
    Map<Path, scala.collection.immutable.Vector<OdfValue<Object>>> pathValuePairs = new HashMap();
    for( OdfInfoItem item : infoItems){
      Collection<OdfValue<Object>> oldValues = JavaConversions.asJavaCollection(item.values());
      // timestamp for the value
      Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
      // type metadata, default is xs:string
      String typeStr = "xs:string";
      // value as String
      String newValueStr = ""; 
      String oldValueStr = ""; 

      if( !oldValues.isEmpty() ){
        OdfValue<Object> old = oldValues.iterator().next();
        double multiplier = 1 + rnd.nextGaussian() * 0.05 ; 
        typeStr = old.typeValue();
        oldValueStr = old.value().toString();
        log.debug( item.path().toString() + ": ");
        log.debug( "Type: " + typeStr);
        log.debug( "Multiplier: " + multiplier);
        log.debug( "Old: " + oldValueStr);

        try{
          Number oldValue = nf.parse(oldValueStr);
          if( oldValue instanceof Long ) {
            long oldV = oldValue.longValue(); 
            typeStr = "xs:long";
            newValueStr = (multiplier * oldV)+"";

          } else if( oldValue instanceof Integer ) {
            int oldV = oldValue.intValue(); 
            typeStr = "xs:integer";
            newValueStr = (multiplier * oldV)+"";

          } else  if( oldValue instanceof Short ) {
            short oldV = oldValue.shortValue(); 
            typeStr = "xs:short";
            newValueStr = (multiplier * oldV)+"";

          } else  if( oldValue instanceof Float ) {
            float oldV = oldValue.floatValue(); 
            typeStr = "xs:float";
            newValueStr = (multiplier * oldV)+"";

          } else  if( oldValue instanceof Double ) {
            double oldV = oldValue.doubleValue(); 
            typeStr = "xs:double";
            newValueStr = (multiplier * oldV)+"";

          }
        } catch(java.text.ParseException pe){
          if( oldValueStr.toLowerCase().equals("false") ){
            typeStr = "xs:boolean";
            if( multiplier > 1.05 || multiplier < -1.05 ) {
              newValueStr = "true";
            } else {
              newValueStr = "false";
            }
          } else if( oldValueStr.toLowerCase().equals("true") ){
            typeStr = "xs:boolean";
            if( multiplier > 1.05 || multiplier < -1.05 ) {
              newValueStr = "false";
            } else {
              newValueStr = "true";
            }
          } else {
            typeStr = "xs:string";
            newValueStr = oldValueStr;
          }
        }
        log.debug( "New: " + newValueStr);
      }
      // Multiple values can be added at the same time but we add one
      Vector<OdfValue<Object>> newValues = new Vector<OdfValue<Object>>();

      OdfValue<Object> value = OdfFactory.createOdfValue(
          newValueStr, typeStr, timestamp
      );
      newValues.add(value);

      pathValuePairs.put(item.path(), OdfTreeCollection.fromJava(newValues));
    }
    scala.collection.mutable.Map<Path,scala.collection.immutable.Vector<OdfValue<Object>>> scalaMap= 
      JavaConversions.mapAsScalaMap(pathValuePairs);

    odf = odf.withValues( JavaHelpers.mutableMapToImmutable(scalaMap) );
    if( !metaDatasAndDescriptionsNotWriten ){
      odf = odf.allMetaDatasRemoved();
    } else metaDatasAndDescriptionsNotWriten = false;

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(name + " pushing data...");

    // Create O-MI write request
    // interval as time to live
    WriteRequest write = OmiFactory.createWriteRequest(
        interval, // ttl
        odf   // O-DF
    );
    
    // timeout for the write request, which means how long this agent waits for write results
    Timeout timeout = new Timeout(interval);

    // Execute the request, execution is asynchronous (will not block)
    Future<ResponsibleAgentResponse> result = writeToNode(write, timeout);

    ExecutionContext ec = context().system().dispatcher();
    // Call LogResult function (below) when write was successful.
    result.onSuccess(new LogResult(), ec);
    result.onFailure(new LogFailure(), ec);

  }


  // Contains function for the asynchronous handling of write result
  public final class LogResult extends OnSuccess<ResponsibleAgentResponse> {
      @Override public final void onSuccess(ResponsibleAgentResponse result) {
        if( result instanceof SuccessfulWrite ){
          // This sends debug log message to O-MI Node logs if
          // debug level is enabled (in logback.xml and application.conf)
          log.debug(name + " wrote all paths successfully.");
        } else if( result instanceof FailedWrite ) {
          FailedWrite fw = (FailedWrite) result; 
          log.warning(
            name + " failed to write to paths:\n" + fw.paths().mkString("\n") +
            " because of following reason:\n" + fw.reasons().mkString("\n")
          );
        }  else if( result instanceof MixedWrite ) {
          MixedWrite mw = (MixedWrite) result; 
          log.warning(
            name + " successfully wrote to paths:\n" + mw.successed().mkString("\n") +
            " and failed to write to paths:\n" + mw.failed().paths().mkString("\n") +
            " because of following reason:\n" + mw.failed().reasons().mkString("\n")
          );
        }
      }
  }
  // Contains function for the asynchronous handling of write failure
  public final class LogFailure extends OnFailure{
      @Override public final void onFailure(Throwable t) {
          log.warning(
            name + " failed to write to all paths, reason: " + t.getMessage()
          );
      }
  }

  
  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public void onReceive(Object message) throws StartFailed, CommandFailed {
    if( message instanceof Start) {
      // Start is received when this agent should start it's functionality
      getSender().tell(start(),getSelf());

    } else if( message instanceof Stop) {
      // Stop is received when this agent should stop it's functionality
      getSender().tell(stop(),getSelf());

    } else if( message instanceof Restart) {
      // Restart is received when this agent should restart
      // default behaviour is to call stop() and then start()
      getSender().tell(restart(),getSelf());

    } else if( message instanceof String) {
      String str = (String) message;
      if( str.equals("Update"))
        update();

    } else unhandled(message);
  }
}
