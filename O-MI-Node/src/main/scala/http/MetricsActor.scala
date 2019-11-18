package http

import akka.actor._
import java.sql.Timestamp
import scala.concurrent.duration._
import types.omi.UserInfo
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
//import slick.driver.H2Driver.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable
import slick.lifted.{ProvenShape}
import io.prometheus.client._
import scala.concurrent.ExecutionContext.Implicits.global
//import scala.collection.JavaConversions.iterableAsScalaIterable
import akka.http.scaladsl.model.Uri
import utils._


class MetricsReporter(val configName: String, val settings: OmiConfigExtension) extends Actor with Timers with MonitoringDB{

  import MetricsReporter._
  import dc.profile.api._
  val prometheusServer = if( settings.metricsEnabled ) {
    val tmp = Some(new exporter.HTTPServer(settings.prometheusPort))
    hotspot.DefaultExports.initialize()
    tmp
  } else None

  def checkEnabled[T](f: () => T): Option[T] = if( settings.metricsEnabled ) Some(f()) else None  
  final val uniqueUsersGauge =  checkEnabled(() => Gauge.build().name("omi_unique_users").help("Unique user of O-MI Node over duration").labelNames("duration").register())
  final val requestSizeHistogram =  checkEnabled(() => Histogram.build()
    .name("omi_request_size")
    .help("Size of request per type")
    .labelNames("request")
    .buckets(settings.metrics.requestSizeBuckets:_*)
    .register())
  final val requestResponseSizeHistogram =  checkEnabled(() => Histogram.build()
    .name("omi_request_response_size")
    .help("Size of request per type")
    .labelNames("request")
    .buckets(settings.metrics.requestSizeBuckets:_*)
    .register())
  final val requestDurationHistogram =  checkEnabled(() => Histogram.build()
    .buckets(settings.metrics.requestDurationBuckets:_*)
    .name("omi_request_duration")
    .help("Duration of active O-MI Request")
    .labelNames("request").register()
    )
  final val activeSubscriptionsGauge =  checkEnabled(() => Gauge.build()
    .name("omi_active_subscriptions")
    .help("Count of currently active subscriptions")
    .labelNames("hasCallback","type").register()
    )

  final val callbacksCounter = checkEnabled(() => Counter.build()
    .name("omi_callback_count")
    .help("Count of callback response sending tries")
    .labelNames("succeeded")
    .register()
    )
  final val callbackDurationHistogram =  checkEnabled(() => Histogram.build()
    .buckets(settings.metrics.callbackDurationBuckets:_*)
    .name("omi_callback_duration")
    .help("Duration of receiving a reply to sent O-MI callback responses")
    .labelNames("protocol").register()
    )

  if( settings.metricsEnabled ){
    timers.startPeriodicTimer("report",Report,10.seconds)
  }
  def receive ={
    case NewRequest(requestToken: Long, timestamp: Timestamp, user: UserInfo, requestType: String, attributes: String, pathCount: Int) =>
      db.run(requestLog.add(requestToken, timestamp, user,requestType,attributes,pathCount))
      requestSizeHistogram.map(_.labels(requestType).observe(pathCount))
    case ResponseUpdate( requestToken: Long, requestType:String, timestamp: Timestamp, pathCount: Int, duration: Long) =>
      db.run(requestLog.updateFromResponse(requestToken,timestamp,pathCount,duration))
      requestResponseSizeHistogram.map(_.labels(requestType).observe(pathCount))
      requestDurationHistogram.map{ hist => hist.labels(requestType).observe(duration/1000.0)}
    case NewSubscription( hasCallback: Boolean, typeStr: String) =>
      activeSubscriptionsGauge.map{_.labels(hasCallback.toString,typeStr).inc()}
    case RemoveSubscription( hasCallback: Boolean, typeStr: String) =>
      activeSubscriptionsGauge.map{_.labels(hasCallback.toString,typeStr).dec()}
    case SetSubscriptionCount( hasCallback: Boolean, typeStr: String, count: Int) =>
      activeSubscriptionsGauge.map{_.labels(hasCallback.toString,typeStr).set(count)}
    case CallbackSent( succeeded, timeMs, _ ) =>
      callbacksCounter.map{_.labels(succeeded.toString).inc()}
      callbackDurationHistogram.map{_.labels("http").observe(timeMs/1000.0)}
    case Report => 
      if( settings.metricsEnabled ){
        val current = currentTimestamp
        val millisecondsInDay: Long = 24*60*60*1000
        val pastWeek = new Timestamp(current.getTime() - 7*millisecondsInDay)
        val pastMonth = new Timestamp(current.getTime() - (30.41666*millisecondsInDay).toLong)

        val monthlyCountF: Future[Int] =  db.run(requestLog.uniqueRemoteAddressesAfter( pastMonth ).result)     
        val weeklyCountF: Future[Int] =  db.run(requestLog.uniqueRemoteAddressesAfter( pastWeek ).result )
        monthlyCountF.foreach{ monthlyCount => uniqueUsersGauge.foreach(_.labels("monthly").set(monthlyCount))}
        weeklyCountF.foreach{ weeklyCount => uniqueUsersGauge.foreach(_.labels("weekly").set(weeklyCount))}
      }
      
  }
}

object MetricsReporter{
  case class NewRequest(requestToken: Long, timestamp: Timestamp, user: UserInfo, requestType: String, attributes: String, pathCount: Int)
  case class ResponseUpdate( requestToken: Long, requestType: String, timestamp: Timestamp, pathCount: Int, duration: Long)
  case class NewSubscription( hasCallback: Boolean, typeStr: String)
  case class SetSubscriptionCount( hasCallback: Boolean, typeStr: String, count: Int)
  case class RemoveSubscription( hasCallback: Boolean, typeStr: String)
  case class CallbackSent( succeeded: Boolean, timeMs:Long, uri: Uri )
  case object Report
  def props(settings: OmiConfigExtension): Props ={
    Props( new MetricsReporter( "access-log", settings))
  }
}

//Access log db
//username | remoteAddress | timestamp | request? | attributes(newest, interval, ...) | leaf paths?
//
trait MonitoringDB{
  def configName: String
  val dc: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile](configName)
  val db = dc.db
  import dc.profile.api._
  val requestLog: RequestLog = new RequestLog()
  def initialize() = {
    val findTables = db.run(namesOfCurrentTables)
    findTables.flatMap {
      tableNames: Set[String] =>
        val queries= if (tableNames.contains("REQUEST_LOG")) {
          slick.dbio.DBIOAction.successful[Unit](())
        } else {
          requestLog.schema.create
        }
        db.run(queries)
    }
  }
  initialize()
  type DBSIOro[Result] = DBIOAction[Seq[Result], Streaming[Result], Effect.Read]
  type DBIOro[Result] = DBIOAction[Result, NoStream, Effect.Read]
  type DBIOwo[Result] = DBIOAction[Result, NoStream, Effect.Write]
  type DBIOsw[Result] = DBIOAction[Result, NoStream, Effect.Schema with Effect.Write]
  type ReadWrite = Effect with Effect.Write with Effect.Read with Effect.Transactional
  type DBIOrw[Result] = DBIOAction[Result, NoStream, ReadWrite]
  def namesOfCurrentTables: DBIOro[Set[String]] = MTable.getTables.map {
    mts =>
      mts.map {
        mt => mt.name.name
      }.toSet
  }
  case class RequestEvent( 
    requestToken: Long, 
    username: Option[Long], 
    remoteAddress: Long, 
    timestamp: Timestamp, 
    requestType: Short, 
    attributes: String, 
    requestLeafPathCount: Int,
    responseLeafPathCount: Option[Int],
    duration: Option[Long]
  )

  class RequestTable(tag: Tag) extends Table[RequestEvent](tag, "REQEUEST_LOG") {

    import dc.profile.api._

    def requestToken: Rep[Long] = column[Long]("REQUEST_TOKEN")
    def username: Rep[Option[Long]] = column[Option[Long]]("USERNAME")
    def remoteAddress: Rep[Long] = column[Long]("REMOTE")
    def timestamp: Rep[Timestamp] = column[Timestamp]("TIMESTAMP", O.SqlType("TIMESTAMP(3)"))
    def requestType: Rep[Short] = column[Short]("REQUESTTYPE")
    def attributes: Rep[String] = column[String]("ATTRIBUTES")
    def requestedPaths: Rep[Int] = column[Int]("REQUESTED_PATHS")
    def responsePaths: Rep[Option[Int]] = column[Option[Int]]("RESPONSED_PATHS")
    def duration: Rep[Option[Long]] = column[Option[Long]]("DURATION")
    def pk = primaryKey("pk",(requestToken,timestamp))
    def * : ProvenShape[RequestEvent] = (requestToken,username,remoteAddress,timestamp,requestType,attributes,requestedPaths,responsePaths,duration) <> (RequestEvent.tupled, RequestEvent.unapply)
  }
  class RequestLog() extends TableQuery[RequestTable]( new RequestTable(_)){

    def requestTypeStrTo(str: String): Short ={
      str match {
        case "write" => 1
        case "read" => 2
        case "subscription" => 3
        case "poll" => 4
        case "cancel" => 5
        case "response" => 6
        case "delete" => 7
        case "call" => 9
        case other => -1
      }
    }
    def add( requestToken: Long, timestamp: Timestamp, user: UserInfo, requestType: String, attributes: String, pathCount: Int) = {
      val dbio = this += RequestEvent(requestToken,user.name.map(_.hashCode.toLong),user.remoteAddress.flatMap(_.toIP).map( _.copy(port = None).hashCode.toLong).getOrElse(0L),timestamp,requestTypeStrTo(requestType),attributes,pathCount,None,None)
      dbio
    }
    def updateFromResponse( requestToken: Long,timestamp: Timestamp,  pathCount: Int, duration: Long) ={
      this.filter( row => row.requestToken === requestToken && row.timestamp === timestamp).map{ event => (event.responsePaths, event.duration)}.update((pathCount,duration))
    }
    def uniqueRemoteAddressesAfter( afterTimestamp: Timestamp) ={
      this.filter( _.timestamp >= afterTimestamp).map( r => r.remoteAddress ).distinct.length
    }
  }
}
