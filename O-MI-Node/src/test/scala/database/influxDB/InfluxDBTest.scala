package database.influxDB

import java.util.Date
import java.sql.Timestamp
import akka.actor.{ActorSystem, ActorRef, Actor, Props}
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit._

import scala.concurrent.{Future}
import scala.util.control.NonFatal
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import http.{OmiConfig }
import com.typesafe.config.ConfigFactory

import database.SingleStores
import database.journal.Models.{ErasePathCommand, GetTree, MultipleReadCommand}
import testHelpers._
import types.odf._
import types.Path

class InfluxDBTest( implicit ee: ExecutionEnv ) 
 extends Specification {
   
   def currentTimestamp = new Timestamp( new Date().getTime)
   def okResult = HttpResponse( 
                          StatusCodes.OK,
                          entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                        )
   def foundDBResult(db: String ) = HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"],["$db"]]}]}]}""" )
                      )
   def badRequestResult( error: String ) = HttpResponse( 
                          StatusCodes.BadRequest,
                          entity = HttpEntity(s"""{"error":"$error"}""" )
                        )

   private case class QueryPackage( queries: Vector[InfluxQuery])
   class MockInfluxDB(
     val tester: ActorRef,
     override protected val config: InfluxDBConfigExtension
    )(
       override implicit val system: ActorSystem,
       override protected val singleStores: SingleStores
    ) extends InfluxDBImplementation( config)(system,singleStores) {
      override def sendQueries(queries: Seq[InfluxQuery]): Future[HttpResponse] = {
       implicit val timeout: Timeout = Timeout( 1 minutes )
        (tester ? QueryPackage(queries.toVector)).mapTo[HttpResponse]
      }
      override def sendMeasurements(measurements: String ): Future[HttpResponse] = {
       implicit val timeout: Timeout = Timeout( 1 minutes )
        (tester ? measurements).mapTo[HttpResponse]
      }
    }
    def inTodo = { 1 === 2}.pendingUntilFixed
    "InfluxDB " >> {

       implicit val timeout: Timeout = Timeout( 1 minutes )
        val loggerConf = ConfigFactory.parseString(
          """
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
        }
        """ ) 
       def testInit(f: ActorSystem => _) = {
         new NoisyActorstest(ActorSystem("InfluxTest", loggerConf.withFallback(ConfigFactory.load()))){ f(system) }
       }
       "should create new DB if configuret one not found" >> testInit{ implicit system: ActorSystem =>
        val singleStores = new DummySingleStores(OmiConfig(system))
        val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
        val probe = 
          system actorOf Props(new Actor {
            def receive = {
              case QueryPackage( queries: Vector[InfluxQuery]) => 
                if( queries.length == 1 ){
                  queries.headOption match{
                    case Some( ShowDBs ) => 
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"]]}]}]}""" )
                      )
                    case Some( CreateDB( name )) => 
                      if( name == conf.databaseName ) {
                        sender() ! okResult
                      } else {
                        sender() ! badRequestResult( "test failure" )
                      }
                    case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                    case None => sender() ! badRequestResult( "wrong query, test failure" )
                  }
                }
            }
          }) 

        val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
        val logFilters = Vector(
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.warning(s"Database ${conf.databaseName} not found from InfluxDB at address ${conf.address}",source, occurrences = 1),
          EventFilter.warning(s"Creating database ${conf.databaseName} to InfluxDB in address ${conf.address}",source, occurrences = 1), 
          EventFilter.info(s"Database ${conf.databaseName} created seccessfully to InfluxDB at address ${conf.address}",source, occurrences = 1)
        )


        filterEvents(logFilters){
          new MockInfluxDB(
            probe,
            conf
          )(system,singleStores)

        }
      }
      "should find existing DB and use it" >> testInit{ implicit system: ActorSystem =>
        val singleStores = new DummySingleStores(OmiConfig(system))
        val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
        val probe = 
          system actorOf Props(new Actor {
            def receive = {
              case QueryPackage( queries: Vector[InfluxQuery] ) => 
                if( queries.length == 1 ){
                  queries.headOption match{
                    case Some( ShowDBs ) => 
                      sender() ! foundDBResult(conf.databaseName)
                    case Some( CreateDB( name )) => 
                      if( name == conf.databaseName ) {
                        sender() ! okResult 
                    } else {
                      sender() ! badRequestResult( "test failure" )
                    }
                    case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                    case None => sender() ! badRequestResult( "wrong query, test failure" )
                  }
                }
            }
          }) 

        filterEvents(
          initializationLogFilters( conf ) 
        ){
          new MockInfluxDB(
            probe,
            conf
          )(system,singleStores)

        }
      }
    "writeMany should" >> {
       "send measurements in correct format" >> testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system))
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val probe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  }
                case meas: String =>
                  sender() ! okResult
              }
            }) 

          val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
          filterEvents(
            initializationLogFilters( conf ) ++ Vector(
            EventFilter.debug(s"Successful write to InfluxDB",source, occurrences = 1)
            )
          ){
            val influx = new MockInfluxDB(
              probe,
              conf
            )(system,singleStores)
            val ii = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
            influx.writeMany(ii)
           }.map(_.returnCode) must beEqualTo( "200 OK").await 
        }

        "return 400 Bad Request status if write fails" >> testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system))
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val probe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  }
                      case meas: String =>
                        sender() ! badRequestResult( "test failure" )
              }
            }) 

          val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
          val str = """{"error":"test failure"}"""
          filterEvents(
            initializationLogFilters( conf ) ++ Vector(
              EventFilter.warning(s"Write returned 400 Bad Request with:\n $str",source, occurrences = 1)
            )
          ){
              val influx = new MockInfluxDB(
                probe,
                conf
              )(system,singleStores)
              val ii = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
              influx.writeMany(ii)
            }.map(_.returnCode) must beEqualTo( "400 Bad Request").await 
          }
      }
     "getNBetween should" >>{
       "prevent request with Oldest parameter" >>  testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system))
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val probe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              probe,
              conf
            )(system,singleStores)

            val n = 53
            val ii = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
            influx.getNBetween(ii,None,None,None,Some(n))
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
          val latestProbe = system actorOf Props(new Actor {
              def receive = {
                case MultipleReadCommand(paths) => 
                  val tuples = paths.map{ path =>
                    (path, IntValue(13,timestamp))
                  }
                  sender() ! tuples
              }
            }) 
          val singleStores = new DummySingleStores(OmiConfig(system),
            latestStore = latestProbe,
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    queries.collect{
                      case  select: SelectValue => select 
                    }.headOption match {
                      case Some( _ ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case None => ???
                    }
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,None,None,None)
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
       "send correct query with only begin parameter" >>  testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.exists{
                          clause => 
                            clause.expressions.size == 1 &&
                            clause.expressions.contains( LowerTimeBoundExpression(timestamp))
                      }  && limitClause.isEmpty && orderByClause.contains(DescTimeOrderByClause())} => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else sender() ! badRequestResult( "Incorrect query" )
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),None,None,None)
            } must beEqualTo(
              Some(ImmutableODF(Vector(
                Object( Path("Objects","Obj")).copy( descriptions= Set(Description("test"))),
              InfoItem( Path("Objects","Obj","II1"), Vector( ShortValue(13,timestamp))).copy( descriptions=Set(Description("test"))),
              InfoItem( Path("Objects","Obj","II2"), Vector( ShortValue(13,timestamp)))
              )))
            ).await
       }
       "send correct query with only end parameter" >> testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.exists{
                          clause => 
                            clause.expressions.size == 1 &&
                            clause.expressions.contains( UpperTimeBoundExpression(timestamp))
                      } && limitClause.isEmpty  && orderByClause.contains(DescTimeOrderByClause())
                      } => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13.7)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else sender() ! badRequestResult( "Incorrect query" )
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,Some(timestamp),None,None)
            } must beEqualTo(
              correctFloatResults
            ).await
       }
       "send correct query with only newest parameter" >> testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val n = 30
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.isEmpty &&
                        limitClause.exists( clause => clause.n == n ) && 
                       orderByClause.contains(DescTimeOrderByClause())
                      } => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13.7)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else {
                      sender() ! badRequestResult( s"Incorrect query: ${queries.map(_.toString).mkString(",")}" )
                    }
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,None,Some(n),None)
            } must beEqualTo(
              correctFloatResults
            ).await
       }
       "send correct query with begin and end parameters" >> testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.exists{
                          clause => 
                            clause.expressions.size == 2 &&
                            clause.expressions.contains( UpperTimeBoundExpression(timestamp))
                            clause.expressions.contains( LowerTimeBoundExpression(timestamp))
                      } && limitClause.isEmpty  && orderByClause.contains(DescTimeOrderByClause())
                      } => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13.7)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else sender() ! badRequestResult( "Incorrect query" )
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),Some(timestamp),None,None)
            } must beEqualTo(
              correctFloatResults
            ).await
       }
       "send correct query with begin and newest parameters" >>testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val n = 30
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.exists{
                          clause => 
                            clause.expressions.size == 1 &&
                            clause.expressions.contains( LowerTimeBoundExpression(timestamp))
                        } &&
                        limitClause.exists( clause => clause.n == n ) && 
                       orderByClause.contains(DescTimeOrderByClause())
                      } => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13.7)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else {
                      sender() ! badRequestResult( s"Incorrect query: ${queries.map(_.toString).mkString(",")}" )
                    }
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),None,Some(n),None)
            } must beEqualTo(
              correctFloatResults
            ).await
       }
       "send correct query with end and newest parameters" >>testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val n = 30
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.exists{
                          clause => 
                            clause.expressions.size == 1 &&
                            clause.expressions.contains( UpperTimeBoundExpression(timestamp))
                        } &&
                        limitClause.exists( clause => clause.n == n ) && 
                       orderByClause.contains(DescTimeOrderByClause())
                      } => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13.7)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else {
                      sender() ! badRequestResult( s"Incorrect query: ${queries.map(_.toString).mkString(",")}" )
                    }
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,None,Some(timestamp),Some(n),None)
            } must beEqualTo(
              correctFloatResults
            ).await
       }
       "send correct query with begin, end and newest parameters" >> testInit{ implicit system: ActorSystem =>
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = DummyHierarchyStore( odf )
          )
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
          val n = 53
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    val results = queries.collect{
                      case  select @ SelectValue(
                        measurement: String,
                        whereClause: Option[WhereClause],
                        orderByClause: Option[OrderByClause],
                        limitClause: Option[LimitClause]
                      ) if { 
                        whereClause.exists{
                          clause => 
                            clause.expressions.size == 2 &&
                            clause.expressions.contains( UpperTimeBoundExpression(timestamp))
                            clause.expressions.contains( LowerTimeBoundExpression(timestamp))
                      } && limitClause.exists( clause => clause.n == n ) && orderByClause.contains(DescTimeOrderByClause())
                      } => select 
                    }.zipWithIndex.map{
                      case (select:SelectValue, index: Int) =>
                        jsonFormat( select.measurement, index, timestamp, 13.7)
                    }
                    if( results.nonEmpty){
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[${results.mkString(",\n")}]}""" )
                      )
                    } else sender() ! badRequestResult( "Incorrect query" )
                  }
                case meas: String =>
                  sender() ! badRequestResult( "test failure" )
              }
            }) 

          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            val leaf = Vector( Object( Path("Objects","Obj")))
            influx.getNBetween(leaf,Some(timestamp),Some(timestamp),Some(n),None)
            } must beEqualTo(correctFloatResults).await
       }
     }
     "Should send correct query for remove and remove correct data cache" >> testInit{ implicit system: ActorSystem =>
          val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)
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
          val singleStores = new DummySingleStores(OmiConfig(system),
            hierarchyStore = hierarchyProbe
          )
          val serverprobe = 
            system actorOf Props(new Actor {
              def receive = {
                case QueryPackage( queries: Vector[InfluxQuery] ) => 
                  if( queries.length == 1 ){
                    queries.headOption match{
                      case Some( ShowDBs ) => 
                        sender() ! foundDBResult(conf.databaseName)
                      case Some( CreateDB( name )) => 
                        if( name == conf.databaseName ) {
                          sender() ! okResult
                        } else {
                          sender() ! badRequestResult( "test failure" )
                        }
                      case Some( DropMeasurement(name)) =>
                        sender() ! okResult
                      case Some( select: SelectValue ) =>
                        sender() ! badRequestResult( "No select queries needed!" )
                      case Some( query ) => sender() ! badRequestResult( "wrong query, test failure" )
                      case None => sender() ! badRequestResult( "wrong query, test failure" )
                    }
                  } else {
                    if( queries.collect{
                      case DropMeasurement(name) => name 
                    }.nonEmpty ) {
                        sender() ! okResult
                    } else{ 
                        sender() ! badRequestResult( "No select queries needed!" )
                    }

                  }
              }
            }) 



          filterEvents(
            initializationLogFilters( conf )
          ){
            val influx = new MockInfluxDB(
              serverprobe,
              conf
            )(system,singleStores)

            influx.remove(Path("Objects","Obj"))
          } must beEqualTo(Vector(1,1)).await
       }
     }
     def initializationLogFilters( conf: InfluxDBConfigExtension) ={
       val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
       Vector(
         EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
         EventFilter.info(s"Database ${conf.databaseName} found from InfluxDB at address ${conf.address}",source, occurrences = 1)
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
