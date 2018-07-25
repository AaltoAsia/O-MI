package database
package influxDB

import akka.actor.ActorSystem
import org.slf4j.{Logger}
import akka.stream.{ActorMaterializer, Materializer}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.unmarshalling._
import scala.concurrent.{Future}
import scala.concurrent.duration._
import scala.util.Try

object InfluxDBClient {
  final class AcceptHeader(format: String) extends ModeledCustomHeader[AcceptHeader] {
    override def renderInRequests: Boolean = true

    override def renderInResponses: Boolean = false

    override val companion: AcceptHeader.type = AcceptHeader

    override def value: String = format
  }

  object AcceptHeader extends ModeledCustomHeaderCompanion[AcceptHeader] {
    override val name = "Accept"

    override def parse(value: String) = Try(new AcceptHeader(value))
  }
}

trait InfluxDBClient {

  protected val config: InfluxDBConfigExtension
  protected val writeAddress: Uri = config.writeAddress //Get from config
  protected val readAddress: Uri = config.queryAddress //Get from config

  import InfluxDBClient._
  implicit val system: ActorSystem
  import system.dispatcher // execution context for futures
  val httpExt = Http(system)
  implicit val mat: Materializer = ActorMaterializer()
  def log: Logger 

  def httpResponseToStrict(futureResponse: Future[HttpResponse]): Future[HttpEntity.Strict] = {
    futureResponse.flatMap {
      case response@HttpResponse(status, headers, entity, protocol) if status.isSuccess =>
        entity.toStrict(10.seconds)
      case response@HttpResponse(status, headers, entity, protocol) if status.isFailure =>
        entity.toStrict(10.seconds).flatMap { stricted =>
          Unmarshal(stricted).to[String].map {
            str =>
              log.warn(s""" Query returned $status with:\n $str""")
              throw new Exception(str)
          }
        }
    }
  }

  def sendQuery(query: String): Future[HttpResponse] = {
    val httpEntity = FormData(("q", query)).toEntity(HttpCharsets.`UTF-8`)
    val request = RequestBuilding.Post(readAddress, httpEntity).withHeaders(AcceptHeader("application/json"))
    val responseF: Future[HttpResponse] = httpExt.singleRequest(request) //httpHandler(request)
    responseF
  }

  def sendMeasurements(measurements: String ): Future[HttpResponse] = {
    val request = RequestBuilding.Post(writeAddress, measurements)
    val response = httpExt.singleRequest(request)
    response
  }
}
