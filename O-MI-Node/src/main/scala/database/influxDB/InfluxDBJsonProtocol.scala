package database.influxDB

import java.sql.Timestamp
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json._
import types.Path
import types.Path._
import types.odf._

import scala.collection.immutable

object InfluxDBJsonProtocol extends DefaultJsonProtocol {
  def getSeries(json: spray.json.JsValue): immutable.Seq[JsValue] = json match {
    case obj: JsObject =>
      obj.getFields("results").collect {
        case results: JsArray =>
          results.elements.collect {
            case statementObj: JsObject =>
              statementObj.getFields("series").collect {
                case series: JsArray =>
                  series.elements
              }.flatten
          }.flatten
      }.flatten
    case other => immutable.Seq.empty[JsValue] //should this throw error instead?
  }

  def measurementNameToPath(measurementName: String): Path = Path(measurementName.replace("\\=", "=")
    .replace("\\ ", " ").replace("\\,", ","))

  class InfluxDBJsonShowDatabasesFormat() extends RootJsonFormat[Seq[String]] {
    def read(json: spray.json.JsValue): Seq[String] = {
      val series: Seq[JsValue] = getSeries(json)
      //println( s"Got ${series.lenght} from show databases" )
      val names: Seq[String] = series.collect {
        case serie: JsObject =>

          serie.getFields("name", "columns", "values") match {
            case Seq(JsString("databases"), JsArray(Seq(JsString("name"))), JsArray(values)) =>
              values.collect {
                case JsArray(Seq(JsString(dbName))) => dbName
              }

            case seq: Seq[JsValue] => Vector.empty
          }
      }.flatten
      names
    }

    def write(obj: Seq[String]): spray.json.JsValue = ???
  }

  class InfluxDBJsonShowMeasurementsFormat() extends RootJsonFormat[Seq[Path]] {
    def read(json: spray.json.JsValue): Seq[Path] = {
      val names: Seq[Path] = getSeries(json).collect {
        case serie: JsObject =>
          serie.getFields("name", "columns", "values") match {
            case Seq(JsString("measurements"), JsArray(Seq(JsString("name"))), JsArray(values)) =>
              values.collect {
                case JsArray(Seq(JsString(strPath))) =>
                  val path = measurementNameToPath(strPath)
                  path
              }

            case seq: Seq[JsValue] => Vector.empty
          }
      }.flatten
      names
    }

    def write(obj: Seq[Path]): spray.json.JsValue = ???
  }

  class InfluxDBJsonODFFormat() extends RootJsonFormat[ImmutableODF] {
    // Members declared in spray.json.JsonReader
    def read(json: spray.json.JsValue): types.odf.ImmutableODF = {
      //println( s"Got following json: $json")
      val series = getSeries(json)
      //println( s"Found ${series.length} series")
      val iis: Seq[InfoItem] = series.collect {
        case serie: JsObject =>
          serie.getFields("name", "columns", "values") match {
            case Seq(JsString(measurementName), JsArray(columns), JsArray(values)) =>
              Some(serieToInfoItem(serie))
            case seq: Seq[JsValue] =>
              None
          }
      }.flatten
      //println( s"Found ${iis.length} series")
      ImmutableODF(iis.toVector)
    }

    def serieToInfoItem(serie: JsObject): InfoItem = {
      serie.getFields("name", "columns", "values") match {
        case Seq(JsString(measurementName), JsArray(Vector(JsString("time"), JsString("value"))), JsArray(values)) =>
          val path = measurementNameToPath(measurementName)
          InfoItem(path.last, path, values = values.collect {
            case JsArray(Seq(JsString(timestampStr), JsNumber(number))) =>
              val timestamp: Timestamp = Timestamp.valueOf(timestampStr.replace("T", " ").replace("Z", " "))
              Value(number, timestamp)
            case JsArray(Seq(JsString(timestampStr), JsBoolean(bool))) =>
              val timestamp: Timestamp = Timestamp.valueOf(timestampStr.replace("T", " ").replace("Z", " "))
              Value(bool, timestamp)
            case JsArray(Seq(JsString(timestampStr), JsString(str))) =>
              val timestamp: Timestamp = Timestamp.valueOf(timestampStr.replace("T", " ").replace("Z", " "))
              Value(str.replace("\\\"", "\""), timestamp)
          })
      }
    }

    // Members declared in spray.json.JsonWriter
    def write(obj: types.odf.ImmutableODF): spray.json.JsValue = ???
  }

}
