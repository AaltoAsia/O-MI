Agents implemented using Java
=======

* [JavaAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaAgent.java), simplest InternalAgent.
Takes a O-DF path of InfoItem and write random generated values to it.

* [JavaRoomAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaRoomAgent.java), 
creates O-DF structure using OdfFactory and writes random generated values to it.

* [JavaFileAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaFileAgent.java).
parses O-DF structure from given file ands write randow generated values to it.

Java agent example, JavaAgent.java
=================================

Let's create an *interanal agent*, that gets a path to O-DF InfoItem from 
configuration and writes random generated values to it every interval given in 
configuration. First, create class `JavaAgent` that extends `JavaInternalAgent`.
Mandatory parts are methods `props`, the constructor and `onReceive`. Any system 
resources should be freed in the stop method at the latest.

Read the comments for more detailed explanation. Here is the code for this example agent:

```Java
package agents;

import java.lang.Exception;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Vector;
import java.sql.Timestamp;

import scala.concurrent.duration.*;
import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;
import scala.collection.immutable.HashMap;
import scala.collection.JavaConverters.*;
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
import types.OdfTypes.OdfValue;
import types.OdfTypes.*;
import types.OdfFactory;
import types.OmiFactory;
import types.OdfTypes.OdfInfoItem;

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 */
public class JavaAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   */
  static public Props props(final Config _config) {
    return Props.create(new Creator<JavaAgent>() {
      private static final long serialVersionUID = 3573L;

      @Override
      public JavaAgent create() throws Exception {
        return new JavaAgent(_config);
      }
    });
  }

  protected Config config;

  protected FiniteDuration interval;

  protected Path path;

  protected Cancellable intervalJob = null;

  //Random for generating new values for path.
  protected Random rnd = new Random();


  // Constructor
  public JavaAgent(Config conf){
    config = conf;

    // Parse configuration for interval
    interval = new FiniteDuration(
            config.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    path = new Path("Objects/SensorBox/temperature");
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


  /**
   * Method to be called when a "Update" message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  public void update() {

    // Generate new OdfValue<Object> 

    // timestamp for the value
    Timestamp timestamp =  new Timestamp(new java.util.Date().getTime());
    // type metadata, default is xs:string
    String typeStr = "xs:double";
    // value as String
    String newValueStr = rnd.nextDouble() +""; 

    // Multiple values can be added at the same time but we add one
    Vector<OdfValue<Object>> values = new Vector<OdfValue<Object>>();

    //OdfValues value can be stored as: string, short, int, long, float or double
    OdfValue<Object> value = OdfFactory.createOdfValue(
        newValueStr, typeStr, timestamp
    );
    values.add(value);
    //Create description
    OdfDescription description = OdfFactory.createOdfDescription( "Temperature sensor in SensorBox");

    // Create O-DF MetaData
    Vector<OdfInfoItem> metaItems = new Vector<OdfInfoItem>();
    Vector<OdfValue<Object>> metaValues = new Vector<OdfValue<Object>>();
    OdfValue<Object> metaValue = OdfFactory.createOdfValue(
        "Celsius", "xs:string", timestamp
    );
    metaValues.add(metaValue);
    OdfInfoItem metaItem = OdfFactory.createOdfInfoItem(
        new Path( path + "/MetaData/Units"),
        metaValues
    );
    OdfMetaData metaData = OdfFactory.createOdfMetaData(metaItems);


    // Create OdfInfoItem to contain the value. 
    OdfInfoItem infoItem = OdfFactory.createOdfInfoItem(
        path, 
        values,
        description,
        metaData
    );

    // createAncestors generates O-DF structure from the path of an OdfNode 
    // and returns the root, OdfObjects
    OdfObjects objects = infoItem.createAncestors();

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(name + " pushing data...");

    // Create O-MI write request
    // interval as time to live
    WriteRequest write = OmiFactory.createWriteRequest(
        interval, // ttl
        objects   // O-DF
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
```

Configuration
=============

Now we have an *internal agent*, but to get O-MI Node to run it, we need to compile it to a .jar
file and put it to `deploy` directory, or if compiled with O-MI Node project, `InternalAgentLoader`
will find it from .jar file of the project.

After this we have the final step, open the `application.conf` and add a new object to
`agent-system.internal-agents`. The format is: 

```
"<name of agent>" = {
  language = "<scala or java>"
  class = "<full class path of agent>"
  owns = ["<O-DF path owned by agent>", ...]
  config = <json object to be passed to constructor> 
}
```

Field `owns` is only needed for `ResponsibleInternalAgent`.

Lines to add for our example:

```
    "JavaAgent" = {
      class = "agents.JavaAgent"
      language = "java"
      config = {
        path = "Objects/JavaAgent/sensor"
        interval = 60 seconds
      }
    }
```

Finally you need to restart O-MI Node to update its configuration.

