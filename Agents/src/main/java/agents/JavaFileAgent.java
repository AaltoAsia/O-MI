package agents;

import agentSystem.InternalAgentConfigurationFailure;
import agentSystem.JavaInternalAgent;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.dispatch.OnFailure;
import akka.dispatch.OnSuccess;
import akka.stream.ActorMaterializer;
import akka.japi.Creator;
import com.typesafe.config.Config;
import scala.collection.JavaConversions;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;
import scala.util.Random;
import types.odf.*;
import types.odf.parsing.*;
import types.omi.*;
import types.*;

import java.io.File;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

  protected ActorMaterializer materializer = ActorMaterializer.create(context());
  protected Config config;

  protected FiniteDuration interval;

  protected String pathToFile;
  protected File file;
  protected ODF odf;

  protected int writeCount = 0;

  protected Cancellable intervalJob = null;

  //Random for generating new values for path.
  protected Random rnd = new Random();


  // Constructor
  public JavaFileAgent(
      Config conf,
      final ActorRef requestHandler,
      final ActorRef dbHandler
  ) throws InternalAgentConfigurationFailure {
    super(requestHandler,dbHandler);
    config = conf;

    // Parse configuration for interval
    interval = new FiniteDuration(
            config.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    // Parse configuration for target O-DF path
    pathToFile = config.getString("file");
    file = new File(pathToFile);
    if( file.exists()  && file.isFile() && file.canRead() ){
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

      Future<ODF> futureODF = ODFStreamParser.parse(file.toPath(),materializer);
      futureODF.onSuccess(new OnSuccess<ODF>(){
        public void onSuccess( ODF parsed){
          odf = parsed;
          writeCount = 0;
        }
      }, ec);
      futureODF.onFailure(new OnFailure() {
        public void onFailure( Throwable failure) throws InternalAgentConfigurationFailure {
          throw new InternalAgentConfigurationFailure( 
              "Invalid O-DF structure"
              );
        }
      }, ec);
    } else if( !file.isFile()){
        throw new InternalAgentConfigurationFailure( 
            "File to be read for O-DF structure is not e file."
          );
    } else if( file.exists()){
        throw new InternalAgentConfigurationFailure( 
          "File to be read for O-DF structure can not be read."
          );
    } else {
        throw new InternalAgentConfigurationFailure( 
          "File to be read for O-DF structure, does not exist."
        );
    } 
  }

  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   */
  @Override
  public void postStop(){

    if (intervalJob != null){//is defined? 
      intervalJob.cancel();  //Cancel intervalJob
    } 
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
    odf = odf.replaceValues( pathValuePairs);
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
