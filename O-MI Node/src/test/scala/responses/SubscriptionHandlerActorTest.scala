//package responses
//
//import parsing.Types.Path
//import parsing.Types.OdfTypes._ 
//import parsing.Types.OmiTypes._
//import org.specs2.mutable._
//import akka.testkit.{ TestKit, TestActorRef, TestProbe }
//import akka.actor._
//import database._
//import scala.concurrent._
//import akka.pattern.ask
//import akka.util.Timeout
//import scala.concurrent.duration.Duration
//import testHelpers.{Actors, BeforeAll}
//import scala.collection.JavaConversions.asJavaIterable
//import scala.collection.JavaConversions.seqAsJavaList
//import scala.collection.JavaConversions.iterableAsScalaIterable
//import java.sql.Timestamp
//
//
//class SubscriptionHandlerActorTest extends Specification with BeforeAll{
//  sequential
//  def newTimestamp(time: Long = -1L): Timestamp = {
//    if(time == -1){
//      new Timestamp(new java.util.Date().getTime)
//    } else {
//      new java.sql.Timestamp(time)
//    }
//  }
//  
//  implicit val dbConnection = new DatabaseConnection // TestDB("subscriptionHandler-test")
//
//  def beforeAll={
//    dbConnection.clearDB()
//    dbConnection.set(Path("Objects/SubscriptionHandlerTest/testData"), new java.sql.Timestamp(1000),  "test")
//  }
//
//  "SubscriptionHandlerActor" should {
//    val testPath = Path("SubscriptionHandlerTest/testData")
//    val odf = OdfObjects(
//      Iterable(
//        OdfObject(
//          Path(testPath.head),
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
//    
//    val testSub1 = SubscriptionRequest(
//        60,
//        2,
//        odf,
//        callback = Some("test")
//      )
//    val testSub2 = SubscriptionRequest(
//        60,
//        -1,
//        odf,
//        callback = Some("test")
//      )
//
//    val testId1 = Promise[Int]
//    val testId2 = Promise[Int]
//    val testId3 = dbConnection.saveSub(NewDBSub(-1,newTimestamp(), 2, None), Array(testPath))
//    "remove eventsub from memory if ttl has expired" in new Actors {
//
//      val subscriptionHandler = TestActorRef[SubscriptionHandler]
//      val subscriptionActor = subscriptionHandler.underlyingActor
//      val probe = TestProbe()
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == testId3) === true//_._2.id == testId3) == true
//      Thread.sleep(2000)
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == testId3) === true
//      subscriptionActor.checkEventSubs(Array(testPath))
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == testId3) === false
//      dbConnection.removeSub(testId3)
//
//    }
//    "load given interval sub into memory when sent load message and remove when sent remove message" in new Actors {
//
//      val subscriptionHandler = TestActorRef[SubscriptionHandler]
//      val subscriptionActor = subscriptionHandler.underlyingActor
//      val probe = TestProbe(){
//        def returnId
//      }
//      val duration = scala.concurrent.duration.Duration(1000, "ms")
//      //      testId.future
//
//      testId1.success(subscriptionHandler.tell(NewSubscription(testSub1), probe .ref))
////      subscriptionActor.eventSubs.isEmpty === true
//      val futureId: Int = Await.result(testId1.future, duration)
//      probe.expectMsgType[Int](Duration.apply(2400, "ms")) === futureId
//      //subscriptionActor.getIntervalSubs.exists(_.id == 0) === true
//      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
//      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
//      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
//    }
//
//    "load given event subs into memory when sent load message" in new Actors {
//      val subscriptionHandler = TestActorRef[SubscriptionHandler]
//      val subscriptionActor = subscriptionHandler.underlyingActor
//      val probe = TestProbe()
//      val duration = scala.concurrent.duration.Duration(1000, "ms")
//      
//      val firstQuery = subscriptionActor.getEventSubs(testPath.toString())//.exists(_.id == 0) === false
//
//      testId2.success(subscriptionHandler.tell(NewSubscription(testSub1), probe.ref))      
//      val futureId = Await.result(testId2.future,duration)
//      
//      firstQuery.exists(_.id == futureId) === false
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == 0) === true
//      
////      probe.expectMsgType[Int](Duration.apply(2400, "ms")) === 0
//
//    }
//
////    "remove given interval sub from queue when sent remove message" in new Actors {
////      //this test probably fails if previous test fails
////      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
////      val subscriptionActor = subscriptionHandler.underlyingActor
////      val probe = TestProbe()
////
////      val futureId: Int = Await.result(testId1.future, scala.concurrent.duration.Duration(1000, "ms"))
////
////      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === true
////
////      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
////      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
////      subscriptionActor.getIntervalSubs.exists(_.id == futureId) === false
////      
////    }
//
//    "remove given event sub from memory when sent remove message" in new Actors {
//      val subscriptionHandler = TestActorRef[SubscriptionHandler]
//      val subscriptionActor = subscriptionHandler.underlyingActor
//      val probe = TestProbe()
//      val duration = scala.concurrent.duration.Duration(1000, "ms")
//      
//      val futureId: Int = Await.result(testId2.future, duration)
//
//      subscriptionActor.getEventSubs.exists(_._2.id == futureId) === true
//      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
//      probe.expectMsgType[Boolean](Duration.apply(2400, "ms")) === true
//      subscriptionActor.getEventSubs(testPath.toString()).exists(_.id == futureId) === false
//    }
//  }
//}
