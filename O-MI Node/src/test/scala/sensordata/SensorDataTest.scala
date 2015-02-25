package sensordata

import org.specs2.mutable._
import akka.testkit.{TestProbe, TestKit, EventFilter}
import org.specs2.specification.Scope
import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import spray.http.{HttpRequest, HttpResponse, HttpEntity}
class SensorDataTest extends Specification {
//  val sensorData = new SensorData{
//
//  }
  class testActors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with Scope
  
  implicit val system = ActorSystem()
  "sensorData object" should {
    sequential
    "should turn loading flag true while waiting for data" in new testActors{
      val probe = TestProbe()
      val sensorData = new SensorData{override def httpRef = probe.ref}
      sensorData.loading === false
      sensorData.queueSensors()
      probe.expectMsgType[HttpRequest]
      sensorData.loading === true
      probe.reply("testmsg")      
      sensorData.loading === false
      
    }
    "log error when parsing fails" in new testActors{
      val probe = TestProbe()
      val sensorData = new SensorData{override def httpRef = probe.ref}
      sensorData.queueSensors()
      probe.expectMsgType[HttpRequest]
      probe.reply(new HttpResponse(entity = HttpEntity("""{
    "vtt_otakaari4_humidity_100":"0.85",
    "vtt_otakaari4_temperature_100":"211.28",
    "vtt_otakaari4_temperature_10624":"-545.39"
}
""")))
    }
  }
}