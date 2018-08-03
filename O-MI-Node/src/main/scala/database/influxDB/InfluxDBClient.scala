package database.influxDB

import akka.actor.ActorSystem
import akka.event.{LoggingAdapter, LogSource}
import scala.concurrent.ExecutionContext
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
  case class AcceptHeader(format: String) extends ModeledCustomHeader[AcceptHeader] {
    override def renderInRequests: Boolean = true

    override def renderInResponses: Boolean = false

    override val companion: AcceptHeader.type = AcceptHeader

    override def value: String = format
  }

  object AcceptHeader extends ModeledCustomHeaderCompanion[AcceptHeader] {
    override val name = "Accept"

    override def parse(value: String) = Try(new AcceptHeader(value))
  }
  def queriesToHTTPPost( queries: Seq[InfluxQuery],readAddress: Uri): HttpRequest ={
    val httpEntity = FormData(("q", queries.map(_.query).mkString(";\n")) ).toEntity(HttpCharsets.`UTF-8`)
    RequestBuilding.Post(readAddress, httpEntity).withHeaders(AcceptHeader("application/json"))
  }
  def httpResponseToStrict(futureResponse: Future[HttpResponse],log: LoggingAdapter)(implicit ec: ExecutionContext, materializer: Materializer): Future[HttpEntity.Strict] = {
    futureResponse.flatMap {
      case response@HttpResponse(status, headers, entity, protocol) if status.isSuccess =>
        entity.toStrict(10.seconds)
      case response@HttpResponse(status, headers, entity, protocol) if status.isFailure =>
        entity.toStrict(10.seconds).flatMap { stricted =>
          Unmarshal(stricted).to[String].map {
            str =>
              log.warning(s""" InfluxQuery returned $status with:\n $str""")
              throw new Exception(str)
          }
        }
    }
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
  implicit val logSourceType: LogSource[InfluxDBClient] = new LogSource[InfluxDBClient] {
    def genString(a:InfluxDBClient) = s"InfluxClient:${a.config.address}:${a.config.databaseName}"
  }
  def log: LoggingAdapter

  def sendQueries(queries: Seq[InfluxQuery]): Future[HttpResponse] = {
    val request =  queriesToHTTPPost(queries.toVector,readAddress)
    val responseF: Future[HttpResponse] = httpExt.singleRequest(request) //httpHandler(request)
    responseF
  }

  def sendMeasurements(measurements: String ): Future[HttpResponse] = {
    val request = RequestBuilding.Post(writeAddress, measurements)
    val response = httpExt.singleRequest(request)
    response
  }
}
