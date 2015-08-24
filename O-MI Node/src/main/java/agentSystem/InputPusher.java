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
import scala.Tuple2;

interface IInputPusher {
    public abstract void handleOdf( OdfObjects objs);
    public abstract void handleObjects( Iterable<OdfObject> objs);
    public abstract void handleInfoItems( Iterable<OdfInfoItem> items);
    public abstract void handlePathValuePairs(Iterable<Tuple2<Path,OdfValue>> pairs);
    public abstract void handlePathMetaDataPairs(Iterable< Tuple2<Path,String> > pairs); 
}

public class InputPusher {
    public static ActorRef ipdb = null; 
     public static void handleOdf( OdfObjects objs) { 
    	if(ipdb != null)
		ipdb.tell(new HandleOdf(objs),null); 
    }
    public static void handleObjects( Iterable<OdfObject> objs) { 
    	if(ipdb != null)
		ipdb.tell(new HandleObjects(objs),null); 
    }
    public static void handleInfoItems( Iterable<OdfInfoItem> items) { 
    	if(ipdb != null)
		ipdb.tell(new HandleInfoItems(items),null); 
    }
    public static void handlePathValuePairs(Iterable<Tuple2<Path,OdfValue>> pairs) { 
    	if(ipdb != null)
		ipdb.tell(new HandlePathValuePairs(pairs),null);
    }
    public static void handlePathMetaDataPairs(Iterable< Tuple2<Path,String> > pairs) { 
    	if(ipdb != null)
		ipdb.tell(new HandlePathMetaDataPairs(pairs),null); 
    }
}
