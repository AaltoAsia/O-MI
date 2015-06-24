package agents;

import agentSystem.InternalAgent;
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

public class CoffeeMaker extends InternalAgent{
    private Path path;
    private Random rnd;
    public void loopOnce(){
	if(!path.toString().isEmpty()){
	    Date date = new java.util.Date();
	    LinkedList<Tuple2<Path,OdfValue>> values = new  LinkedList<Tuple2<Path,OdfValue>>();
	    Tuple2<Path,OdfValue> tuple = new Tuple2( path, new OdfValue( Integer.toString(rnd.nextInt()), "xs:integer", Option.apply( new Timestamp( date.getTime() ) ) ) ); 
	    values.add(tuple);
	    InputPusher.handlePathValuePairs(values);
	    try{
		Thread.sleep(10000);
	    }catch(InterruptedException e){
		shutdown();
	    }
	}
    }
    public void finish(){
	System.out.println("CoffeeMaker has died.");
    }
    public void init(){
	if(configPath.isEmpty() ){
	    shutdown();
	    return;
	}
	path = new Path(configPath);
    }
    public CoffeeMaker(String configPath) { 
	super(configPath);
	rnd = new Random();
    }


}
