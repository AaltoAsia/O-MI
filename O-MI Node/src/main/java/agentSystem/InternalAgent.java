/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

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
/** Abstract base class for internal agents.
 *
 */
public abstract class InternalAgent extends Thread {
    //ActorRef to InternalAgentLoader for communincation.
    protected static ActorRef loader = null;
    //Lo for logging agents, actions.
    protected static LoggingAdapter log = null;
    //One-tiem setters
    public static final void setLoader(ActorRef aloader) {
	if(loader == null && aloader != null)
	    loader = aloader;
    }
    public static final void setLog(LoggingAdapter logger) {
	if(log == null && logger != null)
	    log = logger;
    }
    //Constructor
    public InternalAgent() {
        //noop?
    }
    //Abstract methods that need to be implemented for internal agent.
    //Intercafe for initialisation and passing config to agent.
    public abstract void init( String config );
    //Main method of internal agent, run in different thread after, Thread's start method is called
    public abstract void run();
    //Note: Interruption need to be handled by agent.
}
