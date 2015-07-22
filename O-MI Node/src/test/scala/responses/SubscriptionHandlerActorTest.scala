package responses

import org.specs2.mutable._
import akka.testkit.{ TestKit, TestActorRef, TestProbe }
import akka.actor._
import database._
import scala.concurrent._
import scala.util.Try
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import testHelpers.{Actors, AfterAll}
import types.Path
import types.OdfTypes._
import types.OmiTypes._
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable
import java.sql.Timestamp

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SubscriptionHandlerActorTest extends Specification with AfterAll{
  sequential
 
  def newTimestamp(time: Long = -1L): Timestamp = {
    if(time == -1){
      new Timestamp(new java.util.Date().getTime)
    } else {
      new java.sql.Timestamp(time)
    }
  }
  
  implicit val dbConnection = new TestDB("subscriptionHandler-test")
  implicit val timeout = Timeout(5000)
  val testPath = Path("Objects/SubscriptionHandlerTest/testData")
  dbConnection.set(testPath, new java.sql.Timestamp(1000),  "test")



  def afterAll = {
    dbConnection.destroy()
  }

  
//    val testPath = Path("Objects/SubscriptionHandlerTest/testData")
//    val odf = OdfObjects(
//      Iterable(
//        OdfObject(
//          Path(testPath.init),
//          Iterable(
//            OdfInfoItem(
//              testPath,
//              Iterable.empty[OdfValue]
//            )
//          ),
//          Iterable.empty[OdfObject]
//        )
//      )
//    )
    
    val testSub1 = SubscriptionRequest(
        Duration.apply(60,"seconds"),
        Duration.apply(2, "seconds"),
        dbConnection.getNBetween(dbConnection.get(testPath), None, None, None, None).get,
        callback = Some("test")
      )
      
      /*
       * 
  case class NewDBSub(
  val interval: Duration,
  val startTime: Timestamp,
  val ttl: Duration,
  val callback: Option[String]
) extends SubLike with DBSubInternal
       */
    val testSub2 = SubscriptionRequest(
        Duration.apply(60, "seconds"),
        Duration.apply(-1, "seconds"),
        dbConnection.getNBetween(dbConnection.get(testPath), None, None, None, None).get,
        callback = Some("localhost")
      )

    val testId1 = Promise[Int]
    val testId2 = Promise[Int]
    val testId3 = dbConnection.saveSub(NewDBSub(Duration.apply(-1, "seconds"),newTimestamp(), Duration.apply(2, "seconds"), Some("localhost")), Seq(testPath))

    "SubscriptionHandlerActor" should {
    
    
//    "load event sub into memory at startup and remove eventsub from memory if ttl has expired" in new agentSystem.Actorstest(ActorSystem("subsctiptionhandlertest")) {
//      val subscriptionHandler = TestActorRef[SubscriptionHandler](Props(new SubscriptionHandler()), "Sub Handler")
//      val subscriptionActor = subscriptionHandler.underlyingActor
//      val probe = TestProbe()
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == testId3.id) === true//_._2.id == testId3) == true
//      Thread.sleep(2000)
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == testId3.id) === true
//      Future{dbConnection.set(testPath, newTimestamp(), "value")}
//      Thread.sleep(1000)
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == testId3.id) === false
//      dbConnection.removeSub(testId3)
//
//    }
    
    "load given interval sub into memory when sent NewSubscription message" in new Actors {

      val subscriptionHandler = TestActorRef[SubscriptionHandler](Props(new SubscriptionHandler()), "Sub Handler")
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()

      val duration = scala.concurrent.duration.Duration(1000, "ms")

      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
      testId1.success(Await.result(subscriptionHandler.ask(NewSubscription(testSub1)).mapTo[Try[Int]], Duration.Inf).get)

      val futureId: Int = Await.result(testId1.future, duration)
      Thread.sleep(1000)
      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === true

    }

    "load event subs into memory when sent NewSubscription message" in new Actors {
      

      val subscriptionHandler = TestActorRef[SubscriptionHandler](Props(new SubscriptionHandler()), "Sub Handler")
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      val duration = scala.concurrent.duration.Duration(1000, "ms")
      
      val firstQuery = subscriptionActor.getEventSubs(testPath.toString())//.exists(_.id == 0) === false

      testId2.success(Await.result(subscriptionHandler.ask(NewSubscription(testSub2)).mapTo[Try[Int]], Duration.Inf).get)
    
      val futureId = Await.result(testId2.future,duration)
      Thread.sleep(1000)
      firstQuery.exists(_.id == futureId) === false
      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == futureId) === true
      


    }

    "remove given interval sub from queue when sent remove message" in new Actors {
      //this test probably fails if previous test fails
      val subscriptionHandler = TestActorRef[SubscriptionHandler](Props(new SubscriptionHandler()), "Sub Handler")
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()

      val futureId: Int = Await.result(testId1.future, scala.concurrent.duration.Duration(1000, "ms"))
      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === true

      probe.send(subscriptionHandler, RemoveSubscription(futureId))
      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
      
    }

    "remove given event sub from memory when sent remove message" in new Actors {
      val subscriptionHandler = TestActorRef[SubscriptionHandler](Props(new SubscriptionHandler()), "Sub Handler")
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      val duration = scala.concurrent.duration.Duration(1000, "ms")
      val futureId: Int = Await.result(testId2.future, duration)
      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == futureId) === false
    }
  }
}
