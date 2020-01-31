package database.journal

import akka.actor.Props
import akka.persistence._
import collection.immutable.LongMap
import scala.util.Random

import Models._
import types.omi.Version._

object RequestInfoStore {
  //id given as parameter to make testing possible
  def props(id: String = "requeststore"): Props = Props(new RequestInfoStore(id))

  // from protobuf/peristentTypes.proto
  //case class PRequestInfo(endTime: Long, omiVersion: Double, odfVersion: Double, msgFormat: String) extends PersistentCommand

  //Request store protocol
  
  trait Change extends Event
  
  /** Add new request info, returns RequestId */
  case class AddInfo(endTime: Long, omiVersion: OmiVersion, odfVersion: Option[OdfVersion]) extends PersistentCommand
  /** Gets and removes the info, returns [[PRequestInfo]] */
  case class GetInfo(requestId: Long) extends Event
  /** Gets without removing the info, returns [[PRequestInfo]] */
  case class PeekInfo(requestId: Long)
  /** Gets without removing the info, returns [[ LongMap[PRequestInfo] ]] */
  case class PeekAll()
  //case class RemoveInfo(requestId: Long) extends Event

  // Maybe do later?
  //case class RequestId private (id: Long)
}
import RequestInfoStore._

class RequestInfoStore(override val persistenceId: String) extends JournalStore {
  private var infos: LongMap[PRequestInfo] = LongMap.empty

  private val rand = new Random()
  private def createNewId: Long = {
    val idCandidate: Long = rand.nextInt(Int.MaxValue)
    if (infos contains idCandidate) createNewId
    else idCandidate
  }

  def updateState: Change => Unit = {
    case info: PRequestInfo =>
      infos = infos + (info.id -> info)
    case PRemoveInfo(id) =>
      infos = infos - id
  }

  def receiveRecover: Receive = {
    case event: Change => updateState(event)
    case SnapshotOffer(_, snapshot: PRequestStore) =>// updateState(snapshot)
      infos = LongMap(snapshot.infos.toSeq:_*)
    case RecoveryCompleted => log.info("Recovery completed")
    case x: Any => log.error("Recover not implemented for " + x.toString)
  }

  def receiveCommand: Receive = receiveBoilerplate orElse {
    case SaveSnapshot(msg) => sender() !
      saveSnapshot(PRequestStore(infos))

    case GetInfo(id) =>
      val info = infos.get(id)
      info map {_ =>
        persist(PRemoveInfo(id))(updateState)
      }
      sender() ! info

    case AddInfo(endTime, omiVersion, odfVersion) =>
      val id = createNewId
      val info = PRequestInfo(id, endTime, omiVersion.number, odfVersion.map(_.number), odfVersion.map(_.msgFormat))
      persist(info)(updateState)
      sender() ! id

    case PeekInfo(id) =>
      sender() ! infos.get(id)


    case PeekAll() =>
      sender() ! infos
  }
}
