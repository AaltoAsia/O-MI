package database.influxDB

import java.util.Date
import java.sql.Timestamp

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.model._
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.client.RequestBuilding
import akka.util.Timeout
import akka.testkit._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.mock.Mockito
import com.typesafe.config.ConfigFactory
import http.{OmiConfig, OmiConfigExtension}
import database.SingleStores
import database.journal.HierarchyStore.GetTree
import database.journal.LatestStore.{ErasePathCommand, MultipleReadCommand}
import testHelpers._
import types.odf._
import types.Path

class InfluxDBTest( implicit ee: ExecutionEnv ) 
 extends Specification with Mockito {
   
   def currentTimestamp: Timestamp = new Timestamp( new Date().getTime)
   def okResult: HttpResponse = HttpResponse( 
                          StatusCodes.OK,
                          entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                        )
   def foundDBResult(db: String ): HttpResponse = HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"],["$db"]]}]}]}""" )
                      )
  def badRequestResult( error: String ): HttpResponse = HttpResponse( 
                          StatusCodes.BadRequest,
                          entity = HttpEntity(s"""{"error":"$error"}""" )
                        )
  def source(config: InfluxDBConfigExtension): String = s"InfluxClient:${config.address}:${config.databaseName}"

  sequential
   class MockitoInfluxDB(
      final override val httpExt: HttpExt,
      override val settings: OmiConfigExtension
    )(
       override implicit val system: ActorSystem,
       override protected val singleStores: SingleStores
    ) extends InfluxDBImplementation( settings)(system,singleStores) {
      require( settings.influx.nonEmpty)
    }
    "InfluxDB " >> {

      def dbFoundTest(httpExtMock: HttpExt, config: InfluxDBConfigExtension) = {
        val showDBsRequest = InfluxDBClient.queriesToHTTPPost(Vector(ShowDBs),config.queryAddress)
        httpExtMock.singleRequest( showDBsRequest) returns Future.successful(foundDBResult(config.databaseName))
      }
       implicit val timeout: Timeout = Timeout( 1 minutes )
        val loggerConf = ConfigFactory.parseString(
          """
        omi-service.database = "influxdb"
        influxDB-config {
          database-name = "testingdb"
          address = "http://localhost:8086/"
          #user = <user name>
          #password = <user's password>
        }
        akka {
          stdout-loglevel = OFF
          loglevel = DEBUG
          loggers = ["testHelpers.SilentTestEventListener"]
          #loggers = ["akka.testkit.TestEventListener"]
        }
        """ ) 
       def testInit(f: ActorSystem => _) = {
         new NoisyActorstest(ActorSystem("InfluxTest", loggerConf.withFallback(ConfigFactory.load()))){ f(system) }
       }
       "should create new DB if configuret one not found" >> testInit{ implicit system: ActorSystem =>
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores()
        val config = settings.influx.get
        val source = s"InfluxClient:${config.address}:${config.databaseName}"
        val logFilters = Vector(
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.warning(s"Database ${config.databaseName} not found from InfluxDB at address ${config.address}",source, occurrences = 1),
          EventFilter.warning(s"Creating database ${config.databaseName} to InfluxDB in address ${config.address}",source, occurrences = 1), 
          EventFilter.info(s"Database ${config.databaseName} created seccessfully to InfluxDB at address ${config.address}",source, occurrences = 1)
        )


        filterEvents(logFilters){
          val httpExtMock = mock[HttpExt]
          val showDBsRequest = InfluxDBClient.queriesToHTTPPost(Vector(ShowDBs),config.queryAddress)
          val showDBsResponse =  HttpResponse( 
            StatusCodes.OK,
            entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"]]}]}]}""" )
          )
          val createDBRequest = InfluxDBClient.queriesToHTTPPost(Vector(CreateDB(config.databaseName)),config.queryAddress)
          httpExtMock.singleRequest( showDBsRequest) returns Future.successful(showDBsResponse)
          httpExtMock.singleRequest( createDBRequest) returns Future.successful(okResult)

          new MockitoInfluxDB(
            httpExtMock,
            settings
          )(system,singleStores)
        }
      }
       "should handle correctly DB creation failure" >> testInit{ implicit system: ActorSystem =>
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores()
        val config = settings.influx.get
        val source = s"InfluxClient:${config.address}:${config.databaseName}"
        val logFilters = Vector(
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.warning(s"Database ${config.databaseName} not found from InfluxDB at address ${config.address}",source, occurrences = 1),
          EventFilter.warning(s"Creating database ${config.databaseName} to InfluxDB in address ${config.address}",source, occurrences = 1), 
          EventFilter.error(s"Database ${config.databaseName} could not be created to InfluxDB at address ${config.address}"),
          EventFilter.warning(s"""InfluxQuery returned 400 Bad Request with:\n {"error":"test failure"}""",source, occurrences = 1) 
        )


        filterEvents(logFilters){
          val httpExtMock = mock[HttpExt]
          val showDBsRequest = InfluxDBClient.queriesToHTTPPost(Vector(ShowDBs),config.queryAddress)
          val showDBsResponse =  HttpResponse( 
            StatusCodes.OK,
            entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"]]}]}]}""" )
          )
          val createDBRequest = InfluxDBClient.queriesToHTTPPost(Vector(CreateDB(config.databaseName)),config.queryAddress)
          httpExtMock.singleRequest( showDBsRequest) returns Future.successful(showDBsResponse)
          httpExtMock.singleRequest( createDBRequest) returns Future.successful(badRequestResult("test failure"))

          new MockitoInfluxDB(
            httpExtMock,
            settings
          )(system,singleStores)
        }
      }
      "should find existing DB and use it" >> testInit{ implicit system: ActorSystem =>
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores()
        val config = settings.influx.get

        filterEvents(
          initializationLogFilters( config ) 
        ){
          val httpExtMock = mock[HttpExt]
          dbFoundTest(httpExtMock, config )

          new MockitoInfluxDB(
            httpExtMock,
            settings
          )(system,singleStores)

        }
      }
    "writeMany should" >> {
       "send measurements in correct format" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores()
          val config = settings.influx.get

          val source = s"InfluxClient:${config.address}:${config.databaseName}"
          filterEvents(
            initializationLogFilters( config ) ++ Vector(
            EventFilter.debug(s"Successful write to InfluxDB",source, occurrences = 1)
            )
          ){
            val iis = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
            val httpExtMock = mock[HttpExt]
            val measurements = iis.flatMap{ ii => InfluxDBImplementation.infoItemToWriteFormat(ii).map(_.formatStr) }.mkString("\n")
            val request = RequestBuilding.Post(config.writeAddress, measurements)
            dbFoundTest(httpExtMock, config )
            httpExtMock.singleRequest( request) returns Future.successful(okResult)

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)
            influx.writeMany(iis)
           }.map(_.returnCode) must beEqualTo( "200 OK").await 
        }

        "return 400 Bad Request status if write fails" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores()
          val config = settings.influx.get

          val source = s"InfluxClient:${config.address}:${config.databaseName}"
          val str = """{"error":"test failure"}"""
          filterEvents(
            initializationLogFilters( config ) ++ Vector(
              EventFilter.warning(s"Write returned 400 Bad Request with:\n $str",source, occurrences = 1)
            )
          ){
            val iis = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
            val httpExtMock = mock[HttpExt]
            val measurements = iis.flatMap{ ii => InfluxDBImplementation.infoItemToWriteFormat(ii).map(_.formatStr) }.mkString("\n")
            val request = RequestBuilding.Post(config.writeAddress, measurements)
            dbFoundTest(httpExtMock, config )
            httpExtMock.singleRequest( request) returns Future.successful(badRequestResult("test failure"))

              val influx = new MockitoInfluxDB(
                httpExtMock,
                settings
              )(system,singleStores)
              influx.writeMany(iis)
            }.map(_.returnCode) must beEqualTo( "400 Bad Request").await 
          }
        "log any exception during write" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores()
          val config = settings.influx.get

          val source = s"InfluxClient:${config.address}:${config.databaseName}"
          val exp = new Exception("test failure")
          filterEvents(
            initializationLogFilters( config ) ++ Vector(
              EventFilter.error(s"Failed to communicate to InfluxDB: $exp",source, occurrences = 1)
            )
          ){
            val iis = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
            val httpExtMock = mock[HttpExt]
            val measurements = iis.flatMap{ ii => InfluxDBImplementation.infoItemToWriteFormat(ii).map(_.formatStr) }.mkString("\n")
            val request = RequestBuilding.Post(config.writeAddress, measurements)
            dbFoundTest(httpExtMock, config )
            httpExtMock.singleRequest( request) returns Future.failed(exp)

              val influx = new MockitoInfluxDB(
                httpExtMock,
                settings
              )(system,singleStores)
              influx.writeMany(iis)
            }.recover{
              case NonFatal(e) => e.getMessage() 
            }  must beEqualTo("test failure").await 
          }
      }
     "getNBetween should" >>{
       "prevent request with Oldest parameter" >>  testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores()
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val n = 53
            val iis = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)
            influx.getNBetween(iis,None,None,None,Some(n),None)
          }.recover{ case NonFatal(t) => t.getMessage()} must beEqualTo("Oldest attribute is not allowed with InfluxDB.").await 
       }
       val timestamp = currentTimestamp
       val nodes = Vector(
         Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
         InfoItem( "II1", Path("Objects","Obj","II1"), descriptions = Set(Description("test"))),
         InfoItem( "II2", Path("Objects","Obj","II2")),
         InfoItem( Path("Objects","Obj2","II"), Vector.empty)
       )
       val odf = ImmutableODF( nodes ).valuesRemoved
       "send correct query without additional parameters" >>  testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val latestProbe = system actorOf Props(new Actor {
              def receive = {
                case MultipleReadCommand(paths) => 
                  val tuples = paths.map{ path =>
                    (path, IntValue(13,timestamp))
                  }
                  sender() ! tuples
              }
            }) 
          val singleStores = new DummySingleStores(
            latestStore = latestProbe,
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,None,None,None,None)
          } must beEqualTo(
              Some(ImmutableODF(Vector(
                Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
              InfoItem( Path("Objects","Obj","II1"), Vector( IntValue(13,timestamp))).copy( descriptions=Set(Description("test"))),
              InfoItem( Path("Objects","Obj","II2"), Vector( IntValue(13,timestamp)))
              )))
            ).await
       }
       val correctFloatResults = Some(ImmutableODF(Vector(
                Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
              InfoItem( Path("Objects","Obj","II1"), Vector( FloatValue(13.7f,timestamp))).copy( descriptions=Set(Description("test"))),
              InfoItem( Path("Objects","Obj","II2"), Vector( FloatValue(13.7f,timestamp)))
              )))
       def response(results: Seq[String]):HttpResponse ={
         HttpResponse( 
           StatusCodes.OK,
           entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
         )
       }
       "send correct query with only begin parameter" >>  testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), Some( timestamp ),None,None
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),None,None,None,None)
          } must beEqualTo(
            Some(ImmutableODF(Vector(
              Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
            InfoItem( Path("Objects","Obj","II1"), Vector( ShortValue(13,timestamp))).copy( descriptions=Set(Description("test"))),
            InfoItem( Path("Objects","Obj","II2"), Vector( ShortValue(13,timestamp)))
            )))
          ).await
       }
       "send correct query with only end parameter" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), None, Some( timestamp ), None
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13.7)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,Some(timestamp),None,None,None)
            } must beEqualTo(
              correctFloatResults
            ).await
       }
       "send correct query with only newest parameter" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val n = 30
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), None, None, Some(n)
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13.7)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,None,Some(n),None,None)
          } must beEqualTo(
            correctFloatResults
          ).await
       }
       "send correct query with begin and end parameters" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), Some(timestamp), Some(timestamp), None, 
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13.7)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),Some(timestamp),None,None,None)
          } must beEqualTo(
            correctFloatResults
          ).await
       }
       "send correct query with begin and newest parameters" >>testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val n = 30
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), Some(timestamp), None, Some(n), 
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13.7)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),None,Some(n),None,None)
          } must beEqualTo(
            correctFloatResults
          ).await
       }
       "send correct query with end and newest parameters" >>testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val n = 30
          val config = settings.influx.get

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), None, Some(timestamp), Some(n), 
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13.7)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,Some(timestamp),Some(n),None,None)
          } must beEqualTo(
            correctFloatResults
          ).await
       }
       "send correct query with begin, end and newest parameters" >> testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val config = settings.influx.get
          val n = 53

          filterEvents(
            initializationLogFilters( config )
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ),  Some(timestamp), Some(timestamp), Some(n), 
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            val results = queries.collect{
              case select: SelectValue => select
            }.zipWithIndex.map{
              case (select:SelectValue, index: Int) =>
                jsonFormat( select.measurement, index, timestamp, 13.7)
            }
            httpExtMock.singleRequest( request) returns Future.successful(response(results))

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),Some(timestamp),Some(n),None,None)
          } must beEqualTo(correctFloatResults).await
       }
       "log any execption during execution" >>  testInit{ implicit system: ActorSystem =>
          val settings: OmiConfigExtension = OmiConfig(system)
          val singleStores = new DummySingleStores(
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val config = settings.influx.get

          val exp = new Exception("test failure")
          val source = s"InfluxClient:${config.address}:${config.databaseName}"
          filterEvents(
            initializationLogFilters( config ) ++ 
            Vector(EventFilter.error(s"Failed to communicate to InfluxDB: $exp",source, occurrences = 1))
          ){
            val httpExtMock = mock[HttpExt]
            dbFoundTest(httpExtMock, config )
            val queries = InfluxDBImplementation.createNBetweenInfoItemsQueries(
                Vector( 
                  InfoItem( Path("Objects","Obj","II2"), Vector.empty),
                  InfoItem( Path("Objects","Obj","II1"), Vector.empty)
                ), Some( timestamp ),None,None
              )
            val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
            httpExtMock.singleRequest( request) returns Future.failed(exp)

            val influx = new MockitoInfluxDB(
              httpExtMock,
              settings
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),None,None,None,None)
          }.recover{
              case NonFatal(e) => e.getMessage() 
            }  must beEqualTo("test failure").await 
       }
     }
     "remove" >> {
       "should send correct query and remove correct data cache" >> testInit{ implicit system: ActorSystem =>
         val settings: OmiConfigExtension = OmiConfig(system)
         val config = settings.influx.get
         val nodes = Vector(
           Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
           InfoItem( "II1", Path("Objects","Obj","II1"), descriptions = Set(Description("test"))),
           InfoItem( "II2", Path("Objects","Obj","II2")),
           InfoItem( Path("Objects","Obj2","II"), Vector.empty)
         )
         val odf = ImmutableODF( nodes ).valuesRemoved
         val hierarchyProbe = system actorOf Props(new Actor {
           def receive = {
             case GetTree => sender() ! odf
             case ErasePathCommand(path) => sender() ! {Unit}
           }
         }
         )
         val singleStores = new DummySingleStores(
           hierarchyStore = hierarchyProbe
           )
         filterEvents(
           initializationLogFilters( config )
         ){
           val httpExtMock = mock[HttpExt]
           dbFoundTest(httpExtMock, config )
           val queries = Vector( 
             InfoItem( Path("Objects","Obj","II2"), Vector.empty),
             InfoItem( Path("Objects","Obj","II1"), Vector.empty)
           ).map{
             ii => 
               val mName = InfluxDBImplementation.pathToMeasurementName(ii.path)
               DropMeasurement(mName)
           }
           val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
           httpExtMock.singleRequest( request) returns Future.successful(okResult)

           val influx = new MockitoInfluxDB(
             httpExtMock,
             settings
           )(system,singleStores)

           influx.remove(Path("Objects","Obj"))
         } must beEqualTo(Seq(200)).await
       }
       "should handle failure correctly" >> testInit{ implicit system: ActorSystem =>
         val settings: OmiConfigExtension = OmiConfig(system)
         val config = settings.influx.get
         val nodes = Vector(
           Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
           InfoItem( "II1", Path("Objects","Obj","II1"), descriptions = Set(Description("test"))),
           InfoItem( "II2", Path("Objects","Obj","II2")),
           InfoItem( Path("Objects","Obj2","II"), Vector.empty)
         )
         val odf = ImmutableODF( nodes ).valuesRemoved
         val hierarchyProbe = system actorOf Props(new Actor {
           def receive = {
             case GetTree => sender() ! odf
             case ErasePathCommand(path) => sender() ! {Unit}
           }
         }
         )
         val singleStores = new DummySingleStores(
           hierarchyStore = hierarchyProbe
           )
         filterEvents(
           initializationLogFilters( config )
         ){
           val httpExtMock = mock[HttpExt]
           dbFoundTest(httpExtMock, config )
           val queries = Vector( 
             InfoItem( Path("Objects","Obj","II2"), Vector.empty),
             InfoItem( Path("Objects","Obj","II1"), Vector.empty)
           ).map{
             ii => 
               val mName = InfluxDBImplementation.pathToMeasurementName(ii.path)
               DropMeasurement(mName)
           }
           val request = InfluxDBClient.queriesToHTTPPost(queries,config.queryAddress)
           httpExtMock.singleRequest( request) returns Future.successful(badRequestResult("test failure"))

           val influx = new MockitoInfluxDB(
             httpExtMock,
             settings
           )(system,singleStores)

           influx.remove(Path("Objects","Obj"))
          }.recover{
              case NonFatal(e) => e.getMessage() 
            }  must beEqualTo("""{"error":"test failure"}""").await 
       }
     }
    }
     def initializationLogFilters( config: InfluxDBConfigExtension) ={
       val source = s"InfluxClient:${config.address}:${config.databaseName}"
       Vector(
         EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
         EventFilter.info(s"Database ${config.databaseName} found from InfluxDB at address ${config.address}",source, occurrences = 1)
       )
     }
       def jsonFormat( measurement: String, index: Int, timestamp: Timestamp, value: Any) ={
         s"""{
           "statement_id": $index,
           "series": [
           {
             "name": "${measurement}",
             "columns": [
             "time",
             "value"
             ],
             "values": [
             [
             "$timestamp",
             $value
             ]
             ]
           }
           ]
         }"""
       }
}
