package agents;

import agentSystem.InternalAgent;
import agentSystem.InternalAgentExceptions.AgentException;
import agentSystem.InternalAgentExceptions.AgentInitializationException;
import agentSystem.InternalAgentExceptions.AgentInterruption;
import agentSystem.InputPusher;
import java.util.concurrent.TimeUnit;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.LinkedList;
import java.nio.charset.Charset;
import java.lang.String;
import java.util.Iterator;
import java.util.Random;
import java.util.Date;
import java.sql.Timestamp;

import scala.util.Either; // a value that is either of two types, wrapped in class Right or Left
import scala.Option; // a value that is either Some(value) or None, like a class for null
import scala.Tuple2; // a pair of different types
import scala.collection.immutable.Vector; // a generic immutable collection

import parsing.OdfParser;
import types.OdfTypes.OdfTreeCollection; // defines toJava and fromJava for collections in odf types
import types.OdfTypes.OdfObjects;
import types.OdfTypes.OdfValue;
import types.ParseError;
import types.Path;
import types.OdfTypes.package$;

public class JavaHouseAgent extends InternalAgent{
    
    Iterable<Tuple2<Path,OdfValue>> path_value_pairs;
    private Random rnd;

    public void init( String config ){
        try{
            rnd = new Random();
            List<String> lines = Files.readAllLines(Paths.get(config), Charset.defaultCharset()); 
            String xml = "";
            for( int i = 0; i < lines.size(); ++i){
                xml = xml.concat(lines.get(i));
            }
            Either<Iterable<ParseError>,OdfObjects>result = OdfParser.parse(xml);
            if( result.isLeft() ) {//Errors
                Iterator<ParseError> errors = result.left().get().iterator();
                String agregat = "ParseErrors: ";
                while(errors.hasNext()){
                    agregat = agregat.concat(errors.next().msg());
                }
                throw new Exception(agregat); 
            }else if(result.isRight()){//ODF
                path_value_pairs  = OdfTreeCollection.toJava(package$.MODULE$.getPathValuePairs(result.right().get()));
            }
            log.info("JavaHouseAgent Initialized");
        }catch( Exception e ){
            log.warning( "JavaHouseAgent has caught an exception during initialization." );
            log.error(e, "JavaHouseAgent");
            loader.tell( new AgentInitializationException( this, e ), null );
            InternalAgent.log.warning( "JavaAgent has died." );
        }
    }
    public void run(){

        try{
            while( !interrupted() ){

                Date date = new java.util.Date();
                Iterator<Tuple2<Path,OdfValue>> pair_iter = path_value_pairs.iterator();
                LinkedList<Tuple2<Path,OdfValue>> pairs = new LinkedList< Tuple2< Path, OdfValue > >();
                while(pair_iter.hasNext()){
                    Tuple2<Path, OdfValue> pair = pair_iter.next();
                    
                    // The value that will be written
                    OdfValue value = new OdfValue(
                            Double.toString( Double.parseDouble( pair._2().value() ) + rnd.nextGaussian()*Double.parseDouble(pair._2().value())),  // create a random value
                            "xs:double",
                            //Option.apply(  // Option is a simple container from scala for handling null values
                                new Timestamp( 
                                    date.getTime() 
                                    ) 
                                //)
                            );
                    Tuple2<Path, OdfValue> n_pair = new Tuple2<Path, OdfValue>(pair._1(), value);
                    pairs.add(n_pair);
                }
                path_value_pairs = pairs;
                InputPusher.handlePathValuePairs(path_value_pairs);
                Thread.sleep( 10000 );
            }
        }catch( InterruptedException e ){
            log.warning( "JavaHouseAgent has been interrupted." );
            loader.tell( new AgentInterruption( this, e), null );

        }catch( Exception e ){
            log.warning( "JavaHouseAgent has caught an exception." );
            log.error(e, "JavaHouseAgent");
            loader.tell( new AgentException( this, e), null );

        }finally{
            InternalAgent.log.warning( "JavaHouseAgent has died." );
        }
    }
}
