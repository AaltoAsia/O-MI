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

import agentSystem.InputPusherCmds.HandleOdf;
import agentSystem.InputPusherCmds.HandleObjects;
import agentSystem.InputPusherCmds.HandleInfoItems;
import agentSystem.InputPusherCmds.HandlePathValuePairs;
import agentSystem.InputPusherCmds.HandlePathMetaDataPairs;
import types.OdfTypes.OdfObject;
import types.OdfTypes.OdfObjects;
import types.OdfTypes.OdfInfoItem;
import types.OdfTypes.OdfValue;
import types.Path;
import akka.actor.ActorRef;
import static akka.pattern.Patterns.ask;
import static akka.pattern.Patterns.pipe;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.util.Timeout;

import java.util.concurrent.TimeUnit;

import scala.Tuple2;

/** Interface for pushing data to InputPusher actor. 
 */
public class InputPusher {
    private static Timeout default_timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
    public static ActorRef ipdb = null; 
     public static Future<Object> handleOdf( OdfObjects objs, Timeout t ) { 
    	if(ipdb != null)
        	return ask(ipdb,new HandleOdf(objs), t);
        else
            return Futures.failed(new Exception("ipdb is null for InputPusher."));
    }
     public static Future<Object> handleOdf( OdfObjects objs) { 
         return handleOdf( objs, default_timeout );
     }
    public static Future<Object> handleObjects( Iterable<OdfObject> objs, Timeout t ) { 
    	if(ipdb != null)
		return ask(ipdb,new HandleObjects(objs), t); 
        else
            return Futures.failed(new Exception("ipdb is null for InputPusher."));
    }
     public static Future<Object> handleObjects( Iterable<OdfObject> objs) { 
         return handleObjects( objs, default_timeout );
     }
    public static Future<Object> handleInfoItems( Iterable<OdfInfoItem> items,Timeout t ) { 
    	if(ipdb != null)
        	return ask(ipdb,new HandleInfoItems(items), t ); 
        else
            return Futures.failed(new Exception("ipdb is null for InputPusher."));
    }
     public static Future<Object> handleInfoItems( Iterable<OdfInfoItem> items) { 
         return handleInfoItems( items, default_timeout );
     }
    public static Future<Object> handlePathValuePairs(Iterable<Tuple2<Path,OdfValue>> pairs,Timeout t) { 
    	if(ipdb != null)
		return ask(ipdb,new HandlePathValuePairs(pairs),t);
        else
            return Futures.failed(new Exception("ipdb is null for InputPusher."));
    }
    public static Future<Object> handlePathValuePairs(Iterable<Tuple2<Path,OdfValue>> pairs) { 
         return handlePathValuePairs(pairs,default_timeout);
     }
    public static Future<Object> handlePathMetaDataPairs(Iterable< Tuple2<Path,String> > pairs, Timeout t) { 
    	if(ipdb != null)
	    return ask(ipdb,new HandlePathMetaDataPairs(pairs), t); 
        else
            return Futures.failed(new Exception("ipdb is null for InputPusher."));
    }
    public static Future<Object> handlePathMetaDataPairs(Iterable< Tuple2<Path,String> > pairs) { 
        return handlePathMetaDataPairs( pairs, default_timeout);
    } 
}
