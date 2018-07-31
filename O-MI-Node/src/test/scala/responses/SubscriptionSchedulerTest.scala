package responses

import akka.testkit.TestProbe
import org.specs2.mutable._
import testHelpers.Actorstest
import scala.util.Try

import scala.concurrent.duration._

class SubscriptionSchedulerTest extends Specification {
  val scheduler = new SubscriptionScheduler

  "Subscription scheduler" should {

    "Answer to correct actor" in new Actorstest {
      val probe1 = TestProbe()
      val probe2 = TestProbe()
      scheduler.scheduleOnce(2 seconds, probe1.ref, "meg")

      probe2.expectNoMessage(2500 milliseconds)
      probe1.receiveN(1, 2500 milliseconds)
    }
    "Be accurate to few a milliseconds" in new Actorstest {
      Try{
        val probe = TestProbe()
        scheduler.scheduleOnce(3 seconds, probe.ref, "hello!")

        probe.expectNoMessage(2980 milliseconds)
        probe.receiveN(1, 3020 milliseconds)
      } must beSuccessfulTry.eventually(4, (4*3200).milliseconds)
    }
  }
}
