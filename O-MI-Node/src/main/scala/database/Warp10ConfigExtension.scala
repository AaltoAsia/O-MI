package database

import java.util.concurrent.TimeUnit

import scala.collection.JavaConversions._
import scala.concurrent.duration._
import scala.util.{Success, Failure, Try}

import akka.actor.Extension
import akka.http.scaladsl.model.Uri
import com.typesafe.config.Config
import com.typesafe.config.ConfigException._
import types.Path


 
trait Warp10ConfigExtension extends Extension {
  def config: Config


  //Warp10 tokens and address
  val warp10ReadToken : String = config.getString("warp10.read-token")
  val warp10WriteToken : String = config.getString("warp10.write-token")
  val warp10Address : String = config.getString("warp10.address")
  
}
