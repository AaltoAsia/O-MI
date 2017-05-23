package agents;

import java.lang.Object;
import java.lang.Exception;
import java.lang.Number;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Iterator;
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
import akka.actor.ActorRef;
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
import agentSystem.*;
import types.*;
import types.OmiTypes.*;
import types.OdfTypes.OdfValue;
import types.OdfTypes.*;
import types.OdfFactory;
import types.OmiFactory;
import types.OmiTypes.Results;
import types.OmiTypes.OmiResult;
import types.OdfTypes.OdfInfoItem;

/**
 * Parses given file for O-DF structure and updates it's values.
 */
public class JavaFileAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   *  <a href="https://github.com/typesafehub/config">Typesafe config</a>.
   */
  static public Props props(final Config _config, final ActorRef requestHandler, final ActorRef dbHandler) {
    return Props.create(new Creator<JavaFileAgent>() {
      private static final long serialVersionUID = 3573L;

      @Override
      public JavaFileAgent create() throws Exception {
        return new JavaFileAgent(_config,requestHandler,dbHandler);
      }
    });
  }

  protected Config config;

  protected FiniteDuration interval;

  protected String pathToFile;
  protected File file;
  protected OdfObjects odf;

  protected int writeCount = 0;

  protected Cancellable intervalJob = null;

  //Random for generating new values for path.
  protected Random rnd = new Random();


  // Constructor
  public JavaFileAgent(Config conf, final ActorRef requestHandler, final ActorRef dbHandler){
    super(requestHandler,dbHandler);
    config = conf;

    // Parse configuration for interval
    interval = new FiniteDuration(
            config.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    // Parse configuration for target O-DF path
    pathToFile = config.getString("file");
    file = new File(pathToFile);
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
          writeCount = 0;
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
   * Method to be called when for updating values stored in odf.
   */
  public void updateOdf() {

    // Generate new values for O-DF structure

    //Helper for parsing Numbers from old values
    NumberFormat nf = NumberFormat.getInstance();

    //All O-DF InfoItems in O-DF structure 
    Collection<OdfInfoItem> infoItems = JavaConversions.asJavaCollection(odf.infoItems());

    //Collection of new values per path
    Map<Path, scala.collection.immutable.Vector<OdfValue<Object>>> pathValuePairs = new HashMap();

    //Generate new value for each O-DF InfoItem
    for( OdfInfoItem item : infoItems){
      //Get old values
      Collection<OdfValue<Object>> oldValues = JavaConversions.asJavaCollection(item.values());

      // timestamp for the value
      Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
      // type metadata, default is xs:string
      String typeStr = "";

      String newValueStr = ""; 
      String oldValueStr = ""; 

      //There should be only one value stored in oldValues
      Iterator<OdfValue<Object>> iterator = oldValues.iterator();
      if( iterator.hasNext() ){
        OdfValue<Object> old = iterator.next();

        //Multiplier for generating new value 
        double multiplier = 1 + rnd.nextGaussian() * 0.05 ; 

        //Extract type and value of old
        typeStr = old.typeValue();
        oldValueStr = old.value().toString();

        try{

          //Try to parse string to a Number
          Number oldValue = nf.parse(oldValueStr);
          
          //Generate new number
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
          //Value was not a Number
          //Check if it is a Boolean
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
            //Was not a Boolean. Keep old value as string 
            newValueStr = oldValueStr;
          }
        }
      }

      // Multiple values can be added at the same time but we add one
      Vector<OdfValue<Object>> newValues = new Vector<OdfValue<Object>>();
      OdfValue<Object> value = OdfFactory.createOdfValue(
          newValueStr, typeStr, timestamp
      );
      newValues.add(value);

      //Add to pathValuePairs
      pathValuePairs.put(item.path(), OdfTreeCollection.fromJava(newValues));
    }

    //Convert Map to Scala Map
    scala.collection.mutable.Map<Path,scala.collection.immutable.Vector<OdfValue<Object>>> scalaMap= 
      JavaConversions.mapAsScalaMap(pathValuePairs);

    //Replaces old values with new
    //Odf* classes are immutable, so they need be copied to be edited.
    //We should change as much as we can with single copy to avoid creating garbage.
    odf = odf.withValues( JavaHelpers.mutableMapToImmutable(scalaMap) );
  }

  /**
   * Method to be called when a "Update" message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  public void update() {
    updateOdf();
    //MetaData and description should be writen only once
    if( writeCount == 2 ){
      odf = odf.allMetaDatasRemoved();
    } else writeCount += 1;

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
  
  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public void onReceive(Object message) throws StartFailed, CommandFailed {
    if( message instanceof String) {
      String str = (String) message;
      if( str.equals("Update"))
        update();
      else super.onReceive(message);
    } else super.onReceive(message);
  }
}
