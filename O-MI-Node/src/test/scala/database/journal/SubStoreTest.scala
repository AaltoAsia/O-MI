package database.journal

import java.sql.Timestamp

import akka.actor.{ActorSystem, PoisonPill}
import database.journal.Models.{AddPollSub, GetAllPollSubs, PollSubCommand}
import database.{PollIntervalSub, PollNewEventSub, PollNormalEventSub, PolledSub}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import testHelpers.Actorstest
import types.Path
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._

class SubStoreTest(implicit ee: ExecutionEnv) extends Specification with AfterAll{

  def system: ActorSystem = Actorstest.createAs()

  def afterAll: Unit = {
    system.terminate()
  }

  "SubStore should" >> {

    val startTime: Timestamp = Timestamp.valueOf("2018-08-09 16:00:00")
    val endTime: Timestamp = new Timestamp(Long.MaxValue)
    val paths = Vector(Path("Objects/subStoreTest1"), Path("Objects/subStoreTest2"))
    "for polled subs" >> {
      val validNormalEventPollSub = PollNormalEventSub(1,endTime,startTime,startTime,paths)
      val validNewEventPollSub = PollNewEventSub(2, endTime,startTime,startTime,paths)
      val validIntervalPollSub = PollIntervalSub(3,endTime,1 second,startTime,startTime,paths)
      //ttl has ended for subs below
      val invalidNormalEventPollSub = PollNormalEventSub(4,startTime,startTime,startTime,paths)
      val invalidNewEventPollSub = PollNewEventSub(5, startTime,startTime,startTime,paths)
      val invalidIntervalPollSub = PollIntervalSub(6,startTime,1 second,startTime,startTime,paths)
      "persist adding subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-001"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddPollSub(validNormalEventPollSub)
        subStore1 ! AddPollSub(validNewEventPollSub)
        subStore1 ! AddPollSub(validIntervalPollSub)
        //invalid subs, persisted but not recovered
        subStore1 ! AddPollSub(invalidNormalEventPollSub)
        subStore1 ! AddPollSub(invalidNewEventPollSub)
        subStore1 ! AddPollSub(invalidIntervalPollSub)
        receiveN(6)
        val probe = TestProbe()
        probe watch subStore1
        subStore1 ! PoisonPill //kill first substore actor
        probe.expectTerminated(subStore1)
        val subStore2 = system.actorOf(SubStore.props(subStoreId)) //same id
        subStore2 ! GetAllPollSubs
        val resp = expectMsgType[Set[PolledSub]]
        resp must have size(3) //invalid subs not recovered
        resp must contain(validNormalEventPollSub)
        resp must contain(validNewEventPollSub)
        resp must contain(validIntervalPollSub)
      }
      "persist polling subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-002"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddPollSub(validNormalEventPollSub)
        subStore1 ! AddPollSub(validNewEventPollSub)
        subStore1 ! AddPollSub(validIntervalPollSub)
        receiveN(3)
        subStore1 ! PollSubCommand(validNormalEventPollSub.id)
        subStore1 ! PollSubCommand(validNewEventPollSub.id)
        subStore1 ! PollSubCommand(validIntervalPollSub.id)
        receiveN(3)
        val probe = TestProbe()
        probe watch subStore1
        subStore1 ! PoisonPill //kill first substore actor
        probe.expectTerminated(subStore1)
        val subStore2 = system.actorOf(SubStore.props(subStoreId)) //same id
        subStore2 ! GetAllPollSubs
        val resp = expectMsgType[Set[PolledSub]]
        resp.foreach(sub => (sub.lastPolled.after(startTime)) must beTrue)
      }
     // "persist polling subscriptions correctly" >> new Actorstest(system){

    //  }
    }
  }

}
