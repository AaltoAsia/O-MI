package responses

import org.specs2.mutable._
import akka.testkit.{TestKit, TestActorRef, TestProbe}
import akka.actor._
import com.typesafe.config.ConfigFactory
import org.specs2.specification.Scope
import database.DBSub
import parsing.Types.Path
import scala.concurrent._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration.Duration

class SubscriptionHandlerActorTest extends Specification {
  
  class Actors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with Scope

  val testSub1 = new DBSub(Array(Path("SubscriptionTest/test")), 60, 2,Some("test"),None)
  
  val testId = Promise[Int]

  "SubscriptionHandlerActor" should {
    sequential
    "load given interval sub into memory when sent load message" in new Actors{
      
      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
//      testId.future
      
      testId.success(database.SQLite.saveSub(testSub1))

      val futureId: Int = Await.result(testId.future, scala.concurrent.duration.Duration(1000,"ms"))
//      subscriptionActor.eventSubs.isEmpty === true
      subscriptionActor.intervalSubs.exists(n => n.id == futureId) === false
      subscriptionHandler.tell(NewSubscription(futureId), probe.ref)

      subscriptionActor.intervalSubs.exists(n => n.id == futureId) === true
    } 
    
    "remove given sub from queue when sent remove message" in new Actors{
      //this test probably fails if previous test fails
      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      val probe = TestProbe()
      
      val futureId: Int = Await.result(testId.future, scala.concurrent.duration.Duration(1000,"ms"))

//      subscriptionActor.eventSubs.isEmpty === true
//      subscriptionActor.intervalSubs.isEmpty === false
      subscriptionActor.intervalSubs.exists(n => n.id == futureId) === true
//      val answer = subscriptionHandler.ask(
//          probe.ref, RemoveSubscription(futureId))(Timeout(Duration.apply(1500, "ms")))
//      Await.result(answer, Duration.apply(1500, "ms")) === true
      
      subscriptionHandler.tell(RemoveSubscription(futureId), probe.ref)
      probe.expectMsgType[Boolean](Duration.apply(2400,"ms")) === true
      subscriptionActor.intervalSubs.exists(n => n.id == futureId) === false
    }
  }
}