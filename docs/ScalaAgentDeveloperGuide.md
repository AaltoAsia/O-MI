Scala Internal Agent Developer Guide
=============

[ScalaAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/scala/agents/ScalaAgent.java)

Let's create an *internal agent*, that gets a Path of an O-DF InfoItem from 
configuration and writes random generated values to it on every interval given in 
configuration. 

To create an *internal agent* with scala we only need to create a class that
extends abstract `ScalaInternalAgentTemplate` class and it's companion object
that extends `PropsCreator`.

```Scala
object ScalaAgent extends PropsCreator {
  def props( 
    config: Config,
    requestHandler: ActorRef,
    dbHandler: ActorRef
  ) : Props = { 
    Props(
      new ScalaAgent(
        config, 
        requestHandler,
        dbHandler
      )
    ) 
  }  
}

class ScalaAgent(
  val config: Config,
  requestHandler: ActorRef,
  dbHandler: ActorRef
) extdends ScalaInternalAgentTemplate(
  requestHandler,
  dbHandler
){
  ...
}
```

Because of `ScalaInternalAgentTemplate` takes `requestHandler` and `dbHandler`
as constructor paramaeters, we need to have them as constructor parameters of
our `ScalaAgent`. To implement `PropsCreator` for our companion object we have 
implement `props` method, that takes `config`,`requestHandler` and `dbHandler` 
as parameters. Because we may want to have config stored while agent is running 
we pass it as constructor parameter to our `ScalaAgent` instead of extracting
configuration from it before creating `ScalaAgent`. 

In constructor of `ScalaAgent` we want to parse a `Path` of an O-DF InfoItem and
a duration, that defines how long `ScalaAgent` will wait between writing new
value to the servers database. The config passed to the agent is the same
configuration object that defines all information thot O-MI Node needs to
instantiate it. For the Path of the InfoItem we get field "path" of type string from the config
and create a `Path` from it, and for interval duration between write we get
field "interval" of type duration as seconds from the config.

```Scala
class ScalaAgent( 
  val config: Config,
  requestHandler: ActorRef, 
  dbHandler: ActorRef
)  extends ScalaInternalAgentTemplate(requestHandler, dbHandler){


  //Target O-DF path, parsed from configuration
  val path : Path = Path(config.getString("path"))

  //Interval for scheluding generation of new values, parsed from configuration
  val interval : FiniteDuration= config.getDuration(
    "interval",
    TimeUnit.SECONDS
  ).seconds

```

To have `ScalaAgent` write to O-MI Node's database we create a `case class
Update()` to be our message and schedule a cancellable job that send our
`Uptade()` message to us after every `interval`. We store the cancellable job so
that we can cancel it, if O-MI Node stops our `ScalaAgent`.

```Scala
  //Message for updating values
  case class Update()

  // Schelude update and save job, for stopping
  // Will send Update() message to self every interval
  private val updateSchedule: Cancellable= context.system.scheduler.schedule(
    Duration.Zero,//Delay start
    interval,//Interval between messages
    self,//To
    Update()//Message
  )

```

To write new generated values to O-MI Node's database we create method `update`.

```Scala
  //Random number generator for generating new values
  val rnd: Random = new Random()
  def newValueStr : String = rnd.nextInt().toString 

  //Helper method for getting current timestamps
  def currentTimestamp : Timestamp = new Timestamp(  new java.util.Date().getTime() )

  /**
   * Method to be called when a Update() message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  def update() : Unit = {

    // Generate new OdfValue[Any](value, type, timestamp) 
    val odfValue : OdfValue[Any] = OdfValue( newValueStr, "xs:integer", currentTimestamp ) 

    // Multiple values can be added at the same time but we add one
    val odfValues : Vector[OdfValue[Any]] = Vector( odfValue )

    // Create OdfInfoItem to contain the value. 
    val infoItem : OdfInfoItem = OdfInfoItem( path, odfValues)

    // Method createAncestors generates O-DF structure from the path of an OdfNode 
    // and returns the root, OdfObjects
    val objects : OdfObjects =  infoItem.createAncestors

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(s"$name writing data...")

    // Create O-MI write request
    // interval as time to live
    val write : WriteRequest = WriteRequest( objects, None, interval )

    // Execute the request, execution is asynchronous (will not block)
    val result : Future[ResponseRequest] = writeToDB(write) 

    // Asynchronously handle request's execution's completion
    result.onComplete{
      case Success( response: ResponseRequest )=>
        log.info(s"$name wrote got ${response.results.length} results.")
        response.results.foreach{ 
          case wr: Results.Success =>
            log.info(s"$name wrote paths successfully.")
          case ie: OmiResult => 
            log.warning(s"Something went wrong when $name writed, $ie")
        }
      case Failure( t: Throwable) => 
        log.warning(s"$name's write future failed, error: $t")
    }
  }
```

To have method `update` called on everytime we receive `Update()` message we
need to add following to our `receive` method.

```Scala
  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override def receive : Actor.Receive = {
    //ScalaAgent specific messages
    case Update() => update
  }
```

If O-MI Node stops our `ScalaAgent`, we need stop sending `Update()` message to
ourself. After an `Actor` is stopped it will process current message and
call `postStop` method for clean up. To cancel the sending of the `Update()`
message we just call `cancel` method of the previously stored `Cancelllable` in
the `postStop` method.
```Scala
  /**
   * Method to be called when Agent is stopped.
   * This should gracefully stop all activities that the agent is doing.
   */
  override def postStop : Unit = {
    updateSchedule.cancel()
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
      name = "ScalaAgent" 
      class = "agents.ScalaAgent"
      language = "scala"
      path = "Objects/ScalaAgent/sensor"
      interval = 60 seconds
    }
```

Finally you need to restart O-MI Node to update its configuration.

Responsibility
----------------------

[ResponsbileScalaAgent](https://github.com/AaltoAsia/O-MI/blob/development/Agents/src/main/scala/agents/ResponsibleAgent.java)

We want to make `ScalaAgent` to be *responsible* for it's path. 
Let's create class `ResponsibleScalaAgent` that extends `ScalaAgent` with
`ResponsibleScalaInternalAgent` and implement method `handelWrite` for it.

```Scala
  override protected def handleWrite(write: WriteRequest) : Future[ResponseRequest] = {
    //All paths in write.odf is owned by this agent.
    //There is nothing to check or do for data so it is just writen. 

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.info(s"$name pushing data received through AgentSystem.")

    // Asynchronous execution of request 
    val result : Future[ResponseRequest] = writeToDB(write)

    // Asynchronously handle request's execution's completion
    result.onComplete{
      case Success( response: ResponseRequest )=>
        response.results.foreach{ 
          case wr: Results.Success =>
            // This sends debug log message to O-MI Node logs if
            // debug level is enabled (in logback.xml and application.conf)
            log.debug(s"$name wrote paths successfully.")
          case ie: OmiResult => 
            log.warning(s"Something went wrong when $name writed, $ie")
        }
      case Failure( t: Throwable) => 
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.warning(s"$name's write future failed, error: $t")
        Responses.InternalError(t)
    }
    result.recover{
      case t: Throwable => 
      Responses.InternalError(t)
    }
  }
```

We also need to modify our receive to call `handleWrite` when `WriteRequest` is
received. With `respondFuture` method we make sure that `WriteRequest` is
answered correctly. 
```Scala
  override  def receive : Actor.Receive = {
    //Following are inherited from ResponsibleScalaInternalActor.
    case write: WriteRequest => respondFuture(handleWrite(write))
    //ScalaAgent specific messages
    case Update() => update
  }
```

We need to create companion object for our `ResponsibleScalaAgent` and add
following to `application.conf` to run it.
```
    {
      name = "ResponsibleScalaRAgent" 
      class = "agents.ResponsibleScalaAgent"
      language = "scala"
      responsible = {
        "Objects/RSAgent/" = "w"
      }
      path = "Objects/RSAgent/sensor"
      interval = 60 seconds
    }
```
The "w" assigned to path means that our agent is only responsible for 
`WriteRequest`s and does not receive or handle `CallRequest`s for given path.

Now restart O-MI Node to update its configuration.


ODFAgent.scala
---------------

`ODFAgent` is also very simple agent that has path to .xml file and interval as config.  The file contains
an O-DF structure. `ODFAgent` parses xml file for O-DF, and starts random generating values for
`OdfInfoItems` in O-DF Structure.

