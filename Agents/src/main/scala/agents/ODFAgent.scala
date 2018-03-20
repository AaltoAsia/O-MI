package agents

import agentSystem._ 
import akka.util.Timeout
import akka.pattern.ask
import akka.actor.{Cancellable, Props, ActorRef}
import parsing.OdfParser
import types.{Path, ParseError, ParseErrorList}
import types.Path._
import types.OdfTypes._
import types.OmiTypes.{WriteRequest, ResponseRequest, OmiResult, Results}
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable}
import scala.collection.mutable.{Queue => MutableQueue}
import scala.xml._
import scala.util.{Random, Try, Success, Failure}
import scala.math.Numeric
import java.util.concurrent.TimeUnit
import types.odf._
import java.util.Date
import java.sql.Timestamp;
import java.io.File
import com.typesafe.config.Config

object ODFAgent extends PropsCreator{
  def props( config: Config, requestHandler: ActorRef, dbHandler: ActorRef ): Props = {
    Props( new ODFAgent(config, requestHandler, dbHandler) )
  }
}
// Scala XML contains also parsing package
class ODFAgent(
  val config: Config,
  requestHandler: ActorRef, 
  dbHandler: ActorRef
) extends ScalaInternalAgentTemplate(requestHandler,dbHandler){
   val interval : FiniteDuration= config.getDuration("interval", TimeUnit.SECONDS).seconds
   val odfQueue : MutableQueue[ODF]= MutableQueue()
  
  import scala.concurrent.ExecutionContext.Implicits._
  case class Update()
	
  val rnd: Random = new Random()
  def date: Date = new java.util.Date()

  override def preStart: Unit ={
    val file =  new File(config.getString("file"))
    if( file.exists() && file.canRead() ){
      val xml = XML.loadFile(file)
      ODFParser.parse( xml ) match {
        case Left( errors ) =>
          val msg = errors.mkString("\n")
          log.warning(s"Odf has errors, $name could not be configured.")
          log.debug(msg)
          throw ParseError.combineErrors( errors )
        case Right(odfObjects) => odfQueue.enqueue(odfObjects)
      }
    } else if( file.exists() ){
      val msg = s"File $config could not be read. $name could not be configured."
      log.warning(msg)
      throw AgentConfigurationException( msg )
    } else {
      val msg = s"File $config did not exists. $name could not be configured."
      log.warning(msg)
      throw AgentConfigurationException( msg )
    }
  }

  // Schelude update and save job, for stopping
  // Will send Update message to self every interval
  private val  updateSchelude : Cancellable = context.system.scheduler.schedule(
    Duration(0, SECONDS),
    interval,
    self,
    Update()
  )

   def update() : Unit = {
    log.debug(s"$name pushing data.")
    odfQueue.dequeueFirst{o: ODF => true}
    .foreach{
      odf =>

      // Collect metadata 
      val updated = odf.getInfoItems.map{ 
        infoItem: InfoItem  => 
          val newVal = infoItem.values.lastOption.map{
            value: Value[Any] => 
              genValue(value, new Timestamp( date.getTime() ))
            }
        infoItem.copy( values = newVal.toVector)
      }
      val newODF = ImmutableODF( updated ++ odf.valuesRemoved.nodesWithStaticData )
      
      log.debug(s"Create write request")
      val write = WriteRequest( newODF, None, interval)
      log.debug(s"Request done.")
      val result = writeToDB( write)
      result.onComplete{
        case Success( response: ResponseRequest )=>
          response.results.foreach{ 
            case wr: Results.Success =>
              // This sends debug log message to O-MI Node logs if
              // debug level is enabled (in logback.xml and application.conf)
              log.debug(s"$name wrote paths successfully.")
            case ie: OmiResult => 
              log.warning(s"Something went wrong when $name writed, $ie")
          }
            case Failure( t: Throwable) => 
              // This sends debug log message to O-MI Node logs if
              // debug level is enabled (in logback.xml and application.conf)
              log.warning(s"$name's write future failed, error: $t")
      }
      odfQueue.enqueue(newODF)
    } 
  }

  override  def receive: PartialFunction[Any, Unit] = {
    case Update() => update()
  }
   override def postStop: Unit = {
    updateSchelude.cancel()
   }
  
  private def genValue(value: Value[Any], nts: Timestamp ) : Value[Any] = {
    value match {
        case sval: StringPresentedValue => sval.copy( timestamp = nts )
        case sval: StringValue => sval.copy( timestamp = nts )
        case bval: BooleanValue => 
          if( math.abs(Random.nextGaussian) > 1 ){
            Value(!bval.value,nts)
          } else bval.copy(timestamp =nts)
        case nval: Value[Any] =>
          nval.value match{
            case oldValue: Int => 
              Value(oldValue + (Random.nextGaussian*oldValue*0.10), nts )
            case v => Value(v, nts, nval.attributes )
          }
    }
  }
}
