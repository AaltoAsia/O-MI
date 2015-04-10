package responses

import org.specs2.mutable._
import akka.testkit.{ TestKit, TestActorRef, TestProbe }
import akka.actor._
import database.{ DBSub, DBSensor }
import parsing.Types.Path
import scala.concurrent._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.Duration
import testHelpers.Actors

class SubscriptionHandlerActorTest extends Specification {
  

  database.SQLite.set(new DBSensor(Path("SubscriptionHandlerTest/testData"), "test", new java.sql.Timestamp(1000)))
  val testPath = Path("SubscriptionHandlerTest/testData")

  val testSub1 = new DBSub(Array(testPath), 60, 2, Some("test"), None)
  val testSub2 = new DBSub(Array(testPath), 60, -1, Some("test"), None)

  val testId1 = Promise[Int]
  val testId2 = Promise[Int]
  val testId3 = database.SQLite.saveSub(new DBSub(Array(testPath), 2, -1, Some("test"), None))

  "SubscriptionHandlerActor" should {
    sequential
    "remove eventsub from memory if ttl has expired" in new Actors {

      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      subscriptionActor.getEventSubs.exists(_._2.id == testId3) == true
      Thread.sleep(2000)
      subscriptionActor.getEventSubs.exists(_._2.id == testId3) == true
      subscriptionActor.checkEventSubs(Array(testPath))
      subscriptionActor.getEventSubs.exists(_._2.id == testId3) == false
      database.SQLite.removeSub(testId3)

    }
    "load given interval sub into memory when sent load message and remove when sent remove message" in new Actors {

      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      //      testId.future

      testId1.success(database.SQLite.saveSub(testSub1))

      val futureId: Int = Await.result(testId1.future, scala.concurrent.duration.Duration(1000, "ms"))
      //      subscriptionActor.eventSubs.isEmpty === true
      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
      subscriptionHandler.tell(NewSubscription(futureId), probe.ref)

      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === true
      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
    }

    "load given event subs into memory when sent load message" in new Actors {
      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()

      testId2.success(database.SQLite.saveSub(testSub2))

      val futureId: Int = Await.result(testId2.future, scala.concurrent.duration.Duration(1000, "ms"))

      subscriptionActor.getEventSubs.exists(_._2.id == futureId) == false
      subscriptionHandler.tell(NewSubscription(futureId), probe.ref)

      subscriptionActor.getEventSubs.exists(_._2.id == futureId) === true

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
      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()

      val futureId: Int = Await.result(testId2.future, scala.concurrent.duration.Duration(1000, "ms"))

      subscriptionActor.getEventSubs.exists(_._2.id == futureId) === true
      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
      subscriptionActor.getEventSubs.exists(_._2.id == futureId) === false
    }
  }
}