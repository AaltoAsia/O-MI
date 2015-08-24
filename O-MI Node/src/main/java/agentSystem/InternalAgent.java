/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  https://github.com/AaltoAsia/O-MI/blob/master/LICENSE.txt

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package agentSystem;

import agentSystem.ThreadInitialisationException;
import agentSystem.ThreadException;
import types.OdfTypes.OdfObject;
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
	    return;
	}
	try{
	    while(isRunning()){
		loopOnce();
	    }

	} catch(Exception e) {
	    if(loader != null)
		loader.tell( new ThreadException(this, e), null);
	    
	    shutdown();
	} finally {
	    finish();
	}
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
