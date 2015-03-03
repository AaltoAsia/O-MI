package responses

import org.specs2.mutable._
import akka.testkit.{TestKit, TestActorRef}
import akka.actor._
import com.typesafe.config.ConfigFactory
import org.specs2.specification.Scope
import database.DBSub
import parsing.Types.Path

class SubscriptionHandlerActorTest extends Specification {
  
  class Actors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with Scope

  val testSub1 = new DBSub(Array(Path("SubscriptionTest/test")), 2, 2,None,None)
  
  val testID = database.SQLite.saveSub(testSub1)
  
  "SubscriptionHandlerActor" should{
    "load given sub into memory when sent load message" in new Actors{
      val subscriptionHandler = TestActorRef[SubscriptionHandlerActor]
      val subscriptionActor = subscriptionHandler.underlyingActor
      subscriptionActor.eventSubs.isEmpty === true
      subscriptionActor.intervalSubs.isEmpty === true
    }
  }
}