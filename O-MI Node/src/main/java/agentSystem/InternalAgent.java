package agentSystem;

import agentSystem.ThreadInitialisationException;
import agentSystem.ThreadException;
import types.OdfTypes.OdfObject;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;

public abstract class InternalAgent extends Thread {
    protected static ActorRef loader = null;
    protected static LoggingAdapter log = null;
    public static final void setLoader(ActorRef aloader) {
	if(loader == null && aloader != null)
	    loader = aloader;
    }
    public static final void setLog(LoggingAdapter logger) {
	if(log == null && logger != null)
	    log = logger;
    }
    public InternalAgent() {
        //noop?
    }
    public abstract void init( String config );
    public abstract void run();
}
