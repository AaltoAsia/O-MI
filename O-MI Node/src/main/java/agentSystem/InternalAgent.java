package agentSystem;
import agentSystem.HandleObjects;
import parsing.Types.OdfTypes.OdfObject;
import java.util.LinkedList;

public abstract class InternalAgent extends Thread {
    protected String configPath;
    private Boolean running = true;
    abstract public void loopOnce();
    abstract public void finish();
    abstract public void init();
    public InternalAgent(String configPath) { this.configPath =configPath; }
    public final void run(){
	init();
	while(isRunning()){
	    loopOnce();
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
