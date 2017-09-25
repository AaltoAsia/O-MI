package database
package influxDB

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri._
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._

class InfluxDBConfigExtension( config: Config) extends Extension {
  //Warp10 tokens and address
  val databaseName: String = config.getString("influxDB-config.database-name")
  val address : Uri = Uri( config.getString("influxDB-config.address") )

  val query= Query((s"db",s"$databaseName"),(s"precision","ms"))
  val queryAddress : Uri = address.withPath( Path("/query")).withQuery(query)
  val writeAddress : Uri = address.withPath(Path("/write")).withQuery(query)
}

object InfluxDBConfig extends ExtensionId[InfluxDBConfigExtension] with ExtensionIdProvider {

  override def lookup: InfluxDBConfig.type = InfluxDBConfig
   
  override def createExtension(system: ExtendedActorSystem) : InfluxDBConfigExtension =
    new InfluxDBConfigExtension(system.settings.config)
   
  /**
  * Java API: retrieve the Settings extension for the given system.
  */
  override def get(system: ActorSystem): InfluxDBConfigExtension = super.get(system)
}
