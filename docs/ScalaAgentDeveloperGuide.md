ScalaAgent.scala
----------------
Let's create an *interanal agent*, that gets a path to O-DF InfoItem from 
configuration and writes random generated values to it every interval given in 
configuration. First create class `ScalaAgent` that extends `ScalaInternalAgent`.
and have `config` as constructor parameter.



```Scala
package agents

import scala.util.{Random, Success, Failure}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable.{Queue => MutableQueue}

import java.sql.Timestamp;
import java.util.Date
import java.util.concurrent.TimeUnit

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props, Actor}

import com.typesafe.config.Config

import types.Path
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import agentSystem._ 

/**
 * Companion object for ScalaAgent. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 *  @param _config Contains configuration for this agent, as given in application.conf.
 */
object ScalaAgent extends PropsCreator {
  /**
   * Method for creating Props for ScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props( config: Config) : Props = { 
    Props(new ScalaAgent(config)) 
  }  

}

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 */
class ScalaAgent( override val config: Config)  extends ScalaInternalAgent{

  //Interval for scheluding generation of new values, parsed from configuration
  val interval : FiniteDuration= config.getDuration(
    "interval",
    TimeUnit.SECONDS
  ).seconds

  //Target O-DF path, parsed from configuration
  val path : Path = Path(config.getString("path"))

  //Message for updating values
  case class Update()

  //Random number generator for generating new values
  val rnd: Random = new Random()
  def newValueStr : String = rnd.nextInt().toString 

  //Helper function for current timestamps
  def currentTimestamp : Timestamp = new Timestamp(  new java.util.Date().getTime() )

  //Cancellable update of values, "mutable Option"
  case class UpdateSchedule( var option: Option[Cancellable]  = None)
  private val updateSchedule = UpdateSchedule( None )

  /**
   * Method to be called when a Start() message is received.
   */
  def start : InternalAgentResponse  ={

    // Schelude update and save job, for stopping
    // Will send Update() message to self every interval
    updateSchedule.option = Some(
      context.system.scheduler.schedule(
        Duration.Zero,//Delay start
        interval,//Interval between messages
        self,//To
        Update()//Message
      )
    )

    CommandSuccessful()
  }

  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   */
  def stop : InternalAgentResponse = {
    updateSchedule.option match{
      //If agent has scheluded update, cancel job
      case Some(job: Cancellable) =>

        job.cancel() 

        //Check if job was cancelled
        if(job.isCancelled){
          updateSchedule.option = None
          CommandSuccessful()
        } else StopFailed("Failed to stop agent.", None)

      case None => 
        CommandSuccessful()
    }
  }


  /**
   * Method to be called when a Update() message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  def update() : Unit = {

    // Generate new OdfValue 

    // timestamp for the value
    val timestamp : Timestamp = currentTimestamp
    // type metadata, default is xs:string
    val typeStr : String= "xs:integer"
    //New value as String
    val valueStr : String = newValueStr

    val odfValue : OdfValue = OdfValue( newValueStr, typeStr, timestamp ) 

    // Multiple values can be added at the same time but we add one
    val odfValues : Vector[OdfValue] = Vector( odfValue )

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
    val result : Future[ResponseRequest] = writeToNode(write) 

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
    }
  }

  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override  def receive : Actor.Receive = {
    //Following are inherited from ScalaInternalActor.
    //Must tell/send return value to sender, ask pattern used.
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    //ScalaAgent specific messages
    case Update() => update
  }
}
```

Now we have an *internal agent*, but to get O-MI Node to run it, we need to compile it to a .jar
file and put it to `deploy` directory, or if compiled with O-MI Node project, `InternalAgentLoader`
will find it from project's .jar file.

After this we have the final step, open the `application.conf` and add new object to
`agent-system.internal-agents`. Object's format is: 

```
"<name of agent>" = {
  language = "<scala or java>"
  class = "<class of agent>"
  owns = ["<O-DF path owned by agent>", ...]
  config = <json object> 
}
```

Field `owns` is only needed for `ResponsibleInternalAgent`.

Lines to add for our example:

```
    "ScalaAgent" = {
      language = "scala"
      class = "agents.ScalaAgent"
      config = {
        path = "Objects/ScalaAgent/sensor"
        interval = 60 seconds
      }
    }
```

Finally you need to restart O-MI Node to update its configuration.

ResponsibleAgent.scala
----------------------
We want to make `ScalaAgent` to be *responsible* for it's path. Let's create class 
`ResponsibleScalaAgent` for this and implement method `handelWrite` for it.

```Scala
package agents

import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props, Actor}
import agentSystem._ 
import types.Path
import types.OdfTypes._
import types.OmiTypes.WriteRequest
import scala.concurrent.Future
import scala.concurrent.duration._
import java.sql.Timestamp;
import java.util.Date
import scala.util.{Random, Try}
import scala.concurrent.ExecutionContext.Implicits._
import scala.collection.mutable.{Queue => MutableQueue}
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit

/**
 * Companion object for BasciAgents. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 *  @param _config Contains configuration for this agent, as given in application.conf.
 */
object ScalaAgent extends PropsCreator {
  /**
   * Method for creating Props for ScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props( config: Config) : Props = { 
    Props(new ScalaAgent(config)) 
  }  

}

/**
 * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 */
class ScalaAgent( override val config: Config)  extends ScalaInternalAgent{

  //Interval for scheluding generation of new values, parsed from configuration
  val interval : FiniteDuration= config.getDuration(
    "interval",
    TimeUnit.SECONDS
  ).seconds

  //Target O-DF path, parsed from configuration
  val path : Path = Path(config.getString("path"))

  //Message for updating values
  case class Update()

  //Random number generator for generating new values
  val rnd: Random = new Random()
  def newValueStr : String = rnd.nextInt().toString 

  //Helper function for current timestamps
  def currentTimestamp : Timestamp = new Timestamp(  new java.util.Date().getTime() )

  //Cancellable update of values, "mutable Option"
  case class UpdateSchedule( var option: Option[Cancellable]  = None)
  private val updateSchedule = UpdateSchedule( None )

  /**
   * Method to be called when a Start() message is received.
   */
  def start : InternalAgentResponse  ={

    // Schelude update and save job, for stopping
    // Will send Update() message to self every interval
    updateSchedule.option = Some(
      context.system.scheduler.schedule(
        Duration.Zero,//Delay start
        interval,//Interval between messages
        self,//To
        Update()//Message
      )
    )

    CommandSuccessful()
  }

  /**
   * Method to be called when a Stop() message is received.
   * This should gracefully stop all activities that the agent is doing.
   */
  def stop : InternalAgentResponse = {
    updateSchedule.option match{
      //If agent has scheluded update, cancel job
      case Some(job: Cancellable) =>

        job.cancel() 

        //Check if job was cancelled
        if(job.isCancelled){
          updateSchedule.option = None
          CommandSuccessful()
        } else CommandFailed("Failed to stop agent.")

      case None => 
        CommandSuccessful()
    }
  }


  /**
   * Method to be called when a Update() message is received.
   * Made specifically for this agent to be used with the Akka scheduler.
   * Updates values in target path.
   */
  def update() : Unit = {

    // Generate new OdfValue 

    // timestamp for the value
    val timestamp : Timestamp = currentTimestamp
    // type metadata, default is xs:string
    val typeStr : String= "xs:integer"
    //New value as String
    val valueStr : String = newValueStr

    val odfValue : OdfValue = OdfValue( newValueStr, typeStr, timestamp ) 

    // Multiple values can be added at the same time but we add one
    val odfValues : Vector[OdfValue] = Vector( odfValue )

    // Create OdfInfoItem to contain the value. 
    val infoItem : OdfInfoItem = OdfInfoItem( path, odfValues)

    // Method createAncestors generates O-DF structure from the path of an OdfNode 
    // and returns the root, OdfObjects
    val objects : OdfObjects =  infoItem.createAncestors

    // This sends debug log message to O-MI Node logs if
    // debug level is enabled (in logback.xml and application.conf)
    log.debug(s"$name pushing data...")

    // Create O-MI write request
    // interval as time to live
    val write : WriteRequest = WriteRequest( objects, None, interval )

    // Execute the request, execution is asynchronous (will not block)
    val result : Future[ResponsibleAgentResponse] = writeToNode(write) 

    // Do something if execution was of request was successful
    result.onSuccess{
      case s: SuccessfulWrite =>
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.debug(s"$name pushed data successfully.")
    }

    // Do something if execution was of request failed
    result.onFailure{
      case e: Throwable => 
        // This sends debug log message to O-MI Node logs if
        // debug level is enabled (in logback.xml and application.conf)
        log.warning(s"$name failed to write all data, error: $e")
    }
  }

  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override  def receive : Actor.Receive = {
    //Following are inherited from ScalaInternalActor.
    //Must tell/send return value to sender, ask pattern used.
    case Start() => sender() ! start 
    case Restart() => sender() ! restart 
    case Stop() => sender() ! stop 
    //ScalaAgent specific messages
    case Update() => update
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
    "ResponsibleScalaAgent" = {
      class = "agents.ResponsibleScalaAgent"
      language = "scala"
      owns = [ "Objects/RAgent/sensor" ]
      config = {
        path = "Objects/ResponsibleScalaAgent/sensor"
        interval = 60 seconds
      }
    }
```

Now restart O-MI Node to update its configuration.

ODFAgent.scala
---------------

`ODFAgent` is also very simple agent that has path to .xml file and interval as config.  The file contains
an O-DF structure. `ODFAgent` parses xml file for O-DF, and starts random generating values for
`OdfInfoItems` in O-DF Structure.

