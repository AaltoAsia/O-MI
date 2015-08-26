package agents;

import agentSystem.InternalAgent;
import agentSystem.InternalAgentExceptions.AgentException;
import agentSystem.InternalAgentExceptions.AgentInitializationException;
import agentSystem.InternalAgentExceptions.AgentInterruption;
import agentSystem.InputPusher;
import java.io.File;
import types.OdfTypes.OdfValue;
import types.Path;
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;
import java.util.LinkedList;
import scala.Option;
import scala.Tuple2;
import java.lang.Integer;

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
            log.warning( "JavaAgent has caught an exception during initialisation." );
            loader.tell( new AgentInitializationException( this, e ), null );
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
            loader.tell( new AgentInterruption( this, e), null );
        }catch( Exception e ){
            log.warning( "JavaAgent has caught an exception." );
            loader.tell( new AgentException( this, e), null );
        }finally{
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
}
