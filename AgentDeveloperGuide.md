

What are Agents?
================

Agents are small programs that are connect to devices and push received data to
O-MI Node. 

There are two kinds of Agents using different interfaces: 
- *External agents* that push O-DF formatted sensor data to a TCP port of O-MI Node.
- *Internal agents* that are loaded from .jar file and instantiated to be run inside the same 
JVM as O-MI Node. They can also own paths and receive any authorized write to them for futher 
handling. *Internal agents* that owns any paths is called *responsible*.

External Agent
==============

The recommended option for writing data from outside of O-MI Node is to send O-MI write request to it. 
This is prefered way because there are more security options. 

Another way is to write a program that pushes O-DF formatted data to the TCP
port defined by `application.conf`'s omi-service.external-agent-port parameter.
Program can be written with any programming language. See
[the simple python example](https://github.com/AaltoAsia/O-MI/blob/master/agentExample.py).
Starting and stopping of external agents are the user's responsibility.

If your external agent is run on a different computer, you will need to add its IP-address to
`o-mi-service.input-whitelist-ips`. You can also accept input from subnets by adding their masks to
`o-mi-service.input-whitelist-subnets`.

Internal Agent
================

*Internal agents* are classes that extend `InternalAgent` trait. `InternalAgent` trait extends
`Akka.actor.Actor`. `Actor`s need `Props` for creation. `Props` creation is recommended to be done ussing
companion objects. Companion object of *internal agent* extends `trait PropsCreator`. `PropsCreator`'s
`props` method's return type is `InternalAgentProps`, that can only be created using `InternalAgentProps`
object. This is to prevent creating `Props` for `Actor`'s that to not implement `InternalAgent` trait. 

*Internal agents* and their companion objects are loaded from .jar files by `InternalAgentLoader`. It
enforces following rules for them:
- *Internal agent's* class and it's companion object must be found.
- *Internal agent's* class must extend `InternalAgent` trait.
- *Internal agent's* companion object must extend `PropsCreator`.
- Created `Props` must be for *internal agent's* class.
`InternalAgentLoader` creates `Props` using the *internal agent's* companion object and creates an `Actor` using
it. After this `Start` message is send to the *internal agent*. If *internal agent* starts
succesfully, it is added to `AgentSystem`. If any problems occurs in loading, creation or starting,
*internal agent* is stopped and not added to `AgentSystem`.

*Internal agent's* class  only needs to implement methods `start` and `stop` for `InternalAgent` trait.
They are called when respective command is received. For configuration *internal agent* use
typesafe-config and have an abstract field `config`. Easiest way is to define `config` as constructor parameter.
Configuration is found from `application.conf`, and passed to *internal agent's* companion object for 
creation of `Props`.

BasicAgent.scala
----------------
Let's create an *interanal agent*, that gets a path to O-DF InfoItem from configuration and writes random 
values to it every interval given in configuration. First create class `BasicAgent` that extends 
`InternalAgent` and have `config` as constructor parameter. Get interval from config as
FiniteDuration and path as String and create a Path from it.
```Scala
class BasicAgent( override val config: Config)  extends InternalAgent{

  protected val interval : FiniteDuration = config.getDuration("interval", TimeUnit.SECONDS).seconds
	
  protected val path : Path = Path(config.getString("path"))

```
`BasicAgent` must be able to stop processing data, and write values to O-MI Node at every interval.
Because of `BasicAgent` being an `Actor` we can use `context.system.scheduler` to `schedule` a
repeated sending of an immutable message. Method `schedule` returns a `Cancelable`, that we can save
and cancel later. We create a message `case class Uptade()` to be send and a container for
`Cancelable`. Method `start` whole body is inside `Try` so that any nonfatal exceptions are returned
to `AgentSystem` instead of causing `AgentSystem` to terminate the *internal agent*.
```Scala
  //Message for updating values
  case class Update()
  
  //Interval for scheluding generation of new values
  //Cancellable update of values, "mutable Option"
  case class UpdateSchedule( var option: Option[Cancellable]  = None)
  private val updateSchedule = UpdateSchedule( None )
  
  protected def start : Try[InternalAgentSuccess ] = Try{
  
    // Schelude update and save job, for stopping
    // Will send Update message to self every interval
    updateSchedule.option = Some(
      context.system.scheduler.schedule(
        Duration(0, SECONDS), //Delay before first
        interval,
        self,
        Update
      )
    )
  
    CommandSuccessful()
  }
```
To stop `BasicAgent` to stop proccessing data we need to cancel stored `Cancellable` if job is found. 
Again we return a `Try`, but now we throw some exceaptions if something did go wrong.
```Scala
  protected def stop : Try[InternalAgentSuccess ] = Try{
    updateSchedule.option match{
      //If agent has scheluded update, cancel job
      case Some(job: Cancellable) =>
      
      job.cancel() 
      
      //Check if job was cancelled
      if(job.isCancelled){
        updateSchedule.option = None
        CommandSuccessful()
      }else throw CommandFailed("Failed to stop agent.")
       
      case None => throw CommandFailed("Failed to stop agent, no job found.")
    }
  }
```
Now all required methods for `InternalAgent` trait have been implemented, but `BasicAgent` does not
write any data to `AgentSystem`. Let's create method `update`.
```Scala
  //Random number generator for generating new values
  protected val rnd: Random = new Random()
  protected def newValueStr = rnd.nextInt().toString 
  
  //Helper function for current timestamps
  protected def currentTimestamp = new Timestamp(  new java.util.Date().getTime() )
  
  //Update values in paths
  protected def update() : Unit = {

    val timestamp = currentTimestamp
    val typeStr = "xs:integer"

    //Generate new values and create O-DF
    val infoItem = OdfInfoItem( path, Vector( OdfValue( newValueStr, typeStr, timestamp ) ) )

    //createAncestors generate O-DF structure from a node's path and retuns OdfObjects
    val objects : OdfObjects = createAncestors( infoItem )

    //interval as time to live
    val write = WriteRequest( interval, objects )

    //PromiseResults contains Promise containing Iterable of Promises and has some helper methods.
    //First level Promise is used for getting answer from AgentSystem and second level Promises are
    //used to get results of actual writes and from agents that owned paths that this agent wanted to write.
    val result = PromiseResult()

    //Let's fire and forget our write, results will be received and handled througth promiseResult
    parent ! PromiseWrite( result, write )

    //isSuccessful will return combined result or first failed write.
    val succ = result.isSuccessful

    succ.onSuccess{
      case s: SuccessfulWrite =>
      log.debug(s"$name pushed data successfully.")
    }

    succ.onFailure{
      case e: Throwable => 
      log.warning(s"$name failed to write all data, error: $e")
    }
  }
```
Before creating `update()` we create some utility methods for value generation. We start method
`update()` with creating new random value to be writen and generate O-DF structure from stored path.
Then we create `WriteRequest` to be send to `AgentSystem`. We do not use Akka's pattern to get
response from `AgentSystem`, but instead we create a `PromiseResult` to be passed to `AgentSystem`. 
`PromiseResult` contains a `Promise[Iterable[Promise[ResponsibleAgentResponse ] ] ]` and has some helper methods.
`AgentSystem will complete `Promise` in passed `PromiseResult`. Accessing result is done througth `promiseResult`.
Method `isSuccessful` will give us a `Future` of  combined success or first failure occurred.

We need to add `Update` to handled messages. This is not done like with a normal `Actor`. Instead we
override protected method `receiver` with our match case for `Update`.
```Scala
  override protected def receiver = {
    case Update => update
  }
}
```

We have not yet created companion object for `BasicAgent`. It needs to extend `PropsCreator` and 
implement method `props(config: Config )`. Only way to create `InternalAgentProps` is to use it's
companion object. It has all same `apply` methods as `Props` companion object. 
```Scala
object BasicAgent extends PropsCreator {
  def props( config: Config) : InternalAgentProps = { InternalAgentProps(new BasicAgent(config)) }  
}
```

Now we have an *internal agent*, but to get O-MI Node to run it, we need to compile it to a .jar
file and put it to `deploy` directory, or if compiled with O-MI Node project, `InternalAgentLoader`
will find it from project's .jar file.

After this we have the final step, open the `application.conf` and add new object to
`agent-system.internal-agents`. Object's format is: 

```
"<name of agent>" = {
    class = "<class of agent>"
    config = <json object> 
    owns = ["<Path owned by agent>", ...]
}
```

Field `owns` is only needed for `ResponsibleInternalAgent`.

Lines to add for our example:

```
        "BAgent" = {
            class = "agents.BasicAgent"
            config = {
                path = "Objects/BAgent/sensor"
                interval = 60 seconds
            }
        }
```

Finally you need to restart O-MI Node to update its configuration.

ResponsibleAgent.scala
----------------------
We want to make `BasicAgent` to be *responsible* for it's path. Let's create class 
`ResponsibleAgent` for this and implement method `handelWrite` for it.

```Scala
class ResponsibleAgent extends BasicAgent with ResponsibleInternalAgent {

  protected def handleWrite(promise: Promise[ResponsibleAgentResponse], write: WriteRequest) = {

    val promiseResult = PromiseResult()
    parent ! PromiseWrite( promiseResult, write)

    promise.completeWith( promiseResult.isSuccessful ) 
  }
}
```

Because of `AgentSystem` forwards only parts of O-DF struture that are owned by *internal agent* to
the same *internal agent*, we do not need to check them. We are not doing any checks on data this
time, so we write it and complete promise with result. `AgentSystem` writes data to database,
because data was received from the owner of the paths in O-DF of the write request. 

A *responsible internal agent* is ready to be added to O-MI Node.  We add a new object to
`agent-system.internal-agents` in `application.conf`:

```
        "RAgent" = {
            class = "agents.ResponsibleAgent"
            config = {
                path = "Objects/RAgent/sensor"
                interval = 60 seconds
            }
            owns = [ "Objects/RAgent/sensor" ]
        }
```

Now restart O-MI Node to update its configuration.

ODFAgent.scala
---------------

`ODFAgent` is also very simple agent that has path to .xml file and interval as config.  The file contains
an O-DF structure. `ODFAgent` parses xml file for O-DF, and starts random generating values for
`OdfInfoItems` in O-DF Structure.

