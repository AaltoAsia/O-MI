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

import types.OdfTypes.OdfObject;
import akka.actor.ActorRef;
import akka.event.LoggingAdapter;

/** Abstract base class for internal agents that extends Thread.
 * Work same way than a Thread. When start() method is called run() method will be processed in a different thread.
 * Simplessness of this interface gives freedom in implementation, but also make responsible for handling interruptions and exceptions.
 * Loader will only try to restart agent when exception is given outside of initialization. If exception occurs too often Loader will consider
 * internal agent unrestartable.
 *  
public abstract class InternalAgent extends Thread {
     */
    
    /** ActorRef to InternalAgentLoader for communincation.
     *
    protected static ActorRef loader = null;
    
     */
    /**Log for logging agents, actions.
    protected static LoggingAdapter log = null;
    
     */
    /**One-time setter for loader
    public static final void setLoader(ActorRef aloader) {
	if(loader == null && aloader != null)
	    loader = aloader;
    }
     */
    
    /**One-time setter for log
    public static final void setLog(LoggingAdapter logger) {
	if(log == null && logger != null)
	    log = logger;
    }
    
     */
    /**Base conscrutcor
    public InternalAgent() {
        //noop?
    }

     */
    /**Absctract method for initialisation and passing config to agent. Implement for your internal agents.
     * @param config Config string given in application.conf.
    public abstract void init( String config );

     */
    /**Absctract method that is called when agent is started.
     * 
    public abstract void run();
}
 */
