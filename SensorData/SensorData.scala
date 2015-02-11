package sensordata

// Json4s
import org.json4s._
import org.json4s.native.JsonMethods._


// Akka Actor system
import akka.actor.ActorSystem
 
// HTTP related imports
import spray.http.{ HttpRequest, HttpResponse }
import spray.client.pipelining._
 
// Futures related imports
import scala.concurrent.Future
import scala.util.{ Success, Failure } 

// Scala XML
import scala.xml
import scala.xml._

// Mutable map for sensordata
import scala.collection.mutable.Map

// Need to wrap in a package to get application supervisor actor
// "you need to provide exactly one argument: the class of the application supervisor actor"
package main.scala {

  // trait with single function to make a GET request
  trait WebClient {
    def get(url: String): Future[String]
  }

  // implementation of WebClient trait
  class SprayWebClient(implicit system: ActorSystem) extends WebClient {
    import system.dispatcher

    // create a function from HttpRequest to a Future of HttpResponse
    val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

    // create a function to send a GET request and receive a string response
    def get(url: String): Future[String] = {
      val futureResponse = pipeline(Get(url))
      futureResponse.map(_.entity.asString)
    }
  }

  /**
   * The main program for getting SensorData
   */
  object Program extends App {
    import scala.concurrent.ExecutionContext.Implicits.global
    // bring the actor system in scope
    implicit val system = ActorSystem()
    // Define formats
    implicit val formats = DefaultFormats
    
    // create the client
    val webClient = new SprayWebClient()(system)

    // send GET request with absolute URI (http://121.78.237.160:2100/)
    val futureResponse = webClient.get("http://121.78.237.160:2100/")
    
    // wait for Future to complete
    futureResponse onComplete {
      case Success(response) => 
        // Json data received from the server
        val json = parse(response)

        // List of (sensorname, value) objects
        val list = for {
          JObject(child) <- json
          JField(sensor, JString(value)) <- child
        } yield (sensor, value)
        
        val odf = generateODF(list)
        
        // Print the formatted data
        val formattedXML = new PrettyPrinter(80, 2).format(odf)
        println(formattedXML);
        
        //System.exit(1) // Exit needed?
      case Failure(error) => println("An error has occured: " + error.getMessage)
    }
    
    /**
     * Generate ODF from the parsed & formatted Json data
     * @param list of sensor-value pairs
     * @return generated XML Node
     */
    private def generateODF(list : List[(String, String)]) : scala.xml.Node = {
    	
    	// Define dateformat for dateTime value
    	val dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    	val date = new java.util.Date()
    	val dateTime = dateFormat.format(date).replace(' ', 'T')
    	
    	// Initialize objects and infoitems
    	val objects = Map[String, Map[String, String]]()
        val infoItems = Map[String, String]()
        
        // Iterate over the list to get objects and infoitems
        for(item <- list){
        	val sensor : String = item._1
        	val value : String = item._2 // Currently as string, convert to double?
        	
        	// Split name from underlines
        	val split = sensor.split('_')
        	
        	if(split.length > 3){
	        	// Object id
	        	val objectId : String = split(0) + "_" + split(1) + "_" + split.last
	        	val infoItemName : String = split.drop(2).dropRight(1).mkString("_")
	        	
	        	val temp = objects.find(_._1 == objectId)
	        	
	        	// Append the object parameters to the map
	        	if(temp.isDefined){
	        	  temp.get._2.put(infoItemName, value) // Existing object add new key-value pair to the map
	        	} else {
	        	  objects.put(objectId, Map((infoItemName, value))) // New object create new key-value pair
	        	}
        	} else {
        	  infoItems.put(sensor, value)
        	}
        }
    	
    	// Return generated XML
        generateXML(objects, infoItems, dateTime)
    }
    
    /**
     * Generate XML in O-DF
     * @param objects mapped as (objectId -> (InfoItem name -> InfoItem value))
     * @param infoItems (without objects) mapped as (InfoItem name -> InfoItem value)
     * @param dateTime value
     * @return generated XML Node
     */
    private def generateXML(objects : Map[String, Map[String, String]], infoItems : Map[String, String], 
        dateTime : String) : scala.xml.Node = {
      
      val xml = <Objects>
        			{
        				var node: NodeSeq = NodeSeq.Empty
        				
        				for(o <- objects) {
        				  node ++=
        				  <Object>
        					<id>{o._1}</id>
        					{
        					  	var infoItemNode: NodeSeq = NodeSeq.Empty
        						for(item <- o._2) {
        							infoItemNode ++=
        								<InfoItem name={item._1}>
        					    			<value dateTime={dateTime}>{item._2}</value>
        					    		</InfoItem>
        					  	}
        					  	infoItemNode
        					}
        				  	</Object>
        				}
        				for(item <- infoItems){
        				  node ++=
        				    <InfoItem name={item._1}>
        					    <value dateTime={dateTime}>{item._2}</value>
        					</InfoItem>
        				}
        				
        			 	node
        			}
        		</Objects>
      xml
    }
  }
} 