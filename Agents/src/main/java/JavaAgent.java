package agents;

import java.io.File;
import java.sql.Timestamp;
import java.util.Random;
import akka.util.Timeout;
import java.util.Date;
import java.util.LinkedList;
import java.lang.Integer;

import scala.concurrent.duration.FiniteDuration;
// scala stuff can be generally constructed by calling .apply() method or "new ..."
import scala.Option;
import scala.Tuple2;

import types.OdfTypes.OdfValue;
import types.Path;

import agentSystem.InternalAgent;
import agentSystem.InternalAgentExceptions.AgentException;
import agentSystem.InternalAgentExceptions.AgentInitializationException;
import agentSystem.InternalAgentExceptions.AgentInterruption;
import agentSystem.InputPusher;
import java.util.concurrent.TimeUnit;

public class JavaAgent extends InternalAgent{
    public JavaAgent() { }

    private Path path;
    private Random rnd;
    private final FiniteDuration t = FiniteDuration.create(5, TimeUnit.SECONDS );

    // Initialize, called once to reset the agent
    public void init( String config ){
	try{
	    rnd = new Random();
            path = new Path( config );
            
            log.warning( "JavaAgent has been initialized." );

        }catch( Exception e ){
            log.warning( "JavaAgent has caught an exception during initialization." );
            loader.tell( new AgentInitializationException( this, e ), null );
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
    
    // run of the Thread
    public void run(){

        try{
            while( !interrupted() && !path.toString().isEmpty() ){

                Date date = new java.util.Date();

                // The value that will be written
                OdfValue value = new OdfValue(
                        Integer.toString(rnd.nextInt()),  // create a random value
                        "xs:integer",
                        Option.apply(  // Option is a simple container from scala for handling null values
                            new Timestamp( 
                                date.getTime() 
                                ) 
                            ) 
                        );

                // Create the right container objects:
                LinkedList< Tuple2< Path, OdfValue > > values = new  LinkedList< Tuple2< Path, OdfValue > >();
                LinkedList< Tuple2< Path, String> > metadatas = new  LinkedList< Tuple2< Path, String> >();

                Tuple2< Path, OdfValue > tuple = new Tuple2( path, value ); 
                Tuple2< Path, String> tupleM = new Tuple2( path,"<MetaData xmlns=\"odf.xsd\" ><InfoItem name=\"test\"><value>ok</value></InfoItem></MetaData>"); 

                values.add( tuple );
                metadatas.add(tupleM);

                log.info( "JavaAgent pushing data." );

                // Push data to the system
                InputPusher.handlePathValuePairs( values, new Timeout(t) );
                InputPusher.handlePathMetaDataPairs( metadatas);

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
