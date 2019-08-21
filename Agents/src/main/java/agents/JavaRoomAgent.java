package agents;

import agentSystem.JavaInternalAgent;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.japi.Creator;
import com.typesafe.config.Config;
import scala.collection.JavaConversions;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Random;
import types.JavaHelpers;
import types.OmiFactory;
import types.OdfFactory;
import types.omi.*;
import types.odf.*;
import types.Path;

import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Writes random generated numbers to a O-DF structure defined in createOdf() method.
 * Can be used in testing or as a base for other agents.
 */
public class JavaRoomAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC FACTORY METHOD MUST EXISTS FOR JavaInternalAgent 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   *  <a href="https://github.com/typesafehub/config">Typesafe config</a>.
   */
  static public Props props(final Config _config, final ActorRef requestHandler, final ActorRef dbHandler) {
    return Props.create(new Creator<JavaRoomAgent>() {
      //Random serialVersionUID, for serialization.
      private static final long serialVersionUID = 35735155L;

      @Override
      public JavaRoomAgent create() throws Exception {
        return new JavaRoomAgent(_config, requestHandler, dbHandler);
      }
    });
  }

  //Interval between writes and cancellable job run after every interval. 
  protected FiniteDuration interval;
  protected Cancellable intervalJob;

  //Random for generating new values for path.
  protected Random rnd = new Random();

  //Our O-DF structure
  protected ODF odf;

  // Constructor
  public JavaRoomAgent(Config conf, final ActorRef requestHandler, final ActorRef dbHandler){
    super(requestHandler,dbHandler);    
    // Parse configuration for interval
    interval = new FiniteDuration(
            conf.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    //Lets schedule a message to us on every interval
    //and save the reference so we can stop the agent.
    intervalJob = context().system().scheduler().schedule(
        Duration.Zero(),                //Delay start
        interval,                       //Interval between messages
        self(),                         //To 
        "Update",                       //Message, preferably immutable.
        context().system().dispatcher(),//ExecutionContext, Akka
        null                            //Sender?
        );

    odf = createOdf();
  }

  /**
   * Method to be called when actor is stopped.
   * This should gracefully stop all activities that the agent is doing.
   */
  @Override
  public void postStop(){

    if (intervalJob != null){//is defined? 
      intervalJob.cancel();  //Cancel intervalJob
    } 
  }

  protected int writeCount = 0;
  /**
   * Method for creating O-DF structure to be populated by this JavaInternalAgent.
   */
  public ODF createOdf(){

    //Reset writeCount
    writeCount = 0;
    //O-DF Object s as child of O-DF Objects
    Vector<Node> nodes = new Vector<>();
    nodes.addAll( createExampleRoom() );
    
    //Create O-DF Objects
    return OdfFactory.createImmutableODF(nodes); 
  }

  /**
   * Method for creating O-DF Object for path Objects/ExampleRoom.
   */
  public Vector<Node> createExampleRoom(){

    Vector<Node> nodes = new Vector<>();

    Path path = new Path( "Objects/ExampleRoom" );
    Vector<Description> descriptions = new Vector<>();
    Description description = OdfFactory.createDescription(
        "Example room filled with examples"
    );
    descriptions.add(description);

    //Create an actual O-DF Object
    nodes.add(OdfFactory.createObject(
        path,
        descriptions
    ));

    //Child O-DF InfoItems of ExampleRoom
    InfoItem location = createLocation(path); 
    nodes.add(location);

    //Child O-DF Object of ExampleRoom
    nodes.addAll( createSensorBox(path));

    return nodes;
  }

  /**
   * Method for creating O-DF Object for a SensorBox.
   * @param parentPath Path of parent O-DF Object.
   */
  public Vector<Node> createSensorBox( Path parentPath){

    Vector<Node> nodes = new Vector<>();

    //Generate path from path of parent O-DF Object 
    Path path = new Path( parentPath.toString() +"/SensorBox" );

    //Extract id of parent
    String[] parentArray = parentPath.toArray(); 
    String parentId = parentArray[ parentArray.length - 1];//Last

    //Create a description
    Vector<Description> descriptions = new Vector<>();
    Description description = OdfFactory.createDescription(
        "SensorBox in " + parentId
    );
    descriptions.add(description);

    nodes.add(OdfFactory.createObject(
        path,
        descriptions
    ));

    //O-DF InfoItems of sensors in SensorBox
    nodes.add( createLocation(path) );
    nodes.add( createSensor("Temperature","Celsius",path) );
    nodes.add( createSensor("Humidity","Percentage of water in air",path) );
    //Create O-DY Object for SensorBox
    return nodes;
  }

  /**
   * Method for creating O-DF InfoItem for a sensor.
   * @param name Name of the sensor.
   * @param unit Unit of measured values.
   * @param parentPath Path of parent O-DF Object.
   */
  public InfoItem createSensor( String name, String unit, Path parentPath){
    //Generate path from path of parent O-DF Object 
    Path path = new Path( parentPath.toString() + "/" + name );

    //Extract id of parent
    String[] parentArray = parentPath.toArray(); 
    String parentId = parentArray[ parentArray.length - 1];//Last

    // Generate new Value<java.lang.Object> 
    // timestamp for the value
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    // type metadata, default is xs:string
    String typeStr = "xs:double";
    // value as String
    String newValueStr = rnd.nextDouble() +""; 

    // Multiple values can be added at the same time but we add one
    Vector<Value<java.lang.Object>> values = new Vector<>();
 
    // Value has type parameter for type of value, Object is used to avoid problems
    // with having values with different types in same Collection.
    // Currently only following types are accepted:
    // String, Short, Int, Long, Float and Double.
    // Any other type is converted to String with toString(),
    // but typeStr is not changed.
    Value<java.lang.Object> value = OdfFactory.createValue(
        newValueStr, typeStr, timestamp
    );
    values.add(value);

    //Create Unit meta data for the sensor. 
    Vector<Value<java.lang.Object>> metaValues = new Vector<>();
    Value<java.lang.Object> metaValue = OdfFactory.createValue(
        unit, "xs:string", timestamp
    );
    metaValues.add(metaValue);

    Vector<InfoItem> metaInfoItems = new Vector<>();
    InfoItem metaInfoItem = OdfFactory.createInfoItem(
       new Path( path.toString() +"/MetaData/Unit"), 
       metaValues
    );
    metaInfoItems.add(metaInfoItem);

    MetaData metaData = OdfFactory.createMetaData(
      metaInfoItems    
    );

    //Create description for the sensor.
    Vector<Description> descriptions = new Vector<>();
    Description description = OdfFactory.createDescription(
        name + " sensor of " + parentId
    );
    descriptions.add(description);

    // Create O-DF InfoItem for the sensor. 
    return OdfFactory.createInfoItem(
        path, 
        descriptions,
        values,
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
  public InfoItem createLocation( Path parentPath){
    //Generate path from path of parent O-DF Object 
    Path path = new Path( parentPath.toString() + "/location"  );

    //Extract id of parent
    String[] parentArray = parentPath.toArray(); 
    String parentId = parentArray[ parentArray.length - 1];//Last

    // Generate new Value<java.lang.Object> 

    // timestamp for the value
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    // type metadata, default is xs:string
    String typeStr = "";
    // value as String
    String newValueStr = "+" + rnd.nextDouble() + "+" +rnd.nextDouble() + "+" + rnd.nextInt(15000) +"CRSWGS_84"; 

    // Multiple values can be added at the same time but we add one
    Vector<Value<java.lang.Object>> values = new Vector<>();

    // Value has type parameter for type of value, Object is used to avoid problems
    // with having values with different types in same Collection.
    // Currently only following types are accepted:
    // String, Short, Int, Long, Float and Double.
    // Any other type is converted to String with toString(),
    // but type attribute of O-DF InfoItem is not changed.
    Value<java.lang.Object> value = OdfFactory.createValue(
        newValueStr, timestamp
    );
    values.add(value);

    //Create type meta data about location.
    Vector<Value<java.lang.Object>> metaValues = new Vector<>();
    Value<java.lang.Object> metaValue = OdfFactory.createValue(
        "ISO 6709", timestamp
    );
    metaValues.add(metaValue);

    Vector<InfoItem> metaInfoItems = new Vector<>();
    InfoItem metaInfoItem = OdfFactory.createInfoItem(
       new Path( path.toString() +"/MetaData/type"), 
       metaValues
    );
    metaInfoItems.add(metaInfoItem);

    MetaData metaData = OdfFactory.createMetaData(
      metaInfoItems    
    );

    //Create description for the location.
    Vector<Description> descriptions = new Vector<>();
    Description description = OdfFactory.createDescription(
        "Location of " + parentId
    );
    descriptions.add(description);

    // Create O-DF InfoItem for location.
    return OdfFactory.createInfoItem(
        path, 
        descriptions,
        values,
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
    Collection<InfoItem> infoItems = JavaConversions.asJavaCollection(odf.getInfoItems());

    //Collection of new values per path
    Map<Path, Vector<Value<java.lang.Object>>> pathValuePairs = new HashMap<>();

    //Generate new value for each O-DF InfoItem
    for( InfoItem item : infoItems){
      //Get old values
      Collection<Value<java.lang.Object>> oldValues = JavaConversions.asJavaCollection(item.values());

      // timestamp for the value
      Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
      // type metadata, default is xs:string
      String typeStr = "";

      String newValueStr = ""; 
      String oldValueStr = ""; 

      //There should be only one value stored in oldValues
      Iterator<Value<java.lang.Object>> iterator = oldValues.iterator();
      if( iterator.hasNext() ){
        Value<java.lang.Object> old = iterator.next();

        //Multiplier for generating new value 
        double multiplier = 1 + rnd.nextGaussian() * 0.05 ; 

        //Extract type and value of old
        typeStr = old.typeAttribute();
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
          switch (oldValueStr.toLowerCase()) {
            case "false":
              typeStr = "xs:boolean";
              if (multiplier > 1.05 || multiplier < -1.05) {
                newValueStr = "true";
              } else {
                newValueStr = "false";
              }
              break;
            case "true":
              typeStr = "xs:boolean";
              if (multiplier > 1.05 || multiplier < -1.05) {
                newValueStr = "false";
              } else {
                newValueStr = "true";
              }
              break;
            default:
              //Was not a Boolean. Keep old value as string
              newValueStr = oldValueStr;
              break;
          }
        }
      }

      // Multiple values can be added at the same time but we add one
      Vector<Value<java.lang.Object>> newValues = new Vector<>();
      Value<java.lang.Object> value = OdfFactory.createValue(
          newValueStr, typeStr, timestamp
      );
      newValues.add(value);

      //Add to pathValuePairs
      pathValuePairs.put(item.path(), newValues);
    }


    //Replaces old values with new
    //Odf* classes are immutable, so they need be copied to be edited.
    //We should change as much as we can with single copy to avoid creating garbage.
    odf = odf.replaceValues( pathValuePairs );
  }

  /**
   * Method to be called when a "Update" message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  public void update() {
    updateOdf();
    //MetaData and description should be written only once
    if( writeCount == 2 ){
      odf = odf.metaDatasRemoved().descriptionsRemoved().attributesRemoved();
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

  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public Receive createReceive(){
    return receiveBuilder().matchEquals(
        "Update", s -> update()
    ).build().orElse( super.createReceive() );
  }
}
