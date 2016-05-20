

What are Agents?
----------------
Agents are small programs that connect to sensors and push received data to
O-MI Node. 

There are two kind of Agents using different interfaces: 
- *External agents* that push O-DF formatted sensor data to a TCP port of O-MI Node.
- *Internal agents* that can be loaded from .jar file and instantiated to be run inside same 
JVM as O-MI Node. They can also own paths, and receive any authorized write to them for futher 
handling. *Internal agents* that owns at least a path is called *responsible*.

External Agent
--------------
All you need to do is to write a program that pushes O-DF formatted data to the TCP
port defined by `application.conf`'s omi-service.external-agent-port parameter.
Program can be written with any programming language. See
[the simple python example](https://github.com/AaltoAsia/O-MI/blob/master/agentExample.py).
Starting and stopping of external agents are the user's responsibility.

Another option for writing data from outside of O-MI Node is to send O-MI write request to it. 
This is refered for better security. There is also possibility to use Shibboleth authentication
to get permission for writing.

If your external agent is run on a different computer, you will need to add its IP-address to 
`o-mi-service.input-whitelist-ips`. You can also accept input from subnets by adding 
their masks to `o-mi-service.input-whitelist-subnets`.

Internal Agent
----------------
*Internal agents* are classes loaded from .jar files to `AgentSystem` by `InternalAgentLoader`.
`InternalAgentLoader` instantiate *internal agents* and send `Configure` and `Start` commands
to it. If all three step were successful, *internal agent* is added to `AgentSystem`. After 
this *internal agent* can write data to O-MI node through `AgentSystem` by sending
a `PromiseWrite` to `AgentSystem`. How *internal agent* get data is leaved for itself to
implement. If *internal agent* is *responsible*, it also must be able to handle 
`ResponsibleWrite` with `handleWrite` method.

*Internal agent*'s name, class and configuration is read from 'applicantion.conf`. 
If agent owns any paths, are they also read from `application.conf`. 

Continue reading for more detailled explanitation of class structure of `InternalAgent` or 
skip to `BasicAgent` for example implementation.

`InternalAgent` is a trait extending `Actor` with `ActorLogging` and `Receiving`.
It has helper two methods, `name` for accessing its name in `ActorSystem` and `parent` for
getting  `ActorRef` of `AgentSystem`. It also has four abstract methods each handling received 
respective command received from `AgentSystem`. `Receiving` trait is used to force handling of 
commands, because Akka's ask pattern is used when commands are send from `AgentSystem`.

`Receining` trait implements two 
methods. Method `receiver` adds given `Actor.Receive` to `receivers` so that it is called if 
there is not matching case statement for received message in any previously added 
`Actor.Receive`. Another final method is receive that calls receivers. 

`InternalAgent` calls `receiver` method adding Start, Restart, Stop and Configure 
commands to handled commands so that command's respective method's return value is send back 
to sender, `AgentSystem`. Now creating an InternalAgent means only creating a class 
extending `InternalAgent` and implementing metods: `start`, `stop`, `restart`, and 
`configure(config: String)`.

To write data to O-MI Node, *internal agent* send a `PromiseWrite` containing a `WriteRequest` 
to `AgentSystem`.

`ResponsibleInternalAgent` trait extends `InternalAgent` trait with `handleWrite` method witch
is called when *responsible internal agent* receives `ResponsibleWrite' from `AgentSystem`.
`ReponsibleWrite` contains a `Promise` that needs to be complete with 
a `ResponsibleAgentResponse`. If received write in `ResponsibleWrite` was okay and no futher 
processing is needed, *responsible internal agent* sends it with `PromiseWrite` to
`AgentSystem`, that accepts writes to ownerless paths and paths owned by sender automaticly.

###BasicAgent.scala
We want to create simple *internal agent* that takes a path as config string and writes new 
values to it every specified interval. First we create `class BasicAgent` that 
`extends InternalAgent`. We need to implement following methods: `start`, `restart`, `stop`
and `configure(config: String)`. `AgentSystem` will first send `Configure(config: String)` so
sets start with method `configure( config: String )`:
```Scala
package agents

import agentSystem._ 
import types._
import types.OdfTypes._
import types.OmiTypes._
import akka.util.Timeout
import akka.actor.Cancellable
import akka.pattern.ask
import scala.util.{Success, Failure}
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import scala.concurrent._
import scala.concurrent.duration._
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;
import scala.concurrent.ExecutionContext.Implicits._

class BasicAgent  extends InternalAgent{
  //Path of owned O-DF InfoItem, Option because ugly mutable state
  var pathO: Option[Path] = None
  protected def configure(config: String ) : InternalAgentResponse = {
      pathO  = Some( Path(config) )
      CommandSuccessful("Successfully configured.")
  }
```
Because of straigth forward passing of `config` as `String`, `BasicAgent` can do anything it 
wants for configuration. In this case  we will just create a `Path` from it and save it in 
variable `pathO`. We must return a `CommandSuccessful` to `AgentSystem` so that it knows that c
onfiguration was successful. After successful configuration `AgentSystem` will send `Start` 
command to *internal agent*. So lets implement `start` method next.

```Scala
  //Message for updating values
  case class Update()
  //Interval for scheluding generation of new values
  val interval : FiniteDuration = Duration(60, SECONDS) 
  //Cancellable update of values, Option because ugly mutable state
  var updateSchelude : Option[Cancellable] = None
  protected def start = {
    // Schelude update and save job, for stopping
    // Will send Update message to self every interval
    updateSchelude = Some(context.system.scheduler.schedule(
      Duration(0, SECONDS),
      interval,
      self,
      Update
    ))
    CommandSuccessful("Successfully started.")
  }
```
We want to update path value for every interval. Because *internal agents* are `Actor`s we can 
use `system.scheluder` to schelude repeated sending of a message to `BasicAgent`. First we 
create immutable message `Update()` and `interval` variable. We want to be able to stop 
`BasicAgent` from updating values. This is achieved by saving `Cancelable` created by
scheluding. Scheluding is done by calling `context.system.scheduler.schedule(...)`. We are not
interrested with first parameter defining delay. Second parameter is interval witch sending is 
repeated. Third parameter is `ActorRef` of `Actor` that receives a message. Fourth parameter is
a message to be send. Again we must return `CommandSuccessful` to `AgentSystem`. After starting
*internal agent* successfully `AgentSystem` will not send more messages with out receiving comm
and to do so. Other commands still need to be implemented. `Stop` command is used by `restart` 
command so lets implemented it first.

```Scala
  protected def stop = updateSchelude match{
      //If agent has scheluded update, cancel job
      case Some(job) =>
      job.cancel() 
      //Check if job was cancelled
      job.isCancelled  match {
      case true =>
        CommandSuccessful("Successfully stopped.")
      case false =>
        CommandFailed("Failed to stop agent.")
    }
    case None => CommandFailed("Failed to stop agent, no job found.")
  }
```
To stop `BasicAgent` from updating values, we need to cancel scheluded repeated message
sending. Calling `cancel()` for `job` returns true if cancellation was successful, but job may
have been cancelled allready and could return `false`. So we check `job`'s status with 
`isCancelled` and return result to `AgentSystem`.

```Scala
//Restart agent, first stop it and then start it
protected def restart = {
    stop match{
        case success  : InternalAgentSuccess => start
        case error    : InternalAgentFailure => error
    }
}
```
`BasicAgent now has all functionality required by `InternalAgent` trait, but it does not write
any data to O-MI Node. Lets implement update method that writes dato to O-MI Node.
```Scala
  //Random number generator for generating new values
  val rnd: Random = new Random()
  def newValueStr = rnd.nextInt().toString 
  //Helper function for current timestamps
  def currentTimestamp = new Timestamp(  new java.util.Date().getTime() )
  //Update values in paths
  def update() : Unit = {
    pathO.foreach{ //Only run if some path found 
      path => 
      val timestamp = currentTimestamp
      val typeStr = "xs:integer"
      //Generate new values and create O-DF
      val infoItem = OdfInfoItem(path,Vector(OdfValue(newValueStr,typeStr,timestamp)))
      //fromPath generate O-DF structure from a ode's path and retuns OdfObjects
      val objects : OdfObjects = fromPath(infoItem)
      //Updates interval as time to live
      val write = WriteRequest( interval, objects )
      //PromiseResults contains Promise containing Iterable of Promises and has some helper methods.
      //First level Promise is used for getting answer from AgentSystem and second level Promises are
      //used to get results of actual writes and from agents that owned paths that this agent wanted to write.
      val result = PromiseResult()
      //Lets fire and forget our write, results will be received and handled hrougth promiseResult
      tell(parent,PromiseWrite( result, write ))
      //isSuccessful will return combined result or first failed write.
      val succ = result.isSuccessful
      succ.onSuccess{
        case s: SuccessfulWrite =>
        log.debug(s"$name pushed data successfully.")
      }
      succ.onFailure{
        case e => 
        log.warning(s"$name failed to write all data, error: $e")
      }
    }
  }
```
First there is some helper methods for value generation. Method `update` will try to write
new value only if `BasicAgent` has a O-DF path. First we create a O-DF structure to be writed 
and then `WriteRequest` containin it and `ttl` parameter. We could use Akka's ask pattern to 
send write to `AgentSystem` and receive result from a 'Future', but `AgentSystem` could need to
wait confirmations from other *internal agents* when we are writing to paths owned by them. 
This could block `AgentSystem` from processing other received messages. To avoid blocking, 
a `Promise` is send along the write, `AgentSystem` will complete it with a `Future`. 
Because single write can cause need for multiple corfirmations from *internal agents*, the 
`Future` completing the `Promise` passed to `AgentSystem` will return iterable containing 
`Promise`s for results from confirmations. Proccessing results from one write could be
difficult without wrapping it into `PromiseResult` and it's methods for most common cases.
So we create a `PromiseResult` and use `!` to send `PromiseWrite` to `AgentSystem` that handles
things for us. Then we aggregate successful writes to single result, when all writes have a 
result. If all writes were succellful we log it at debug level or if even one write failed, we 
log it at warnnig level.

`BasicAgent` will not yet call method `update` when `Update` is  received. We need to add 
a match case for it. This is not done same way than with normal `Actor`. We used `Receiver`
trait to force implementation of commands: `Start`, `Stop`, `Restart` and `Configure`. Now we
have to use `receiver` to add new match case for message `Update`.
```
  receiver{
    case Update => update
  }
}
```

Now we have a *internal agent*, but to get O-MI Node to run it, we need to
compile it to a .jar file and put it to `deploy` directory, or if compiled 
with O-MI Node project, `InternalAgentLoader` will find it from project's .jar file.

After this we have the final step, open the `application.conf` and add new object to
`agent-system.internal-agents`. Object's format is: 

```
"<name of agent>" = {
    class = "<class of agent>"
    config = "<config string>"
    owns = ["<Path owned by agent>", ...]
}
```
Field `owns` is only needed for `ResponsibleInternalAgent`.

Lines to add:
```
"BAgent" = {
    class  = "agents.BasicAgent"
    config = "Objects/BAgent/sensor"
}
```

Now you need to restart O-MI Node to update its configuration.

###ResponsibleAgent.scala
We want to make `BasicAgent` to be *responsible* for it's path. Lets create class 
`ResponsibleAgent` for this and implement method `handelWrite` for it.
```
class ResponsibleAgent  extends BasicAgent with ResponsibleInternalAgent{
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
    val promiseResult = PromiseResult()
    parent ! PromiseWrite( promiseResult, write)
    promise.completeWith( promiseResult.isSuccessful ) 
  }
}
```
Because of `AgentSystem` forward only O-DF strutures parts that are owned by *internal agent*
to *internal agent*, we do not need to check them. We are not doing any checks on data 
this time, so we write it and complete promise with result. `AgentSystem` writes data to 
database, because it was received from owner of paths in O-DF structure. 

A *responsible internal agent* is ready to be added to O-MI Node.
We add new object to `agent-system.internal-agents` in `application.conf`:
```
"RAgent" = {
    class  = "agents.ResponsibleAgent"
    config = "Objects/RAgent/sensor"
    owns = "Objects/RAgent/sensor"
}
```
Now restart O-MI Node to update its configuration.
ODFAgent
---------------
ODFAgent is also very simple agent that get path to .xml file as config string.
This file contains a O-DF structure.
ODFAgent parses xml file for O-DF, and start random generating values for OdfInfoItems.

