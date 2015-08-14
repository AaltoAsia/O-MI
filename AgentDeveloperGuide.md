Agent Developer Guide
=====================

What are Agents?
----------------
Agents are small programs that connect to sensors and push received data to
O-MI Node. 
There are two kind of Agents ussing different interfaces: 
*External agents that push O-DF foramated sensor data to a TCP port of O-MI
Node.
*Internal agents that can be loaded from .jar file and instatiated to be run
inside same JVM as O-MI Node. These will us internal interface to push data to
database.

External Agent
--------------
All you need to do is to write a program that push O-DF formated data to TCP
port defined by application.conf's omi-service.external-agent-port parameter.

Internal Agent
----------------
InternalAgent is a abstract class extending Thread class. They have two
abstract methods: init and run. After InternalAgent is instatieted its init
method is called with string given for agent in application.conf. This string can
contain anything, like path to a config file. After this InternalAgents start method is
called and Thread runs run method in another thread. InternalAgent have also
two other members: log for logging and loader for cummincation with
InternalAgentLoader. 

For pushing data to database InputPusher's interface is used. It have five
static public methods:
handleOdf that takes a OdfObjects as parameter,
handleObjects that takes Iterable of OdfObject as parameter,
handleInfoItems that takes Iterable of OdfInfoItem as parameter,
handlePathValuePairs that takes Iterable of (Path, OdfValue) pairs as parameter,
handlePathMetaDataPairs that takes Iterable of (Path, OdfMetaData) pairs as parameter,

To use o-mi-node.jar as libarary add it to classpath.

JavaAgent and ScalaAgent both random generate both take O-DF path as config
parameter and start pushing random values to that path.
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

In init mehtod we initialise rnd for random value generation and save config
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

In run method we generate new value and push it to path every ten seconds.
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
code from Java. Also notice that agent need to handle interruption of thread
by themself and terminate itself when interrupt happens.

Now we have a internal agent, but to get O-MI Node to run it, we need to
compile it to .jar file and put it to deploy directory. After this we have
final step, look at application.conf and add new line to
agent-system.internal-agents: 
"<classname of agent>" = "<config string>"
"agents.JavaAgent" = "Objects/JavaAgent/sensor"


