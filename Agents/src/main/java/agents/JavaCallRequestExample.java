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


/**
 * Requires following configuration in application.conf:
 *  {
 *    name = "JavaCallRequestExample" 
 *    class = "agents.JavaCallRequestExample"
 *    language = "java"
 *     responsible = {
 *       "Objects/Service/JavaCall" = "c"
 *     }
 *  }
 *
 *
 *  Example request:
 *  <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="0">
 *    <call msgformat="odf">
 *      <msg>
 *        <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
 *          <Object>
 *            <id>Service</id>
 *            <InfoItem name="JavaCall">
 *              <value type="odf">
 *                <Objects>
 *                  <Object>
 *                    <id>MyParameters</id>
 *                    <InfoItem name="MyParameter">
 *                      <value type="xs:int">0</value>
 *                    </InfoItem>
 *                  </Object>
 *                </Objects>
 *             </value>
 *           </InfoItem>
 *         </Object>
 *       </Objects>
 *     </msg>
 *   </call>
 * </omiEnvelope>
 */
public class JavaCallRequestExample extends ResponsibleJavaInternalAgent {

    // O-DF Path for the rpc
    private static final Path callPath = new Path( "Objects/Service/JavaCall" );


    // Some hardcoded json result data that are selected with a call parameter
    private String[] myJsonSource = {
                "{\"results\" : [{\"name\": \"first\", \"value\": 2}, {\"name\": \"second\", \"value\": 4}]}",
                "{\"results\" : [{\"name\": \"first\", \"value\": 4}, {\"name\": \"second\", \"value\": 8}]}"};

    // Classes representing the json data, used for gson library
    class MyResultData {
        public ArrayList<MyValue> results;
    }

    class MyValue {
        public String name;
        public int value;
    }



    // Json does not have one-to-one mapping to O-DF, so this coverts an example of domain specific json
    public OdfObjects jsonToOdf(String json){

        // google json library: https://github.com/google/gson/blob/master/UserGuide.md
        Gson gson = new Gson();

        // Parse json to MyResultData class
        MyResultData myData = gson.fromJson(json, MyResultData.class);

        // Use MyResultData to create the O-DF Objects
        // The following O-DF tree is chosen to represent the json:
        // * Objects
        //   * Object "Results"
        //     * InfoItem
        //       * value
        //     * InfoItem
        //       * value
        //     * ...
        
        ArrayList<OdfObject> objectsChildren = new ArrayList<OdfObject>();

        // create Object "Results"
        OdfObject myDataObject = myResultDataToOdf(myData);
        objectsChildren.add(myDataObject);


        OdfObjects objects = OdfFactory.createOdfObjects(objectsChildren, "1.0");
        return objects;

        // PRO TIP: Another way to create O-DF Objects hierarchies is to create only the leaf InfoItems
        // then call .createAncestors() to each of the InfoItems to create a list of OdfObjects
        // and then reduce the list to single OdfObjects with a.union(b).
        // This might be more inefficent in terms of performance, but a lot easier/faster to code for simple O-DFs.
        // Docs:
        // https://otaniemi3d.cs.hut.fi/omi/node/html/api/java/types/OdfTypes/OdfNode.html#createAncestors--
        // https://otaniemi3d.cs.hut.fi/omi/node/html/api/java/types/OdfTypes/OdfObjectsImpl.html#union-types.OdfTypes.OdfObjects-
    }

    private final ArrayList<OdfObject> emptyObjects = new ArrayList<OdfObject>();
    //private final ArrayList<OdfInfoItem> emptyInfoItems = new ArrayList<OdfInfoItem>();

    
    public OdfObject myResultDataToOdf(MyResultData myData) {

        Path resultsPath = new Path("Objects/Results");

        // Results Object will have InfoItems as children
        ArrayList<OdfInfoItem> infoItemChildren = new ArrayList<OdfInfoItem>();

        for (MyValue myVal: myData.results) {

            String name = myVal.name;
            Path path = new Path("Objects/Results/"+name);

            ArrayList<OdfValue<Object>> values = myValueToOdf(myVal);

            OdfInfoItem child = OdfFactory.createOdfInfoItem(path, values);

            infoItemChildren.add(child);
        }

        OdfObject myResultsObject = OdfFactory.createOdfObject(resultsPath, infoItemChildren, emptyObjects);

        return myResultsObject;
    }


    public ArrayList<OdfValue<Object>> myValueToOdf(MyValue myVal) {

        ArrayList<OdfValue<Object>> values = new ArrayList<OdfValue<Object>>();

        // for ignoring the type, use String
        OdfValue<Object> odfValue = OdfFactory.createOdfValue(myVal.value, getCurrentTime());
        values.add(odfValue);

        return values;
    }



    // This method is run when call request is received and application.conf settings
    // are configured to route the request to this agent.
    public Future<ResponseRequest> handleCall(CallRequest callRequest){


        try{
            // fetch the InfoItem containing the rpc parameters from the O-DF of the incoming call request
            OdfNode node = callRequest.odf().get(callPath).get();

            OdfInfoItem ii = (OdfInfoItem) node;

            // The parameters are contained in another O-DF hierarchy inside the <value> element
            OdfValue<Object> paramOdfValue = ii.values().head(); // head = first element

            OdfObjects parameterOdf = (OdfObjects) paramOdfValue.value();

            // Parameter should have the format:
            // * Objects
            //   * Object MyParameters
            //     * InfoItem MyParameter

            // Query O-DF with a Path
            Path queryPath = new Path("Objects/MyParameters/MyParameter");

            OdfInfoItem myParameterItem = (OdfInfoItem) parameterOdf.get(queryPath).get(); 
            // Returns scala.Option, for which we call .get() as we don't care about errors atm.


            // Try to parse (if the caller gave the parameter as xs:string type)
            //int myParameter = Integer.parseInt((String) myParameterItem.values().head().value());
            // ... Or if the <value> has right type attribute, it is already parsed:
            int myParameter = (int) myParameterItem.values().head().value();

            OdfObjects returnOdf = jsonToOdf(myJsonSource[myParameter]);

            return Futures.successful( 
                    Responses.Success(
                        scala.Option.apply(returnOdf),
                        Duration.apply(10,TimeUnit.SECONDS)
                        )
                    );
        } catch(Exception exp ) {
            return Futures.successful( 
                    Responses.InternalError(exp)
                    );
        }
    }



    public Timestamp getCurrentTime() {
        return new Timestamp(new java.util.Date().getTime());
    }


    // Boilerplate for Akka Actors
    static public Props props(final Config config, final ActorRef requestHandler,final ActorRef dbHandler){
        return Props.create(
                new Creator<JavaCallRequestExample>() {
                    private static final long serialVersionUID = 35735155L;

                    @Override
                    public JavaCallRequestExample create() throws Exception {
                        return new JavaCallRequestExample(config, requestHandler, dbHandler);
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
}



