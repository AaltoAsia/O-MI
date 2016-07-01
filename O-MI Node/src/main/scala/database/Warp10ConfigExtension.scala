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


  //Warp10
  val warp10ReadToken : String = "4kNNDT1O6y4awN_mKF65iEqWziXrnoik5avMWV91crCvfKFStTXwsB3JIn1Q2lCIt1D2n.C6HeoEnDVfEF.DiPAmw7kmCyBLnEtLL40BpQLvuY4vSJXRlstmJQn9m.XZMRFcXKALtIIo5IBnA2myGmile7qAZClT"
  //val warp10ReadToken : String = config.getString("warp10.read-token")
  val warp10WriteToken : String = "bOhkbG8Abht0BDXoA5oyboWSAE3n8cGgNguVEj_N49tquqJlQW5ZV.QLzfGvVHF7mH_qRk.MkhCP_WIdkRvYC2WSZjRS3msEh38y3._NWlRLE_ZS9Wiyc4Fn_vTQZm2B"
  //val warp10WriteToken : String = config.getString("warp10.write-token")
  val warp10Address : String = config.getString("warp10.address")
  
}
