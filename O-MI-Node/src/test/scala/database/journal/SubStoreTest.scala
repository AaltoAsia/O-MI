package database.journal

import java.sql.Timestamp

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.http.scaladsl.model.Uri
import database.journal.Models._
import database._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import testHelpers.Actorstest
import types.Path
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import types.omi.{HTTPCallback, RawCallback}
import SubStore._

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
    val callback = HTTPCallback(Uri("http://localhost:2345"))
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
        subStore1 ! SaveSnapshot()
        receiveN(7)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
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
        subStore1 ! SaveSnapshot()
        receiveN(4)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
        subStore2 ! GetAllPollSubs
        val resp = expectMsgType[Set[PolledSub]]
        resp must have size(3)
        resp.foreach(sub => (sub.lastPolled.after(startTime)) must beTrue)
      }
      "persist removing subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-003"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddPollSub(validNormalEventPollSub.copy(id=7))
        subStore1 ! AddPollSub(validNormalEventPollSub)
        subStore1 ! AddPollSub(validNewEventPollSub)
        subStore1 ! AddPollSub(validIntervalPollSub)
        receiveN(4)
        subStore1 ! RemovePollSub(validNormalEventPollSub.id)
        subStore1 ! RemovePollSub(validNewEventPollSub.id)
        subStore1 ! RemovePollSub(validIntervalPollSub.id)
        subStore1 ! SaveSnapshot()
        receiveN(4)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
        subStore2 ! GetAllPollSubs
        val resp = expectMsgType[Set[PolledSub]]
        resp must have size(1)
        resp must contain(validNormalEventPollSub.copy(id = 7 ))
      }

    }
    "for event subs" >> {
      val validNormalEventSub= NormalEventSub(1,paths,endTime,callback)
      val validNewEventSub = NewEventSub(2,paths,endTime,callback)
      //ttl has ended for subs below
      val invalidNormalEventSub= NormalEventSub(4,paths,startTime,callback)
      val invalidNewEventSub = NewEventSub(5,paths,startTime,callback)
      "persist adding subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-004"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddEventSub(validNormalEventSub)
        subStore1 ! AddEventSub(validNewEventSub)
        subStore1 ! AddEventSub(invalidNormalEventSub)
        subStore1 ! AddEventSub(invalidNewEventSub)
        subStore1 ! SaveSnapshot()
        receiveN(5)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
        subStore2 ! GetAllEventSubs
        val resp = expectMsgType[Set[EventSub]]
        resp must have size(2)
        resp must contain(validNormalEventSub)
        resp must contain(validNewEventSub)
      }
      "persist removing subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-005"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddEventSub(validNormalEventSub)
        subStore1 ! AddEventSub(validNewEventSub)
        subStore1 ! AddEventSub(validNormalEventSub.copy(id = 6)) //
        subStore1 ! AddEventSub(validNewEventSub.copy(id=7)) //
        receiveN(4)
        subStore1 ! RemoveEventSub(validNormalEventSub.id)
        subStore1 ! RemoveEventSub(validNewEventSub.id)
        subStore1 ! SaveSnapshot()
        receiveN(3)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
        subStore2 ! GetAllEventSubs
        val resp = expectMsgType[Set[EventSub]]
        resp must have size(2)
        resp must contain(validNormalEventSub.copy(id=6))
        resp must contain(validNewEventSub.copy(id=7))
      }

    }
    "for interval subs" >> {
      val validIntervalSub = IntervalSub(1,paths,endTime,callback,1 seconds, startTime)
      val invalidIntervalSub = IntervalSub(2,paths,startTime,callback,1 seconds,startTime)
      "persist adding subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-006"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddIntervalSub(validIntervalSub)
        subStore1 ! AddIntervalSub(invalidIntervalSub)
        subStore1 ! SaveSnapshot()
        receiveN(3)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
        subStore2 ! GetAllIntervalSubs
        val resp = expectMsgType[Set[IntervalSub]]
        resp must have size(1)
        resp must contain(validIntervalSub)
      }
      "persist removing subscriptions correctly" >> new Actorstest(system){
        val subStoreId = "sub-007"
        val subStore1 = system.actorOf(SubStore.props(subStoreId))
        subStore1 ! AddIntervalSub(validIntervalSub)
        subStore1 ! AddIntervalSub(validIntervalSub.copy(id=3))
        receiveN(2)
        subStore1 ! RemoveIntervalSub(validIntervalSub.id)
        subStore1 ! SaveSnapshot()
        receiveN(2)
        val subStore2 = terminateAndStart(subStore1,subStoreId)
        subStore2 ! GetAllIntervalSubs
        val resp = expectMsgType[Set[IntervalSub]]
        resp must have size(1)
        resp must contain(validIntervalSub.copy(id=3))
      }

    }
  }
  def terminateAndStart(ref: ActorRef, id: String)(implicit system: ActorSystem): ActorRef = {
    val probe = TestProbe()
    probe watch ref
    ref ! PoisonPill
    probe.expectTerminated(ref)
    system.actorOf(SubStore.props(id))
  }

}
