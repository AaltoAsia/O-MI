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
import akka.actor.Cancellable;

import com.typesafe.config.Config;

import agentSystem.JavaInternalAgent; 
import agentSystem.ResponsibilityRequest;
import agentSystem.*;
import types.Path;
import types.OmiTypes.*;
import types.OdfTypes.OdfValue;
import types.OdfTypes.*;
import types.OdfTypes.OdfInfoItem;

public class JavaAgent extends JavaInternalAgent {
  /*
   *  THIS STATIC METHOD MUST EXISTS FOR JavaInternalAgent. 
   *  WITHOUT IT JavaInternalAgent CAN NOT BE INITIALIZED.
   *  Implement it in way that Akka recommends to.
   *
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
  public JavaAgent( Config conf){
    config = conf;
    interval = new FiniteDuration( config.getDuration("interval", TimeUnit.SECONDS), TimeUnit.SECONDS);	
    path = new Path(config.getString("path"));
  }
  protected Config config;
  protected FiniteDuration interval;
  protected Path path;

  protected Cancellable job = null;
  /*
   * Method to be called when a Start() message is received.
   */
  @Override
  public InternalAgentSuccess start() throws StartFailed {
    try{
      //Lets schelude a messge to us on every interval
      //and save it for possibility that we want to stop agent.
      job = context().system().scheduler().schedule(
        Duration.Zero(),//Delay start
        interval,//Interval between messages
        self(),//To 
        "Update",//Message, preferably immutable.
        context().system().dispatcher(),//ExecutionContext, Akka
        null//Sender?
      );
      return new CommandSuccessful();
    } catch( Exception exp ) {
      //Normally in Akka if exception is thrown in child actor, it is 
      //passed to its parent. That uses SupervisorStrategy to decide 
      //what to do. With StartFailed we can tell AgentSystem that an 
      //Exceptian was thrown during handling of Start() message.
      throw new StartFailed( exp.getMessage(), exp);
    }
  }

  /*
   * Method to be called when a Stop() message is received.
   */
  @Override
  public InternalAgentSuccess stop()  throws CommandFailed {

    if( job != null){//Job is defined? 
      job.cancel(); //Cancel job
      
      //Check if job was cancelled
      if( job.isCancelled() ){
        job = null;
      } else {
        throw new CommandFailed("Failed to stop agent.");
      }
    } 
    return new CommandSuccessful();
  }

  //Random for generating new values for path.
  Random rnd = new Random();
  /*
   * Method to be called when a "Update" message is received.
   */
  public void update() {

    //Generate new OdfValue 
    Timestamp timestamp =  new Timestamp(  new java.util.Date().getTime() );
    String typeStr = "xs:integer";
    String newValueStr = rnd.nextInt() +""; 
    Vector<OdfValue> values = new Vector();
    //Ugly. "Calls static method of OdfValue to construct an OdfValue."
    OdfValue value = OdfValue$.MODULE$.apply( newValueStr, typeStr, timestamp, new HashMap());
    values.add(value);

    //Create OdfInfoItem. 
    OdfInfoItem infoItem = new OdfInfoItem(
        path, 
        //Convert to Scala Vector. For more conversion between Java and Scala
        //look for scala.collectio.JavaConversions
        scala.collection.JavaConversions.iterableAsScalaIterable(values).toVector(),
        scala.Option.empty(), //None
        scala.Option.empty() //None
    );

    //createAncestors generate O-DF structure from an OdfNode's path and retuns OdfObjects
    OdfObjects objects = infoItem.createAncestors();

    log.debug( name + " pushing data...");
    //Create O-MI write request
    //interval as time to live
    WriteRequest write = new WriteRequest(
        interval, //ttl
        objects, //O-DF
        scala.Option.empty()//Callback, optional
    );
    
    Timeout timeout = new Timeout(interval);
    ExecutionContext ec = context().system().dispatcher();
    //We need to tell AgentSystem who is askinng request to be handled, so we wrap write
    //in ResponsibilityRequest.
    ResponsibilityRequest rw = new ResponsibilityRequest(name, write);

    //Lets ask AgentSystem to handle our write
    Future<Object> future = ask( agentSystem,rw, timeout);
    //Map Object to ResponsibleAgentResponse
    Future<ResponsibleAgentResponse> result = future.map(new Mapper<Object,ResponsibleAgentResponse>() {
        public ResponsibleAgentResponse apply(Object obj) {
            return (ResponsibleAgentResponse) obj;
        }
    }, ec);
    //Call LogResult if write was successful.
    result.onSuccess(new LogResult(), ec);

  }
  public final class LogResult extends OnSuccess<ResponsibleAgentResponse> {
      @Override public final void onSuccess(ResponsibleAgentResponse t) {
        log.debug(name + " pushed data successfully.");
      }
  }
  
  /*
   * Method that is inherited from akka.actor.UntyppedActor and handles incoming messages
   * from other Actors.
   */
  @Override
  public void onReceive(Object message) throws StartFailed, CommandFailed {
    if( message instanceof Start) {
      getSender().tell(start(),getSelf());
    } else if( message instanceof Stop) {
      getSender().tell(stop(),getSelf());
    } else if( message instanceof Restart) {
      getSender().tell(restart(),getSelf());
    } else if( message instanceof String) {
      String str = (String) message;
      if( str.equals("Update"))
        update();
    } else unhandled(message);
  }
}
