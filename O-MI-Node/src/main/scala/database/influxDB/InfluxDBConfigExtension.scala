package database
package influxDB

import akka.actor.{ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider}
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri._
import com.typesafe.config.{Config, ConfigException}

import scala.util.Try

class InfluxDBConfigExtension(config: Config) extends Extension {
  //Warp10 tokens and address
  val databaseName: String = config.getString("influxDB-config.database-name")
  val address: Uri = Uri(config.getString("influxDB-config.address"))
  val userO: Option[String] = Try(Some(config.getString("influxDB-config.user"))).recover {
    case exp: ConfigException.Missing => None
  }.get
  val passwdO: Option[String] = Try(Some(config.getString("influxDB-config.password"))).recover {
    case exp: ConfigException.Missing => None
  }.get

  val query: Query = (userO, passwdO) match {
    case (Some(user), Some(passwd)) =>
      Query((s"db", s"$databaseName"), (s"precision", "ms"), (s"u", user), ("p", passwd))
    case (None, None) =>
      Query((s"db", s"$databaseName"), (s"precision", "ms"))
    case (_, _) => throw new Exception("Either user or password for influxdb is missing.")
  }
  val queryAddress: Uri = address.withPath(Path("/query")).withQuery(query)
  val writeAddress: Uri = address.withPath(Path("/write")).withQuery(query)
}

object InfluxDBConfig extends ExtensionId[InfluxDBConfigExtension] with ExtensionIdProvider {

  override def lookup: InfluxDBConfig.type = InfluxDBConfig

  override def createExtension(system: ExtendedActorSystem): InfluxDBConfigExtension =
    new InfluxDBConfigExtension(system.settings.config)

  /**
    * Java API: retrieve the Settings extension for the given system.
    */
  override def get(system: ActorSystem): InfluxDBConfigExtension = super.get(system)
}
