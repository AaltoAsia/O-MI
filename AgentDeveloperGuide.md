Agent Developer Guide
=====================

What are Agents?
----------------
Agents are small programs that connect to sensors and push received data to
O-MI Node. 

There are two kind of Agents ussing different interfaces: 
* *External agents* that push O-DF foramated sensor data to a TCP port of O-MI
Node.
* *Internal agents* that can be loaded from .jar file and instatiated to be run
inside same JVM as O-MI Node.

External Agent
--------------
All you need to do is to write a program that pushes O-DF formated data to the TCP
port defined by `application.conf`'s omi-service.external-agent-port parameter.
Program can be writen with any programming language. See
[the simple python example](https://github.com/AaltoAsia/O-MI/blob/master/agentExample.py).
Starting and stopping of external agents are the user's responsibility.

Internal Agent
----------------
*InternalAgent* is an abstract class extending `Thread` class. They have two
abstract methods: `init` and `run`. After an `InternalAgent` is created its `init`
method is called with the string given for agent in `application.conf`. This string can
contain anything, like a path to a config file. After this `InternalAgent`'s `start` method is
called. This causes the `run` method to be run in an another thread. 

InternalAgent have also two other members: 
* `LoggingAdapter log` for logging and 
* `ActorRef loader` for communication with the `InternalAgentLoader`. 

For pushing data to database `InputPusher`'s interface is used. It has five
static public methods:
- `handleOdf` that takes an `OdfObjects` as parameter,
- `handleObjects` that takes an `Iterable` of `OdfObject` as parameter,
- `handleInfoItems` that takes an `Iterable` of `OdfInfoItem` as parameter,
- `handlePathValuePairs` that takes an `Iterable` of `(Path, OdfValue)` pairs as parameter,
- `handlePathMetaDataPairs` that takes an `Iterable` of `(Path, OdfMetaData)` pairs as parameter,

To make internal agents you need to have 
**o-mi-node.jar as a libarary and added to your classpath**.

`JavaAgent` and `ScalaAgent` both take an O-DF path as `config`
parameter and start pushing random generated values to that path.

Lets look at JavaAgent.java:
```java
public class JavaAgent extends InternalAgent{
    public JavaAgent() { 
    }
    private Path path;
    private Random rnd;
    private boolean initialised = false;
    public void init( String config ){
        try{
            rnd = new Random();
            path = new Path( config );
            initialised = true;
            log.warning( "JavaAgent has been initialised." );
        }catch( Exception e ){
            log.warning( "JavaAgent has caucth exception turing initialisation." );
            loader.tell( new ThreadInitialisationException( this, e ), null );
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
    public void run(){
        try{
            while( !interrupted() && !path.toString().isEmpty() ){
                Date date = new java.util.Date();
                LinkedList< Tuple2< Path, OdfValue > > values = new  LinkedList< Tuple2< Path, OdfValue > >();
                Tuple2< Path, OdfValue > tuple = new Tuple2(
                        path,
                        new OdfValue(
                            Integer.toString(rnd.nextInt()), 
                            "xs:integer",
                            Option.apply( 
                                new Timestamp( 
                                    date.getTime() 
                                    ) 
                                ) 
                            ) 
                        ); 
                values.add( tuple );
                log.info( "JavaAgent pushing data." );
                InputPusher.handlePathValuePairs( values );
                Thread.sleep( 10000 );
            }
        }catch( InterruptedException e ){
            log.warning( "JavaAgent has been interrupted." );
            loader.tell( new ThreadException( this, e), null );
        }finally{
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
}
```

In the `init` mehtod we initialise `rnd` for random value generation and save the `config`
as O-DF.

```java
    public void init( String config ){
        try{
            rnd = new Random();
            path = new Path( config );
            initialised = true;
            log.warning( "JavaAgent has been initialised." );
        }catch( Exception e ){
            log.warning( "JavaAgent has caucth exception turing initialisation." );
            loader.tell( new ThreadInitialisationException( this, e ), null );
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
```

In the `run` method we generate a new value and push it to the `path` every ten seconds.
```java
    public void run(){
        try{
            while( !interrupted() && !path.toString().isEmpty() ){
                Date date = new java.util.Date();
                LinkedList< Tuple2< Path, OdfValue > > values = new  LinkedList< Tuple2< Path, OdfValue > >();
                Tuple2< Path, OdfValue > tuple = new Tuple2(
                        path,
                        new OdfValue(
                            Integer.toString(rnd.nextInt()), 
                            "xs:integer",
                            Option.apply( 
                                new Timestamp( 
                                    date.getTime() 
                                    ) 
                                ) 
                            ) 
                        ); 
                values.add( tuple );
                log.info( "JavaAgent pushing data." );
                InputPusher.handlePathValuePairs( values );
                Thread.sleep( 10000 );
            }
        }catch( InterruptedException e ){
            log.warning( "JavaAgent has been interrupted." );
            loader.tell( new ThreadException( this, e), null );
        }finally{
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
```

Because O-MI Node has been writen with Scala, you may need to call Scala
code from Java. Also notice that agents need to [handle the interruption of thread
by themself and terminate itself when interrupt happens](https://docs.oracle.com/javase/tutorial/essential/concurrency/interrupt.html).

Now we have an internal agent, but to get O-MI Node to run it, we need to
compile it to a .jar file and put it to `deploy` directory. After this we have
the final step, open the `application.conf` and add new line to
`agent-system.internal-agents`: 
```
"<classname of agent>" = "<config string>"
"agents.JavaAgent" = "Objects/JavaAgent/sensor"
```

