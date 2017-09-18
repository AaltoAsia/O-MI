package database

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

import akka.actor.Extension
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri._
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._


 
trait InfluxDBConfigExtension extends Extension {
  def config: Config


  //Warp10 tokens and address
  val influxDBdatabaseName: String = config.getString("influxDB.database-name")
  val influxDBAddress : Uri = Uri( config.getString("influxDB.address") )

  val query= Query((s"db",s"$influxDBdatabaseName"),(s"precission","ms"))
  val influxDBQueryAddress : Uri = influxDBAddress.withPath( Path("/query")).withQuery(query)
  val influxDBWriteAddress : Uri = influxDBAddress.withPath(Path("/write")).withQuery(query)
}

