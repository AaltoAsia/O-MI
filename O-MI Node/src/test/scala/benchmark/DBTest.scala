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

trait UnCachedDBTest extends DBTest {
  val name: String = "UncachedDBTest"
  implicit val system = ActorSystem(name + "System")
  implicit val settings = OmiConfig(system)
  implicit val singleStores = new SingleStores(settings)
  val db: DBReadWrite = new UncachedTestDB(name,false)(system, singleStores, settings)
  test(db, "UncachedDB")
}
trait OldAndNewDBTest extends DBTest {
  val name: String = "OldAndNewDBTest"
   val oldsystem = ActorSystem(name + "OldSystem")
  val oldsettings = OmiConfig(oldsystem)
  val oldsingleStores = new SingleStores(oldsettings)
  val olddb: DBReadWrite = new UncachedTestDB("OldDB",false)(oldsystem, oldsingleStores, oldsettings)
  test(olddb, "OldDB")(oldsystem)
  val newsystem = ActorSystem(name + "NewSystem")
  val newsettings = OmiConfig(newsystem)
  val newsingleStores = new SingleStores(newsettings)
  val newdb: DBReadWrite = new TestDB("NewDB",false)(newsystem, newsingleStores, newsettings)
  test(newdb, "NewDB")(newsystem)
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
  lazy val pathCounts = Gen.range("paths")(10, 60, 10)// ++ Gen.range("values")(1, 10, 1)
  lazy val valueCounts = Gen.range("values")(10, 40, 10)// ++ Gen.range("values")(1, 10, 1)
  lazy val writeCounts = Gen.range("writes")(10, 60, 10) 
  lazy val pathParts = List(
    "a",
    "b",
    "c",
    "d",
    "e"
  )

  lazy val infoitems: Vector[OdfInfoItem] = (for{
    pathSeq <- pathParts.permutations   
    path = Path("Objects/" + pathSeq.mkString("/"))
    infoitem = OdfInfoItem( path, Vector(OdfValue(1,new Timestamp(1))))
  } yield infoitem ).toVector
  println("Total infoItems created: " + infoitems.length)

  def test( db: DBReadWrite, name: String )(implicit system: ActorSystem): Unit = {
  import system.dispatcher
    performance of s"$name" in {
      Await.result(db.writeMany(infoitems ), 100.seconds)

      measure method "writeMany" in {
        val paths: Gen[Vector[OdfInfoItem]]= pathCounts.map{ n =>
          infoitems.take(n).map{
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
        using(paths) curve("n paths with single value in each")  afterTests {
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
            infoitems.map{
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
                    val start = Random.nextInt(infoitems.length - 40)
                    infoitems.slice(start, start + 40).map{
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
            using(writes) curve("n concurrent writeManys") afterTests {
              db.trimDB()
              } in { ws =>
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
        lazy val readInfoItems= Gen.range("InfoItems")(20, 120, 20).map{ 
          n => 
            infoitems.take(n).map{
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

        val readNewest= Gen.range("Newest")(10, 40, 10) 
        val odfIter = infoitems.take(60)
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
      }
    }
  }
  
}
