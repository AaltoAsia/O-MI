package bench
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import java.sql.Timestamp
import java.util.Date
import scala.util.{Random, Try, Success, Failure}
import org.scalameter.api._
import akka.actor.ActorSystem
import http._
import database._
import types._
import types.OdfTypes._
import org.scalameter.picklers.Pickler
import org.scalameter.{Setup,Key}
import org.scalameter.picklers.Implicits._
import com.typesafe.config.{ConfigFactory, Config}

trait UnCachedDBTest extends DBTest {
  val name: String = "UncachedDBTest"
  implicit val system = ActorSystem(name + "System")
  implicit val settings = OmiConfig(system)
  implicit val singleStores = new SingleStores(settings)
  val db: DBReadWrite = new UncachedTestDB(name,false)(system, singleStores, settings)
  test(db, "UncachedDB")
}
trait OldAndNewDBTest extends DBTest {
  val psqlconfig = ConfigFactory.load(
      ConfigFactory.parseString(
        """
dbconf {
  driver = "slick.driver.PostgresDriver$"
  db {
    url = "jdbc:postgresql:omiNodeTest"
    user = "omi"
    password = "testingominode"
    driver = org.postgresql.Driver
    connectionPool = disabled
    keepAliveConnection = true
    connectionTimeout = 15s
  }
}

        """).withFallback(ConfigFactory.load()))

  val h2config = ConfigFactory.load(
      ConfigFactory.parseString(
        """
dbconf {
  driver = "slick.driver.H2Driver$"
  db {
    url = "jdbc:h2:mem:test1"
    driver = org.h2.Driver
    connectionPool = disabled
    keepAliveConnection = true
    connectionTimeout = 15s
  }
}
        """).withFallback(ConfigFactory.load()))
  val name: String = "OldAndNewDBTest"
   val oldsystem = ActorSystem(name + "OldSystem")
  val oldsettings = OmiConfig(oldsystem)
  val oldsingleStores = new SingleStores(oldsettings)
  val olddb: DBReadWrite = new UncachedTestDB("OldDB", false,psqlconfig)(oldsystem, oldsingleStores, h2settings)
  test(olddb, "OldDBFILE")(oldsystem)
  val newsystem = ActorSystem(name + "NewSystem")
  val newsettings = OmiConfig(newsystem)
  val newsingleStores = new SingleStores(newsettings)
  val newdb: DBReadWrite = new TestDB("NewDB", false,psqlconfig)(newsystem, newsingleStores, h2settings)
  test(newdb, "NewDBFILE")(newsystem)
}

trait DBTest extends Bench[Double]{

  //  implicit val settings = OmiConfig(system)
  //  implicit val singleStores = new SingleStores(settings)
 // = new UncachedTestDB(name,false)(system, singleStores, settings)

  lazy val measurer = new Measurer.Default
  lazy val executor = LocalExecutor(
    new Executor.Warmer.Default,
    Aggregator.min[Double],
    measurer)
  override def persistor = new JSONSerializationPersistor( "benchResults")
  override def reporter: Reporter[Double] = Reporter.Composite(
      new RegressionReporter(
            RegressionReporter.Tester.OverlapIntervals(),
                RegressionReporter.Historian.ExponentialBackoff() ),
                HtmlReporter(true)
              )
  lazy val random = Random
  lazy val infoitemsCounts = Gen.range("InfoItems")(20, 120, 20)// ++ Gen.range("values")(1, 10, 1)
  lazy val valueCounts = Gen.range("Values")(20, 120, 20)// ++ Gen.range("values")(1, 10, 1)
  lazy val writeCounts = Gen.range("Writes")(10, 60, 10) 
  lazy val pathParts = List(
    "a",
    "b",
    "c",
    "d",
    "e"
  )

  lazy val allInfoItems: Vector[OdfInfoItem] = (for{
    pathSeq <- pathParts.permutations   
    path = Path("Objects/" + pathSeq.mkString("/"))
    infoitem = OdfInfoItem( path, Vector(OdfValue(1,new Timestamp(1))))
  } yield infoitem ).toVector
  println("Total infoItems created: " + allInfoItems.length)

  def test( db: DBReadWrite, name: String )(implicit system: ActorSystem): Unit = {
  import system.dispatcher
    performance of s"$name" in {
      Await.result(db.writeMany(allInfoItems ), 100.seconds)

      measure method "writeMany" in {
        val infoItems: Gen[Vector[OdfInfoItem]]= infoitemsCounts.map{ n =>
          allInfoItems.take(n).map{
            infoitem => infoitem.copy(
              values = Vector(
                OdfValue( 
                  random.nextInt,
                  new Timestamp( new Date().getTime() )
                )
              )
            )
          }.toVector
        } 
        using(infoItems) curve("n paths with single value in each")  afterTests {
          db.trimDB()
          } in { vls =>
            Try{
              Await.result(db.writeMany( vls ), 10.seconds)
              } match {
                case Success(_) => 
                case Failure(exp: Throwable) => println(exp.getMessage)
              }
          }      
          val values : Gen[Vector[OdfInfoItem]]= valueCounts.map{ n =>  
            val start = Random.nextInt(allInfoItems.length - 40)
            allInfoItems.slice(start, start + 40).map{
              infoitem => infoitem.copy(values = 
                (0 until n).map{ i =>
                  OdfValue( 
                    random.nextInt,
                    new Timestamp( new Date().getTime() )
                  )
                }.toVector
                )
            } 
          }
          using(values) curve("120 paths with n values in each")  afterTests {
            db.trimDB()
            } in { vls =>
              Try{
                Await.result(db.writeMany( vls ), 10.seconds)
                } match {
                  case Success(_) => 
                  case Failure(exp: Throwable) => println(exp.getMessage)
                }
            }

            val writeSizes = writeCounts.map{ n => (0 until n)}
            val writes = writeSizes.map{
              case arr => 
                arr.map{
                  case _ =>
                    val start = Random.nextInt(allInfoItems.length - 40)
                    allInfoItems.slice(start, start + 40).map{
                      case infoitem =>
                        infoitem.copy( 
                          values = Vector(
                            OdfValue( 
                              random.nextInt,
                              new Timestamp( new Date().getTime() )
                            )
                          )
                        )
                    }
                }
            }
            using(writes) curve("n concurrent writeManys") in { ws =>
                Try{
                  Await.result(
                    Future.sequence( 
                      ws.map{
                        infos =>
                          //println(s"Write ${infos.length} paths")
                          db.writeMany(infos)
                      }.toVector
                      ).map(_.toVector), 
                    120.seconds
                  )
                  } match {
                    case Success(res) => 
                      //      counter += 1
                      //      println("writes writen: " + res.length)
                    case Failure(exp: Throwable) => println(exp.getMessage)
                  }
              }
      }
      measure method "getNBetween" in {
        lazy val readInfoItems= infoitemsCounts.map{ 
          n => 
            allInfoItems.take(n).map{
              infoitem => infoitem.copy(
                values = Vector()
              )
            }
        }
        using(readInfoItems) curve("10 newest values from n infoitems") in { odf =>
          Try{
            Await.result(
              db.getNBetween(odf, None, None, Some(10), None),
              10.seconds
            )
            } match {
              case Success(res) => 
                //      counter += 1
                //      println("writes writen: " + res.length)
              case Failure(exp: Throwable) => println(exp.getMessage)
            }
        }

        val readNewest = valueCounts
        val odfIter = allInfoItems.take(60)
        using(readNewest) curve("n newest values from 60 infoitems") in { newest =>
          Try{
            Await.result(
              db.getNBetween(odfIter, None, None, Some(newest), None),
              10.seconds
            )
            } match {
              case Success(res) => 
                //      counter += 1
                //      println("writes writen: " + res.length)
              case Failure(exp: Throwable) => println(exp.getMessage)
            }
        }
        using(readNewest) curve("n oldest values from 60 infoitems") in { oldest =>
          Try{
            Await.result(
              db.getNBetween(odfIter, None, None, None,Some(oldest)),
              10.seconds
            )
            } match {
              case Success(res) => 
                //      counter += 1
                //      println("writes writen: " + res.length)
              case Failure(exp: Throwable) => println(exp.getMessage)
            }
        }

        /*
        using(readNewest) curve("n newest values from objects") in { newest =>
          Try{
            Await.result(
              db.getNBetween(Vector(OdfObjects()), None, None, Some(newest), None),
              10.seconds
            )
            } match {
              case Success(res) => 
                //      counter += 1
                //      println("writes writen: " + res.length)
              case Failure(exp: Throwable) => println(exp.getMessage)
            }
        }
        using(readNewest) curve("n oldest values from objects")  afterTests {
          db.trimDB()
          } in { oldest =>
          Try{
            Await.result(
              db.getNBetween(Vector(OdfObjects()), None, None, None, Some(oldest)),
              10.seconds
            )
            } match {
              case Success(res) => 
                //      counter += 1
                //      println("writes writen: " + res.length)
              case Failure(exp: Throwable) => println(exp.getMessage)
            }
        }*/
      }
    }
  }
  
}
