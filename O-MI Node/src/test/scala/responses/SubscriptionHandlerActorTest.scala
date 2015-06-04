package responses

import parsing.Types.Path
import parsing.Types.OdfTypes._ 
import parsing.Types.OmiTypes._
import org.specs2.mutable._
import akka.testkit.{ TestKit, TestActorRef, TestProbe }
import akka.actor._
import database._
import scala.concurrent._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.Duration
import testHelpers.Actors
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable

class SubscriptionHandlerActorTest extends Specification {
  sequential
  
  implicit val dbConnection = new SQLiteConnection // TestDB("subscriptionHandler-test")

  step {
    dbConnection.clearDB()
    dbConnection.set(new DBSensor(Path("SubscriptionHandlerTest/testData"), "test", new java.sql.Timestamp(1000)))
  }

  "SubscriptionHandlerActor" should {
    val testPath = Path("SubscriptionHandlerTest/testData")
    val odf = OdfObjects(
      Iterable(
        OdfObject(
          Path(testPath.head),
          Iterable(
            OdfInfoItem(
              testPath,
              Iterable.empty[OdfValue]
            )
          ),
          Iterable.empty[OdfObject]
        )
      )
    )
    
    val testSub1 = SubscriptionRequest(
        60,
        2,
        odf,
        callback = Some("test")
      )
    val testSub2 = SubscriptionRequest(
        60,
        -1,
        odf,
        callback = Some("test")
      )

    val testId1 = Promise[Int]
    val testId2 = Promise[Int]
    val testId3 = dbConnection.saveSub(new DBSub(Array(testPath), 2, -1, Some("test"), None))
    "remove eventsub from memory if ttl has expired" in new Actors {

      val subscriptionHandler = TestActorRef[SubscriptionHandler]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      subscriptionActor.getEventSubs.exists(_._2.id == testId3) == true
      Thread.sleep(2000)
      subscriptionActor.getEventSubs.exists(_._2.id == testId3) == true
      subscriptionActor.checkEventSubs(Array(testPath))
      subscriptionActor.getEventSubs.exists(_._2.id == testId3) == false
      dbConnection.removeSub(testId3)

    }
    "load given interval sub into memory when sent load message and remove when sent remove message" in new Actors {

      val subscriptionHandler = TestActorRef[SubscriptionHandler]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      val duration = scala.concurrent.duration.Duration(1000, "ms")
      //      testId.future

      subscriptionHandler.tell(NewSubscription(testSub1), probe.ref)
      //      subscriptionActor.eventSubs.isEmpty === true

      probe.expectMsgType[Int](Duration.apply(2400, "ms")) === 0
      //subscriptionActor.getIntervalSubs.exists(_.id == 0) === true
      subscriptionHandler.tell(RemoveSubscription(0), probe.ref)
      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
      subscriptionActor.getIntervalSubs.exists(_.id == 0) === false
    }

    "load given event subs into memory when sent load message" in new Actors {
      val subscriptionHandler = TestActorRef[SubscriptionHandler]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      val duration = scala.concurrent.duration.Duration(1000, "ms")
      subscriptionActor.getEventSubs.exists(_._2.id == 0) === false

      subscriptionHandler.tell(NewSubscription(testSub1), probe.ref)

      probe.expectMsgType[Int](Duration.apply(2400, "ms")) === 0

    }

//    "remove given interval sub from queue when sent remove message" in new Actors {
//      //this test probably fails if previous test fails
//      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
//      val subscriptionActor = subscriptionHandler.underlyingActor
//      val probe = TestProbe()
//
//      val futureId: Int = Await.result(testId1.future, scala.concurrent.duration.Duration(1000, "ms"))
//
//      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === true
//
//      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
//      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
//      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
//      
//    }

    "remove given event sub from memory when sent remove message" in new Actors {
      val subscriptionHandler = TestActorRef[SubscriptionHandler]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()

      //subscriptionActor.getEventSubs.exists(_._2.id == 0) === true
      subscriptionHandler.tell(RemoveSubscription(0), probe.ref)
      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
      subscriptionActor.getEventSubs.exists(_._2.id == 0) === false
    }
  }
}
