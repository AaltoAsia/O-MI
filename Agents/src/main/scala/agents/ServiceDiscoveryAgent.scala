package agents

import java.sql.Timestamp
import java.util.concurrent.TimeUnit
import java.net.InetAddress

import collection.JavaConverters._
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import agentSystem._
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.typesafe.config.{Config, ConfigValue}
import types.omi.{OmiResult, ResponseRequest, Results, WriteRequest}
import types.Path
import types.odf._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Random, Success}
import utils.RichConfig

/**
 * Companion object for ScalaAgent. Extends PropsCreator to enforce recommended practice in Props creation.
 *  <a href="http://doc.akka.io/docs/akka/2.4/scala/actors.html#Recommended_Practices">Akka recommends to</a>.
 *
 */
object ServiceDiscoveryAgent extends PropsCreator{
  /**
   * Method for creating Props for ScalaAgent.
   *  @param config Contains configuration for this agent, as given in application.conf.
   */
  def props( 
    config: Config,
    requestHandler: ActorRef,
    dbHandler: ActorRef
    ) : Props = {
    Props(new ServiceDiscoveryAgent(config, requestHandler, dbHandler))
  }

}

/**
  * Pushes random numbers to given O-DF path at given interval.
 * Can be used in testing or as a base for other agents.
 * Extends ScalaInternalAgentTemplate that implements some basic functionality.
 *
 * @param config Contains configuration for this agent, as given in application.conf.
 */
class ServiceDiscoveryAgent(
                             val config: Config,
                           requestHandler: ActorRef,
                           dbHandler: ActorRef
)  extends ScalaInternalAgentTemplate(requestHandler, dbHandler) {//ScalaInternalAgentTemplate(requestHandler, dbHandler){

  val address: Option[InetAddress] = config.getOptionalString("address").map(InetAddress.getByName(_))
  val hostname: Option[String] = config.getOptionalString("hostname")

  val serviceType: String = config.getString("serviceType")
  val serviceName: String = config.getString("serviceName")
  val serviceSubType: String = config.getString("serviceSubType")
  val servicePort: Int = config.getInt("servicePort")
  val conf: Config = config.getConfig("props")
  val props = conf.root.keySet().asScala.map(key => key -> conf.getString(key)).toMap

  val jmdns: JmDNS = JmDNS.create(address.getOrElse(null),hostname.getOrElse(null))
  val serviceInfo: ServiceInfo = ServiceInfo.create(
    serviceType,
    serviceName,
    serviceSubType,
    servicePort,
    0,
    0,
    false,
    props.asJava
  )

  jmdns.registerService(serviceInfo)

  /**
   * Method that is inherited from akka.actor.Actor and handles incoming messages
   * from other Actors.
   */
  override def receive: Actor.Receive = {
    case msg =>
  }

  /**
   * Method to be called when Agent is stopped.
   * This should gracefully stop all activities that the agent is doing.
   */
  override def postStop : Unit = {
    jmdns.unregisterAllServices()
  }
}
