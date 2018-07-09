package responses

import scala.concurrent.duration._

import akka.testkit.TestProbe
import org.specs2.mutable._
import testHelpers.Actorstest

class SubscriptionSchedulerTest extends Specification {
  val scheduler = new SubscriptionScheduler

  "Subscription scheduler" should {

    "Answer to correct actor" in new Actorstest {
      val probe1 = TestProbe()
      val probe2 = TestProbe()
      scheduler.scheduleOnce(2 seconds, probe1.ref, "meg")

      probe2.expectNoMsg(2500 milliseconds)
      probe1.receiveN(1, 2500 milliseconds)
    }
    "Be accurate to few a milliseconds" in new Actorstest {
      val probe = TestProbe()
      scheduler.scheduleOnce(3 seconds, probe.ref, "hello!")

      probe.receiveN(1, 3010 milliseconds)
      probe.expectNoMsg(2990 milliseconds)
    }
  }
}
