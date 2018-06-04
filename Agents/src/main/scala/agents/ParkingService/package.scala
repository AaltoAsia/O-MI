package agents

import java.sql.Timestamp
import java.util.Date
import types.OdfTypes._

package object parkingService{

  def getStringFromInfoItem( iI: OdfInfoItem): Option[String] ={
        iI.values.headOption.map{ value => value.value.toString} 
  }
  def getBooleanFromInfoItem( iI: OdfInfoItem): Option[Boolean] ={
        iI.values.headOption.map{ value => 
          value.value match {
            case b: Boolean  => b
            case str: String => 
            str.toLowerCase match{
              case "true" => true
              case "false" => false
            }
            case str: Any => str.toString.toLowerCase match{
              case "true" => true
              case "false" => false
            }
          }
        } 
  }
  def getLongFromInfoItem( iI: OdfInfoItem): Option[Long] ={
    iI.values.headOption.map{ value => 
      value.value match {
        case l: Long => l
        case i: Int => i
        case s: String => s.toInt
        case s: Any => s.toString.toInt
      }
    } 
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
  def currentTime: Timestamp = new Timestamp( new Date().getTime )
} 
