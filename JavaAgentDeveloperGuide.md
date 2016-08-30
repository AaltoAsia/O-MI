Java Agent Developer Guide
======

Agents implemented using Java
-------

* [JavaTemplateAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaTemplateAgent.java), 
template class for starting to development of your own agents.

* [JavaAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaAgent.java), simplest InternalAgent.
Takes a O-DF path of InfoItem and write random generated values to it.

* [JavaRoomAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaRoomAgent.java), 
creates O-DF structure using OdfFactory and writes random generated values to it.

* [JavaFileAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaFileAgent.java).
parses O-DF structure from given file ands write random generated values to it.

Development enviroment
------
Add O-MI Node's `lib` directory to your IDE. `lib` directory can be found add 
root level of release package. You can create release package by running `sbt release`, 
that creates release .jar file to `target/scala-2.11/`.

Java agent example
--------
*Internal agents* are classes implementing `InternalAgent` interface. 
`InternalAgent` interface extends Akka's `Actor` interface. This makes every 
*internal agent* to [Akka Actor](http://doc.akka.io/docs/akka/2.4/java/untyped-actors.html).
To implement *internal agent* using Java you need to create a class extending 
`JavaInternalAgent`. `JavaInternalAgent` is abstract class providing some 
default and utility members. It also extends Akka's `UntyppedActor`. 
We enforce AKka's recommended practice for `Props` creation by requiring every 
`JavaInternalAgent` to have public static `Props props(final [Config config](https://github.com/typesafehub/config))`.

Let's copy [`Agents/src/main/java/agents/JavaTemplateAgent.java`](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaTemplateAgent.java) to start developing new *internal agent*:
```Java
package agents;

import akka.japi.Creator;
import akka.actor.Props;

import com.typesafe.config.Config;

import agentSystem.*;

/**
 * Template class for Java agents.
 */
public class JavaTemplateAgent extends JavaInternalAgent {
  /**
   *  THIS STATIC FACTORY METHOD MUST EXISTS FOR JavaInternalAgent 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that
   *  <a href="http://doc.akka.io/docs/akka/current/java/untyped-actors.html#Recommended_Practices">Akka recommends to</a>.
   *
   *  @param _config Contains configuration for this agent, as given in application.conf.
   *  <a href="https://github.com/typesafehub/config">Typesafe config</a>.
   */
  static public Props props(final Config config) {
    return Props.create(new Creator<JavaTemplateAgent>() {
      //Random serialVersionUID, for serialization.
      private static final long serialVersionUID = 35735155L;

      @Override
      public JavaTemplateAgent create() throws Exception {
        return new JavaTemplateAgent(config);
      }
    });
  }

  // Constructor
  public JavaTemplateAgent(Config config){
  }

  /**
   * Method to be called when a Start() message is received.
   */
  @Override
  public InternalAgentResponse start(){
      return new CommandSuccessful();
  }

  /**
   * Method to be called when a Stop() message is received.
   */
  @Override
  public InternalAgentResponse stop(){
      return new CommandSuccessful();
  }
}
```
`JavaTemplateAgent` implements everything that is required by O-MI Node to run it, 
but it does not do anything. It has implementation of methods `props`, `start` and `stop`, and
constructor taking a [Typesafe Config](https://github.com/typesafehub/config) as parameter.

For this example let's create an *interanal agent*, that creates O-DF structure 
and writes random generated values to it. Firstly let's replace all `JavaTemplateAgent`s with `JavaRoomAgent`.

We want to be able to change how often our agent will generate new values and 
write them without recompiling. So we parse a `FiniteDuration` from Config 
passed as constructor parameter and save it as seconds to variable `interval`.
Variable `interval` defines duration between two writes to O-MI Node. We also 
create our O-DF structure by calling `createOdf` method in constructor. 

```Java
  //Interval between writes 
  protected FiniteDuration interval;

  //Random for generating new values for path.
  protected Random rnd = new Random();

  //Our O-DF structure
  protected OdfObjects odf;

  // Constructor
  public JavaRoomAgent(Config conf){

    // Parse configuration for interval
    interval = new FiniteDuration(
            conf.getDuration("interval", TimeUnit.SECONDS),
            TimeUnit.SECONDS);	

    odf = createOdf();
  }
```

In Java instead of calling Scala code directly, we use `OdfFactory` and `OmiFactory` to create
Scala classes. To create `OdfObjects` we need to create a `Vector` of `OdfObject`s and call 
`OdfFactory.createOdfObjects` with it. Let's create a O-DF Object in O-DF path "Objects/ExampleRoom" by calling method `createExampleRoom` and it to the `Vector` and then create and return
a `OdfObjects`. We also set count of writes to zero.

```Java
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

```
In `createExampleRoom` we create `OdfObject` for O-DF Path with "Objects/ExampleRoom", 
O-DF's Path's constructor takes O-DF path as `String` with following format: 
"Objects/<Id of O-DF Object>/../<Id of O-DF Object>/<Name of O-DF InfoItem>".

`OdfDescription` for `OdfObject` is created from a `String`. It could also have 
language specified as second parameter. 

For child `OdfObject`s and `OdfInfoItem`s we create two `Vector`s and populae them
with corresponding elements. `createLocation` creates `OdfInfoItem` that contains
location of ExampleRoom in format specifed in 
[Warp10 integration documentation](https://github.com/AaltoAsia/O-MI/blob/warp10integration/warp10-documentation.md).
We do not cover it in this example because of later explained `createSensor` does 
mostly the same thing.

```Java
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
        objects,
        description
    );
  }
```

In `createSensorBox` we do pretty much the same that in `createExampleRoom`, but take O-DF `Path` of 
parent as a parameter. O-DF Path can be converted to a sequence containing O-DF Object Ids and InfoItem name.
By converting parent O-DF Object's path to a array we can extract it's Id. This time we do not have any child 
O-DF Objects, but we child O-DF InfoItems by calling `createSensor` with a name, units measured and 
a O-DF path.

```Java
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
        objects,
        description
    );
  }
```

In `createSensor` we create new O-DF `Path` and extract parent O-DF Object's id like in 
`createSensor`, but we also create `OdfMetaData` and `OdfValue`s for `OdfInfoItem` 
at the new `Path`. 

`OdfValue` has a type parameter defining type of stored value. Currently
only basic datatypes work with it and everything else is stored as `String`.
Using `java.lang.Object` as type parameter avoids problems having multiple differently
typed values in same collection. Value of `typeStr` can be any built-in data type defined in
[XML Schema documentation](https://www.w3.org/TR/xmlschema-2/#built-in-datatypes).

`OdfInfoItem`s in `OdfMetaData` need O-DF `Path`s. The recommended format for these `Path`s is
".../<name of O-DF InfoItem>/MetaData/<name of O-DF InfoItem in MetaData>".


```Java
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
    OdfValue<Object> metaValue = OdfFactory.createOdfValue(
        unit, "xs:string", timestamp
    );
    metaValues.add(metaValue);

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
```

Now we have generated a O-DF structure using `OdfFactory`, but we have not writen it to
O-MI Node yet. Let's create method `update` that is called when our agent receives "Update".
Updating O-DF structure is larger operation so we put it in `updateOdf`, but call it as first 
in `update`.

We do not want write metadate multiple times so we call `allMetaDatasRemoved` method for `odf`
before writing second time. Then we create O-MI Write request using `OmiFactory`. 

Writing to O-MI Node happens asyncronously and need a timeout to wait for results. `writeToNode`
method returns `Future` that contains our results. `Future` is placeholder objet for a value that 
may net yet exist. Its value is supplied in future by a asyncronous task. We could wait for it 
to complete. but that coulde block processing. So instead we handle it's result also asyncronously. 
To do so we need create two classes.

```Java
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
```

LogResult and LogFailure simply logs information about result.
Please prefer to [Akka's Future documentation](http://doc.akka.io/docs/akka/2.4/java/futures.html).

```Java
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

```

Let's go back to updating O-DF structure's values and implement `updateOdf`.
We take all InfoItems and their values, then generate new values and store them
as Path and value pairs, then replace old values with new values.

Two important things here. First thing is that all classes starting with "Odf" are immutable so for
better performance we want to do as much changes at once with single copying of O-DF structure.

Second thing is that scala.collection.JavaConversions contains useful converters between
Scala and Java, and that types.JavaHelpers has also some helper methods.

New values generated will differ less than 5% from old value with probability of 68%.

```Java
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
```

We have not yet really implemented `start` and `stop` methods needed by our agent.
We wanted to have method `update` called every time an `"Update"` message was received,
but we have not yet send such message to our agent. We also want to write after every 
interval of seconds. To do so we can use `ActorSystem`'s scheduler to schedule repeated 
sending of message on every interval. We want to be able to stop agent from receiving 
`"Update"` messages. This can be done with return value of `schedule` message, so we store it
to `intervalJob`.

If something throws an exception during `start` method we want to still answer to sender 
of command. If we do not answer, the sender will have to wait until the asyncronous call
timeouts. 

In Akka every Actor have a supervisor, usually parent Actor or ActorSystem, that
has SupervisorStrategy for handling exceptions thrown by their childs. Options for handling 
exceptions are limited, so agent should handle all exception by itself and only throw exception
when nothing can be done and agent should be terminated.

```Java
  protected Cancellable intervalJob;
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
```

Another method that we have not implemented is `stop`. When `Stop()` message is received we want
to stop writing data to O-MI Node. We saved our scheduled sending of `"Update"` message so that 
it could be stopped. Now we simple check if `intervalJob` exist and cancel it.

Scala `Option`s are objects that may contain `Some` value. Method `apply` is factor method that creates 
an `Option` with given value or if given `null`, creates an empty `Option` containing `None`. With this
Scala can avoid checking for variables having possibly `null` as value.

```Java
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
```

We have implemented all methods in original templatep. But because of using custom `"Update"`
message as our way to implement main loop, we have to add it to handled messages. To  handle
receiving of `"Update"` we have to override Akka `UntypedActor`'s method `onReceive(Object message)`
Because we want to keep handling other messages too, let's copy `JavaInternalAgent`'s implementation.

We need to check type of received `message` to be able to cast it to correct type. For predefined
`Start()`, `Stop()` and `Restart()` we want to call corresponding methods and tell the result to
sender of message and tell which Actor answered.

For our custom `"Update"` message we want just run method `update`.

```Java
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
```

Now we have created new agent but we need to add some configuration to `application.conf` so that
O-MI Node actually runs our agent. 

For full source code look at: [JavaRoomAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaRoomAgent.java), 

Configuration
------

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
  config = <json object to be passed to constructor of agent> 
}
```

Field `owns` is only needed for `ResponsibleInternalAgent`.

Lines to add for our example:

```
    "ExampleRoom" = {
      class = "agents.JavaRoomAgent"
      language = "java"
      config = {
        interval = 60 seconds
      }
    }
```

Finally you need to restart O-MI Node to update its configuration.

