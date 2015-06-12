package agentSystem;

import agentSystem.HandleOdf;
import agentSystem.HandleObjects;
import agentSystem.HandleInfoItems;
import agentSystem.HandlePathValuePairs;
import agentSystem.HandlePathMetaDataPairs;
import parsing.Types.OdfTypes.OdfObject;
import parsing.Types.OdfTypes.OdfObjects;
import parsing.Types.OdfTypes.OdfInfoItem;
import parsing.Types.OdfTypes.OdfValue;
import parsing.Types.Path;
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
