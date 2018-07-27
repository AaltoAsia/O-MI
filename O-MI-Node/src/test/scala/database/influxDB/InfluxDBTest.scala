package database
package influxDB


import java.util.Date
import java.sql.Timestamp
import akka.actor.{ActorSystem, ActorRef, Actor, Props}
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.util.Timeout
import akka.testkit._

import scala.concurrent.{Future}
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import http.{OmiConfig, OmiConfigExtension}
import com.typesafe.config.ConfigFactory

import testHelpers._
import types.odf._
import types.OmiTypes.OmiReturn
import types.Path

class InfluxDBTest( implicit ee: ExecutionEnv ) 
 extends Specification {
   
   def currentTimestamp = new Timestamp( new Date().getTime)
   class MockInfluxDB(
     val tester: ActorRef,
     override protected val config: InfluxDBConfigExtension
    )(
       override implicit val system: ActorSystem,
       override protected val singleStores: SingleStores
    ) extends InfluxDBImplementation( config)(system,singleStores) {
      override def sendQueries(queries: Seq[InfluxQuery]): Future[HttpResponse] = {
       implicit val timeout: Timeout = Timeout( 1 minutes )
        (tester ? queries).mapTo[HttpResponse]
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
      "should create new DB if configuret one not found" >> new NoisyActorstest(ActorSystem("InfluxTest", loggerConf.withFallback(ConfigFactory.load()))){
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores(settings)
        val probe = 
          system actorOf Props(new Actor {
            def receive = {
              case queries: Seq[InfluxQuery] => 
                if( queries.length == 1 ){
                  queries.headOption match{
                    case Some( ShowDBs ) => 
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"]]}]}]}""" )
                      )
                    case Some( CreateDB( name )) => 
                      if( name == conf.databaseName ) {
                        sender !  HttpResponse( 
                          StatusCodes.OK,
                          entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                        )
                    } else {
                      sender ! HttpResponse( 
                        StatusCodes.BadRequest,
                        entity = HttpEntity("""{"error":"test failure"}""" )
                      )
                    }
                  }
                }
            }
          }) 
        val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)

        val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
        val logFilters = Vector(
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.warning(s"Database ${conf.databaseName} not found from InfluxDB at address ${conf.address}",source, occurrences = 1),
          EventFilter.warning(s"Creating database ${conf.databaseName} to InfluxDB in address ${conf.address}",source, occurrences = 1), 
          EventFilter.info(s"Database ${conf.databaseName} created seccessfully to InfluxDB at address ${conf.address}",source, occurrences = 1)
        )


        filterEvents(logFilters){
          val influx = new MockInfluxDB(
            probe,
            conf
          )(system,singleStores)

        }
      }
      "should find existing DB and use it" >> new NoisyActorstest(ActorSystem("InfluxTest", loggerConf.withFallback(ConfigFactory.load()))){
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores(settings)
        val probe = 
          system actorOf Props(new Actor {
            def receive = {
              case queries: Seq[InfluxQuery] => 
                if( queries.length == 1 ){
                  queries.headOption match{
                    case Some( ShowDBs ) => 
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"],["${conf.databaseName}"]]}]}]}""" )
                      )
                    case Some( CreateDB( name )) => 
                      if( name == conf.databaseName ) {
                        sender !  HttpResponse( 
                          StatusCodes.OK,
                          entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                        )
                    } else {
                      sender ! HttpResponse( 
                        StatusCodes.BadRequest,
                        entity = HttpEntity("""{"error":"test failure"}""" )
                      )
                    }
                  }
                }
            }
          }) 
        val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)

        val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
        val logFilters = Vector(// TODO: Why sucsse when filter should fail
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.info(s"Database ${conf.databaseName} found from InfluxDB at address ${conf.address}",source, occurrences = 1)
        )


        filterEvents(logFilters){
          val influx = new MockInfluxDB(
            probe,
            conf
          )(system,singleStores)

        }
      }
     "should write in correct format" >> new NoisyActorstest(ActorSystem("InfluxTest", loggerConf.withFallback(ConfigFactory.load()))){
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores(settings)
        val probe = 
          system actorOf Props(new Actor {
            def receive = {
              case queries: Seq[InfluxQuery] => 
                if( queries.length == 1 ){
                  queries.headOption match{
                    case Some( ShowDBs ) => 
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"],["${conf.databaseName}"]]}]}]}""" )
                      )
                    case Some( CreateDB( name )) => 
                      if( name == conf.databaseName ) {
                        sender !  HttpResponse( 
                          StatusCodes.OK,
                          entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                        )
                    } else {
                      sender ! HttpResponse( 
                        StatusCodes.BadRequest,
                        entity = HttpEntity("""{"error":"test failure"}""" )
                      )
                    }
                  }
              }
              case meas: String =>
                sender() ! HttpResponse( 
                  StatusCodes.OK,
                  entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                )
            }
          }) 
        val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)

        val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
        val logFilters = Vector(// TODO: Why sucsse when filter should fail
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.info(s"Database ${conf.databaseName} found from InfluxDB at address ${conf.address}",source, occurrences = 1),
          EventFilter.debug(s"Successful write to InfluxDB",source, occurrences = 1)
        )


        filterEvents(logFilters){
          val influx = new MockInfluxDB(
            probe,
            conf
          )(system,singleStores)
          val ii = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
          influx.writeMany(ii)
         }.map(_.returnCode) must beEqualTo( "200 OK").await 
      }

     "should return 400 Bad Request status if write fails" >> new NoisyActorstest(ActorSystem("InfluxTest", loggerConf.withFallback(ConfigFactory.load()))){
        val settings: OmiConfigExtension = OmiConfig(system)
        val singleStores = new DummySingleStores(settings)
        val probe = 
          system actorOf Props(new Actor {
            def receive = {
              case queries: Seq[InfluxQuery] => 
                if( queries.length == 1 ){
                  queries.headOption match{
                    case Some( ShowDBs ) => 
                      sender() ! HttpResponse( 
                        StatusCodes.OK,
                        entity = HttpEntity(
                          s"""{"results":[{"statement_id":0,"series":[{"name":"databases","columns":["name"],"values":[["_internal"],["${conf.databaseName}"]]}]}]}""" 
                        )
                      )
                    case Some( CreateDB( name )) => 
                      if( name == conf.databaseName ) {
                        sender !  HttpResponse( 
                          StatusCodes.OK,
                          entity = HttpEntity("""{"results":[{"statement_id":0}]}""" )
                        )
                    } else {
                      sender ! HttpResponse( 
                        StatusCodes.BadRequest,
                        entity = HttpEntity("""{"error":"test failure"}""" )
                      )
                    }
                  }
              }
              case meas: String =>
                sender() ! HttpResponse( 
                  StatusCodes.BadRequest,
                  entity = HttpEntity("""{"error":"test failure"}""" )
                )
            }
          }) 
        val conf: InfluxDBConfigExtension = new InfluxDBConfigExtension(system.settings.config)

        val source = s"InfluxClient:${conf.address}:${conf.databaseName}"
        val str = """{"error":"test failure"}"""
        val logFilters = Vector(// TODO: Why sucsse when filter should fail
          EventFilter.debug(start = s"Found following databases: ",source = source, occurrences = 1),
          EventFilter.info(s"Database ${conf.databaseName} found from InfluxDB at address ${conf.address}",source, occurrences = 1),
          EventFilter.warning(s"Write returned 400 Bad Request with:\n $str",source, occurrences = 1)
        )


        filterEvents(logFilters){
          val influx = new MockInfluxDB(
            probe,
            conf
          )(system,singleStores)
          val ii = Vector( InfoItem( Path("Objects","Obj","II"), Vector( IntValue(13,currentTimestamp))))
          influx.writeMany(ii)
         }.map(_.returnCode) must beEqualTo( "400 Bad Request").await 
     }
     "should read" >> {
       "in correct format" >> inTodo
       "unmarshal results correctly" >> inTodo
       "prevent oldest queries" >> inTodo
       "fail if sending throws something" >> inTodo
       "return correct O-MI Return if Server responses with failure" >> inTodo
     }
     "remove" >>{
       "should be in correct format" >> inTodo
       "should remove data from cache too" >> inTodo
       "throw exception if server response with failure" >> inTodo
     }
   }
}
