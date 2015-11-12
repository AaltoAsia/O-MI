package agents

import agentSystem.InternalAgent
import agentSystem.InternalAgentExceptions.{AgentException, AgentInitializationException, AgentInterruption}
import agentSystem.InputPusher
import types._
import types.OdfTypes._
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }

class ScalaAgent  extends InternalAgent{
	val rnd: Random = new Random()
	var pathO: Option[Path] = None
  def date = new java.util.Date();
  var initialised = false
  override def init( config: String){
    try{
      pathO = Some( new Path(config))
      initialised = true
      InternalAgent.log.warning("ScalaAgent has been initialised.");
    }catch{
      case e : Exception =>
      InternalAgent.log.warning("ScalaAgent has caucth exception turing initialisation.");
      InternalAgent.loader ! AgentInitializationException(this,e) 
      InternalAgent.log.warning("ScalaAgent has died.");
    }finally{
    }
  }
  override def run(): Unit = {
    try{
      while(!Thread.interrupted() && pathO.nonEmpty && initialised){

        val tuple = pathO.map{
          path => 
          ( 
            path,
            OdfValue(
              rnd.nextInt().toString, 
              "xs:integer",
              new Timestamp( date.getTime() )
            ) 
          ) 
        }
        val values = Iterable(tuple).flatten
        InternalAgent.log.info("ScalaAgent pushing data.");
        InputPusher.handlePathValuePairs(values);
        Thread.sleep(10000);
      }     
    }catch{
      case e : InterruptedException =>
      InternalAgent.log.warning("ScalaAgent has been interrupted.");
      InternalAgent.loader ! AgentInterruption(this,e) 
      case e : Exception =>
      InternalAgent.log.warning("ScalaAgent has caught an exception");
      InternalAgent.loader ! AgentException(this,e) 
    }finally{
      InternalAgent.log.warning("ScalaAgent has died.");
    }
  }
}
