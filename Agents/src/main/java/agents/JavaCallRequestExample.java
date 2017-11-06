package agents;

import java.util.Vector;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import com.google.gson.*;

import scala.concurrent.duration.*;
import scala.concurrent.Future;
import akka.japi.Creator;
import akka.actor.Props;
import akka.actor.ActorRef;
import akka.dispatch.*;

import com.typesafe.config.Config;

import agentSystem.*;
import types.*;
import types.OdfTypes.*;
import types.OmiTypes.*;


public class JavaCallRequestExample extends ResponsibleJavaInternalAgent {

    // Boilerplate for Akka Actors
    static public Props props(final Config config, final ActorRef requestHandler,final ActorRef dbHandler){
        return Props.create(
                new Creator<ResponsibleJavaAgentDemo>() {
                    private static final long serialVersionUID = 35735155L;

                    @Override
                    public ResponsibleJavaAgentDemo create() throws Exception {
                        return new ResponsibleJavaAgentDemo(config, requestHandler, dbHandler);
                    }
                } 
        );
    }

    // Constructor
    public JavaCallRequestExample(
            Config config,
            final ActorRef requestHandler,
            final ActorRef dbHandler) {

        super(requestHandler,dbHandler);

    }

    public Timestamp getCurrentTime() {
        return new Timestamp(new java.util.Date().getTime());
    }

    // Json does not have one-to-one mapping to O-DF, but this tries to transform some use cases
    public OdfNode jsonToOdf(JsonObject jsonElement){


        ArrayList<OdfObject> children = new ArrayList<OdfObject>();

        for (Map.Entry<String,JsonElement> entry: jsonElement.entrySet()) {
            String id = entry.getKey();

            // First level children MUST be <Object>s in the O-DF standard
            JsonObject jsonChild = (JsonObject) entry.getValue();

            OdfObject child = (OdfObject) jsonToOdf(jsonChild, new Path("Objects/"+id));
            children.add(child);
        }

        OdfObjects objects = OdfFactory.createOdfObjects(children, "1.0");
        return objects;
    }

    // Recursive conversion
    public OdfNode jsonToOdf(JsonElement jsonElement, Path parentPath){

        if (jsonElement instanceof JsonObject) {
            JsonObject jsonObj = (JsonObject) jsonElement;
            // Object will be converted to O-DF Object
            Path objectPath = new Path(parentPath.toString()+"/"+id); // TODO id

            ArrayList<OdfObject> objectChildren = new ArrayList<OdfObject>();
            ArrayList<OdfInfoItem> infoItemChildren = new ArrayList<OdfInfoItem>();

            for (Map.Entry<String,JsonElement> entry: jsonObj.entrySet()) {

                String id = entry.getKey();
                JsonElement jsonChild = entry.getValue();

                OdfNode child = jsonToOdf(jsonChild, objectPath);
                if (child instanceof OdfObject)
                    objectChildren.add((OdfObject)child);
                else if (child instanceof OdfInfoItem)
                    infoItemChildren.add((OdfInfoItem)child);
            }

            OdfObject obj = OdfFactory.createOdfObject(objectPath, infoItemChildren, objectChildren);
            return obj;

        } else if (jsonElement instanceof JsonArray){
            JsonArray jsonArr = (JsonArray) jsonElement;
            // Array will be converted to O-DF InfoItem with values or Object if it contains objects

            ArrayList<OdfNode> values = new ArrayList<OdfNode>();

            for (JsonElement arrElem: jsonArr) {
                if (!(arrElem instanceof JsonPrimitive)) {
                    throw new java.lang.UnsupportedOperationException("Don't know what to use as Object ID. Domain specific json converting needed.");
                }
                OdfNode value = jsonToOdf(arrElem, parentPath); 
            }


        } else if (jsonElement instanceof JsonPrimitive) {
            JsonPrimitive jsonPrim = (JsonPrimitive) jsonElement;
            // primitive will be converted to string value, TODO: add other types
            System.out.println("JSON test:");
            System.out.println(jsonPrim.toString());
            System.out.println(jsonPrim.getAsString());
            return OdfFactory.createOdfValue(jsonPrim.getAsString(), getCurrentTime());

        } else if (jsonElement instanceof JsonNull) {
            // null will be converted to empty value
            return OdfFactory.createOdfValue("", getCurrentTime());
        }
    }

    // O-DF Path for the rpc
    private static final Path callPath = new Path( "Objects/Service/JavaCall" );

    // Some hardcoded result data that are selected with a call parameter
    private ArrayList myJsonSource = new ArrayList("{\"result\" : [\"hello\"}", "{}");

    // This method is run when call request is received and application.conf settings
    // are configured to route the request to this agent.
    public Future<ResponseRequest> handleCall(CallRequest callRequest){

        // google json library: https://github.com/google/gson/blob/master/UserGuide.md
        Gson gson = new Gson();

        try{
            // fetch the InfoItem containing the rpc parameters from the O-DF of the incoming call request
            OdfNode node = callRequest.odf().get(callPath).get();
            OdfInfoItem ii = (OdfInfoItem)node;

            // The parameters are contained in another O-DF hierarchy inside the <value> element
            OdfValue<Object> value = ii.values().head();

            OdfValue<Object> newValue = OdfFactory.createOdfValue("derp", getCurrentTime());  

            Vector<OdfValue<Object>> values = new Vector<OdfValue<Object>>();
            values.add( newValue );
            OdfInfoItem new_ii = OdfFactory.createOdfInfoItem(
                    new Path("Objects/Service/Greeting"),
                    values 
                    );

            return Futures.successful( 
                    Responses.Success(
                        scala.Option.apply(new_ii.createAncestors()),
                        Duration.apply(10,TimeUnit.SECONDS)
                        )
                    );
        } catch(Exception exp ) {
            return Futures.successful( 
                    Responses.InternalError(exp)
                    );
        }
    }
}



