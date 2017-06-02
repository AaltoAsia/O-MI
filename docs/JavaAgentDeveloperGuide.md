Java Agent Developer Guide
======

Agents implemented using Java
-------

* [JavaTemplateAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaTemplateAgent.java), 
template class for starting development of your own agents.

* [JavaAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaAgent.java), simplest InternalAgent.
Takes an O-DF path of InfoItem and writes random generated values to it.

* [JavaRoomAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaRoomAgent.java), 
creates O-DF structure using OdfFactory and writes random generated values to it.

* [JavaFileAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaFileAgent.java).
parses O-DF structure from given file ands writes random generated values to it.

Development enviroment
------

(Note that it is also possible to develop with the O-MI Node environment with 
sbt (see Readme.md). Directory for the agents is at `Agents/src/main/java/`)

1. Get the release package
  a. Download the latest release
  b. or you can create a new release package by running `sbt release` (see 
  Readme.md) that creates release packages to `target/scala-2.11/`.
2. Add O-MI Node's `lib` directory to the project libraries in your IDE. `lib` 
  directory can be found add root level of release package.



Java agent example
--------

*Internal agents* are classes implementing `InternalAgent` interface. 
`InternalAgent` interface extends Akka's `Actor` interface. This makes every 
*internal agent* to an [Akka Actor](http://doc.akka.io/docs/akka/2.4/java/untyped-actors.html) which is a higher level abstraction of a thread, see the Akka documentation for details.

To implement *internal agent* using Java you need to create a class extending 
`JavaInternalAgent`. `JavaInternalAgent` is abstract class providing some 
default and utility members. It also extends Akka's `UntyppedActor`. 
We enforce AKka's recommended practice for `Props` creation by requiring every 
`JavaInternalAgent` to have `public static 
Props props(final `[Config](https://github.com/typesafehub/config)` config)`.

Let's copy 
[`Agents/src/main/java/agents/JavaTemplateAgent.java`](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/java/agents/JavaTemplateAgent.java) 
to start developing a new *internal agent*:

```Java
package agents;

import akka.japi.Creator;
import akka.actor.Props;
import akka.actor.ActorRef;
import akka.dispatch.Mapper;
import akka.dispatch.OnSuccess;
import akka.dispatch.OnFailure;

import com.typesafe.config.Config;

import agentSystem.*;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.OmiResult;
import types.OmiTypes.Results;

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
  static public Props props(final Config config, final ActorRef requestHandler, final ActorRef dbHandler) {
    return Props.create(new Creator<JavaTemplateAgent>() {
      //Random serialVersionUID, for serialization.
      private static final long serialVersionUID = 35735155L;

      @Override
      public JavaTemplateAgent create() throws Exception {
        return new JavaTemplateAgent(config, requestHandler, dbHandler);
      }
    });
  }

  // Constructor
  public JavaTemplateAgent(Config config, final ActorRef requestHandler, final ActorRef dbHandler){
    super(requestHandler,dbHandler);
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

}
```
`JavaTemplateAgent` implements everything that is required by O-MI Node to run it, 
but it does not do anything. It has implementation of method `props` and 
constructor taking a [Typesafe Config](https://github.com/typesafehub/config),
`requestHandler` and `dbHandler` as parameters. 

For this example let's create an *interanal agent*, that creates O-DF structure 
from single O-DF Path of an InfoItem and and writes random generated values to it. 
First, let's replace all  `JavaTemplateAgent`s with `JavaAgent`.

To create a O-DF `Path` we get field `"path"` of type string from `config`.
We also want to be able to change how often our agent will generate new values and 
write them without recompiling. So we parse a `FiniteDuration` from `config` 
and save it as seconds to variable `interval`.
Variable `interval` defines duration between two writes to O-MI Node. 
We also schedule an repeated sending of message `"Update"` to ourself.

```Java
  protected Config config;
  protected FiniteDuration interval;
  protected Path path;
  protected Cancellable intervalJob;

  // Constructor
  public JavaAgent(Config conf, final ActorRef requestHandler, final ActorRef dbHandler){
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
```

To have our `JavaAgent` to react on `"Update"` message we override the method 
`void onReceive` to check if received message is type of `String` and is equal
to `"Update"`. If message matches, then method `update()` is called.

```Java
  /**
   * Method that is inherited from akka.actor.UntypedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public void onReceive(Object message){
    if( message instanceof String) {
      String str = (String) message;
      if( str.equals("Update"))
        update();
      else super.onReceive(message);
    } else super.onReceive(message);
  }
```

In `update` method we random generate a value and create an `OdFValue` for it
and an `OdfInfoItem` for path. Creating `Odf*` types is done with `OdfFactory`
that has static factory method for most of the types. To write the new value to
the O-MI Node's database we create a `WriteRequest` using `OmiFactory` that has 
also static factory methods for most of the O-MI requests. Created write request
is send to the database to be handled asynchronously. This returns a `Future`
that will contain the result of the write. 

```Java
  //Random for generating new values for path.
  protected Random rnd = new Random();

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

    // Create OdfInfoItem to contain the value. 
    OdfInfoItem infoItem = OdfFactory.createOdfInfoItem(
        path, 
        values
    );

    // createAncestors generates O-DF structure from the path of an OdfNode 
    // and returns the root, OdfObjects
    OdfObjects objects = infoItem.createAncestors();

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(name() + " pushing data...");

    // Create O-MI write request
    // interval as time to live
    WriteRequest write = OmiFactory.createWriteRequest(
        interval, // ttl
        objects   // O-DF
    );
    

    // Execute the request, execution is asynchronous (will not block)
    Future<ResponseRequest> result = writeToDB(write);

    ExecutionContext ec = context().system().dispatcher();
    //Asynchronous handling of completion of future result
    result.onSuccess(new LogResult(), ec);
    result.onFailure(new LogFailure(), ec);
  }
```

Because we do not want to block the processing of the agent, we handle the
results asynchronously too. To get to know haw to handle `Future`s refer to 
[Akka's Future documentation](http://doc.akka.io/docs/akka/2.4/java/futures.html).

```Java
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

```

If O-MI Node stops our `JavaAgent`, we need stop sending `"Update"` message to
ourself. After an `Actor` is stopped it will process current message and
call `postStop` method for clean up. To cancel the sending of the `"Update"`
message we just call `cancel` method of the previously stored `Cancelllable` in
the `postStop` method.

```Java
  @Override
  public void postStop(){
      intervalJob.cancel();  //Cancel intervalJob
  }
```

Now we have an *internal agent*, but to get O-MI Node to run it, we need to 
compile it to a .jar file and put it to `deploy` directory, or if compiled with
O-MI Node project, `InternalAgentLoader` will find it from project's .jar file.

After this we have the final step, open the `application.conf` and add new object to
`agent-system.internal-agents`. Object's format is: 

```
    {
      name = "<name of agent>"
      class = "<full class path of agent>"
      language = "<scala or java>"
      responsible = {
        "<O-DF path that agent is responsible for>" = "<request types that agent handles, w= write and c = call>",
        ...
      }
      ... <Other agent specific fields.>
    }
```

Field `responsible` is only needed for `ResponsibleInternalAgent`.

Lines to add for our example:

```
    {
      name = "JavaAgent" 
      class = "agents.JavaAgent"
      language = "java"
      path = "Objects/JavaAgent/sensor"
      interval = 60 seconds
    }
```
Finally you need to restart O-MI Node to update its configuration.

Responsibility
----------------------
TODO
