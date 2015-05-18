package agentSystem;

import agentSystem.ThreadInitialisationException;
import agentSystem.ThreadException;
import parsing.Types.OdfTypes.OdfObject;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;

public abstract class InternalAgent extends Thread {
    protected String configPath;
    private Boolean running = true;
    private static ActorRef loader = null;
    protected static LoggingAdapter log = null;
    public static final void setLoader(ActorRef aloader) {
	if(loader == null)
	    loader = aloader;
    }
    public static final void setLog(LoggingAdapter logger) {
	if(log == null)
	    log = logger;
    }
    abstract public void loopOnce();
    abstract public void finish();
    abstract public void init();
    public InternalAgent(String configPath) {
	this.configPath =configPath; 
    }
    public final void run(){
	try{
	    init();
	} catch(Exception e) {
	    if(loader != null)
		loader.tell( new ThreadInitialisationException(this, e), null);
	    
	    shutdown();
	    finish();
	}
	try{
	    while(isRunning()){
		loopOnce();
	    }

	} catch(Exception e) {
	    if(loader != null)
		loader.tell( new ThreadException(this, e), null);
	    
	    shutdown();
	    finish();
	}
	finish();
    }

    public final boolean isRunning(){
	synchronized(running){
	    return running;
	} 
    }
    public final void shutdown(){
	synchronized(running){
	    running = false;	
	}
    }
}
