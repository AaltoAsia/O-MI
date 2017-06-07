package agents

import java.sql.Timestamp
import java.util.Date
import types.OdfTypes._

package object parkingService{

  def getStringFromInfoItem( iI: OdfInfoItem): Option[String] ={
        iI.values.headOption.map{ value => value.value.toString} 
  }
  def getDoubleFromInfoItem( iI: OdfInfoItem): Option[Double] ={
            iI.values.headOption.map{
              value => 
                value.value match {
                  case d: Double => d
                  case f: Float => f.toDouble
                  case s: String => s.toDouble
                  case a: Any => a.toString.toDouble
              }
            } 
  }
  def currentTime = new Timestamp( new Date().getTime )
} 
