package agents;

import java.lang.Object;
import java.lang.Exception;
import java.lang.Number;
import java.text.NumberFormat;
import java.util.concurrent.TimeUnit;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.util.Vector;
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

import agentSystem.JavaInternalAgent; 
import agentSystem.ResponsibilityRequest;
import agentSystem.*;
import types.Path;
import types.OmiTypes.*;
import types.OdfTypes.*;
import types.OdfFactory;
import types.OmiFactory;
import types.*;

/**
 * Writes random generated numbers to a O-DF structure defined in createOdf() method.
 * Can be used in testing or as a base for other agents.
 */
public class JavaRoomAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   *  <a href="https://github.com/typesafehub/config">Typesafe config</a>.
   */
  static public Props props(final Config _config) {
    return Props.create(new Creator<JavaRoomAgent>() {
      //Randow serialVersionUID, for serialization.
      private static final long serialVersionUID = 35735155L;

      @Override
      public JavaRoomAgent create() throws Exception {
        return new JavaRoomAgent(_config);
      }
    });
  }

  protected Config config;

  protected FiniteDuration interval;

  protected Cancellable intervalJob = null;

  //Random for generating new values for path.
  protected Random rnd = new Random();

  protected OdfObjects odf;

  // Constructor
  public JavaRoomAgent(Config conf){
    config = conf;

    // Parse configuration for interval
    interval = new FiniteDuration(
            config.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    odf = createOdf();
  }


  /**
   * Method to be called when a Start() message is received.
   */
  @Override
  public InternalAgentResponse start(){
    try{
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

      return new CommandSuccessful();

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

  protected int writeCount = 0;
  /**
   * Method for creating O-DF structure to be populated by this JavaInternalAgent.
   */
  public OdfObjects createOdf(){

    //Reset writeCount
    writeCount = 0;
    //O-DF Object s as child of O-DF Objects
    Vector<OdfObject> objects = new Vector<OdfObject>();
    objects.add( createExampleRoom() );
    
    //Create O-DF Objects
    return OdfFactory.createOdfObjects(objects); 
  }

  /**
   * Method for creating O-DF Object for path Objects/ExampleRoom.
   */
  public OdfObject createExampleRoom(){

    Path path = new Path( "Objects/ExampleRoom" );

    OdfDescription description = OdfFactory.createOdfDescription(
        "Example room filled with examples"
    );

    //Child O-DF InfoItems of ExampleRoom
    Vector<OdfInfoItem> infoItems = new Vector<OdfInfoItem>();
    OdfInfoItem location = createLocation(path); 
    infoItems.add(location);

    //Child O-DF Object of ExampleRoom
    Vector<OdfObject> objects = new Vector<OdfObject>();
    objects.add( createSensorBox(path));

    //Creata actual O-DF Object
    return OdfFactory.createOdfObject(
        path,
        infoItems,
        objects
    );
  }

  /**
   * Method for creating O-DF Object for a SensorBox.
   * @param parentPath Path of parent O-DF Object.
   */
  public OdfObject createSensorBox( Path parentPath){

    //Generate path from path of parent O-DF Object 
    Path path = new Path( parentPath.toString() +"/SensorBox" );

    //Extract id of parent
    String[] parentArray = parentPath.toArray(); 
    String parentId = parentArray[ parentArray.length - 1];//Last

    //Creata description
    OdfDescription description = OdfFactory.createOdfDescription(
        "SensorBox in " + parentId
    );

    //O-DF InfoItems of sensors in SensorBox
    Vector<OdfInfoItem> infoItems = new Vector<OdfInfoItem>();
    infoItems.add( createLocation(path) );
    infoItems.add( createSensor("Temperature","Celsius",path) );
    infoItems.add( createSensor("Humidity","Percentage of water in air",path) );

    //SensorBox doesn't have child O-DF Object s
    Vector<OdfObject> objects = new Vector<OdfObject>();

    //Create O-DY Object for SensorBox
    return OdfFactory.createOdfObject(
        path,
        infoItems,
        objects
    );

  }

  /**
   * Method for creating O-DF InfoItem for a sensor.
   * @param name Name of the sensor.
   * @param unit Unit of measured values.
   * @param parentPath Path of parent O-DF Object.
   */
  public OdfInfoItem createSensor( String name, String unit, Path parentPath){
    //Generate path from path of parent O-DF Object 
    Path path = new Path( parentPath.toString() + "/" + name );

    //Extract id of parent
    String[] parentArray = parentPath.toArray(); 
    String parentId = parentArray[ parentArray.length - 1];//Last

    // Generate new OdfValue<Object> 
    // timestamp for the value
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    // type metadata, default is xs:string
    String typeStr = "xs:double";
    // value as String
    String newValueStr = rnd.nextDouble() +""; 

    // Multiple values can be added at the same time but we add one
    Vector<OdfValue<Object>> values = new Vector<OdfValue<Object>>();
 
    // OdfValue has type parameter for type of value, Object is used to avoid problems
    // with having values with different types in same Collection.
    // Currently only following types are accepted:
    // String, Short, Int, Long, Float and Double.
    // Any other type is converted to String with toString(),
    // but typeStr is not changed.
    OdfValue<Object> value = OdfFactory.createOdfValue(
        newValueStr, typeStr, timestamp
    );
    values.add(value);

    //Create Unit meta data for the sensor. 
    Vector<OdfValue<Object>> metaValues = new Vector<OdfValue<Object>>();
    OdfValue<Object> metavalue = OdfFactory.createOdfValue(
        unit, "xs:string", timestamp
    );
    metaValues.add(value);

    Vector<OdfInfoItem> metaInfoItems = new Vector<OdfInfoItem>();
    OdfInfoItem metaInfoItem = OdfFactory.createOdfInfoItem(
       new Path( path.toString() +"/MetaData/Unit"), 
       metaValues
    );
    metaInfoItems.add(metaInfoItem);

    OdfMetaData metaData = OdfFactory.createOdfMetaData(
      metaInfoItems    
    );

    //Create description for the sensor.
    OdfDescription description = OdfFactory.createOdfDescription(
        name + " sensor of " + parentId
    );

    // Create O-DF InfoItem for the sensor. 
    return OdfFactory.createOdfInfoItem(
        path, 
        values,
        description,
        metaData

    );
  }

  /**
   * Method for creating O-DF InfoItem for a location.
   *
   * Uses format specified in 
   *  <a href="https://github.com/AaltoAsia/O-MI/blob/warp10integration/warp10-documentation.md">warp10 integration documentation</a>.
   * @param parentPath Path of parent O-DF Object.
   */
  public OdfInfoItem createLocation( Path parentPath){
    //Generate path from path of parent O-DF Object 
    Path path = new Path( parentPath.toString() + "/location"  );

    //Extract id of parent
    String[] parentArray = parentPath.toArray(); 
    String parentId = parentArray[ parentArray.length - 1];//Last

    // Generate new OdfValue<Object> 

    // timestamp for the value
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    // type metadata, default is xs:string
    String typeStr = "";
    // value as String
    String newValueStr = "+" + rnd.nextDouble() + "+" +rnd.nextDouble() + "+" + rnd.nextInt(15000) +"CRSWGS_84"; 

    // Multiple values can be added at the same time but we add one
    Vector<OdfValue<Object>> values = new Vector<OdfValue<Object>>();

    // OdfValue has type parameter for type of value, Object is used to avoid problems
    // with having values with different types in same Collection.
    // Currently only following types are accepted:
    // String, Short, Int, Long, Float and Double.
    // Any other type is converted to String with toString(),
    // but type attribute of O-DF InfoItem is not changed.
    OdfValue<Object> value = OdfFactory.createOdfValue(
        newValueStr, timestamp
    );
    values.add(value);

    //Create type meta data about loceation.
    Vector<OdfValue<Object>> metaValues = new Vector<OdfValue<Object>>();
    OdfValue<Object> metavalue = OdfFactory.createOdfValue(
        "ISO 6709", timestamp
    );
    metaValues.add(value);

    Vector<OdfInfoItem> metaInfoItems = new Vector<OdfInfoItem>();
    OdfInfoItem metaInfoItem = OdfFactory.createOdfInfoItem(
       new Path( path.toString() +"/MetaData/type"), 
       metaValues
    );
    metaInfoItems.add(metaInfoItem);

    OdfMetaData metaData = OdfFactory.createOdfMetaData(
      metaInfoItems    
    );

    //Create description for the location.
    OdfDescription description = OdfFactory.createOdfDescription(
        "Location of " + parentId
    );

    // Create O-DF InfoItem for location.
    return OdfFactory.createOdfInfoItem(
        path, 
        values,
        description,
        metaData

    );
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
