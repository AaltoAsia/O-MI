package agents

import java.sql.Timestamp
import java.util.Date
import scala.util.Try

import types._
import types.odf._
import types.ParseError

case class MVError( msg: String ) extends ParseError(msg, "MobiVoc error:")

package object parking{


  def currentTimestamp: Timestamp = new Timestamp( new Date().getTime())
  def getStringOption(name: String, path: Path, odf: ImmutableODF): Option[String] = {
    odf.get( path / name ).flatMap{
      case ii:InfoItem =>
        ii.values.map{
          case sv: StringValue => sv.value
          case sv: StringPresentedValue => sv.value
          case v: Value[Any] => v.value.toString
        }.headOption
      case ii:Node =>
        throw MVError( s"$name should be an InfoItem.")
    }
  }
  def getLongOption(name: String, path: Path, odf: ImmutableODF): Option[Long] = {
    odf.get( path / name ).flatMap{
      case ii:InfoItem =>
        ii.values.collectFirst {
          case value: ShortValue =>
            Option(value.value.toLong)
          case value: IntValue =>
            Option(value.value.toLong)
          case value: LongValue =>
            Option(value.value)
          case value: StringValue => Try{value.value.toLong}.toOption
          case value: StringPresentedValue =>  Try{value.value.toLong}.toOption
        }.flatten
      case ii:Node =>
        throw MVError( s"$name should be an InfoItem.")
    }
  }
  def getDoubleOption(name: String, path: Path, odf: ImmutableODF): Option[Double] = {
    odf.get( path / name ).flatMap{
      case ii:InfoItem =>
        ii.values.collectFirst {
          case value: FloatValue =>
            Option(value.value.toDouble)
          case value: DoubleValue =>
            Option(value.value)
          case value: ShortValue =>
            Option(value.value.toDouble)
          case value: IntValue =>
            Option(value.value.toDouble)
          case value: LongValue =>
            Option(value.value.toDouble)
          case value: StringValue => Try{value.value.toDouble}.toOption
          case value: StringPresentedValue =>  Try{value.value.toDouble}.toOption
        }.flatten
      case ii:Node =>
        throw MVError( s"$name should be an InfoItem.")
    }
  }
  def getBooleanOption(name: String, path: Path, odf: ImmutableODF): Option[Boolean] = {
    odf.get( path / name ).flatMap{
      case ii:InfoItem =>
        ii.values.collectFirst {
          case value: StringValue if value.value.toLowerCase == "true" => true
          case value: StringValue if value.value.toLowerCase == "false" => false
          case value: StringPresentedValue if value.value.toLowerCase == "true" => true
          case value: StringPresentedValue if value.value.toLowerCase == "false" => false
          case value: StringValue if value.value == "1" => true
          case value: StringValue if value.value == "0" => false
          case value: StringPresentedValue if value.value == "1" => true
          case value: StringPresentedValue if value.value == "0" => false
          case value: IntValue => value.value == 1
          case value: BooleanValue =>
            value.value
        }
      case ii:Node =>
        throw MVError( s"$name should be an InfoItem.")
    }
  }
}
