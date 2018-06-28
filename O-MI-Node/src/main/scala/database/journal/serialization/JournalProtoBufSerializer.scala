package database.journal.serialization

import akka.serialization.SerializerWithStringManifest
import database.journal._

class JournalProtoBufSerializer extends SerializerWithStringManifest {
  final val WriteLatestManifest = "WL"
  final val PersistentValueManifest = "PV"//classOf[PPersistentValue].getName
  final val ErasePathManifest = "EP"//classOf[PErasePath].getName
  final val PersistentNodeManifest = "PN"//classOf[PPersistentNode].getName
  final val ObjectManifest = "O"//classOf[PObject].getName
  final val ObjectsManifest = "Os"//classOf[PObjects].getName
  final val TimestampManifest = "T"//classOf[PTimestamp].getName
  final val QlmidManifest = "Q"//classOf[PQlmid].getName
  final val DescriptionManifest = "D"//classOf[PDescription].getName
  final val MetaDataManifest = "M"//classOf[PMetaData].getName
  final val InfoItemManifest = "II"//classOf[PInfoItem].getName
  final val UnionManifest = "U"//classOf[PUnion].getName
  final val EventSubManifest = "E"//classOf[PEventSub].getName
  final val EventSubsManifest = "ES"//classOf[PEventSubs].getName
  final val PolledSubManifest = "P"//classOf[PPolledSub].getName
  final val SubIdsManifest = "SI"//classOf[PSubIds].getName
  final val SubStoreStateManifest = "SSS"//classOf[PSubStoreState].getName
  final val CallbackManifest = "C"//classOf[PCallback].getName
  final val PollNormalEventSubManifest = "PNE"//classOf[PPollNormalEventSub].getName
  final val PollNewEventSubManifest = "PNEW"//classOf[PPollNewEventSub].getName
  final val PollIntervalSubManifest = "PI"//classOf[PPollIntervalSub].getName
  final val IntervalSubManifest = "I"//classOf[PIntervalSub].getName
  final val NormalEventSubManifest = "NE"//classOf[PNormalEventSub].getName
  final val NewEventSubManifest = "NEW"//classOf[PNewEventSub].getName
  final val AddSubManifest = "AS"//classOf[PAddSub].getName
  final val RemoveEventSubManifest = "RE"//classOf[PRemoveEventSub].getName
  final val RemoveIntervalSubManifest = "RI"//classOf[PRemoveIntervalSub].getName
  final val RemovePollSubManifest = "RP"//classOf[PRemovePollSub].getName
  final val PollSubManifest = "PS"//classOf[PPollSub].getName
  final val ValueListManifest = "V"//classOf[PValueList].getName
  final val PathToDataManifest = "PT"//classOf[PPathToData].getName
  final val PollDataManifest = "PD"//classOf[PPollData].getName
  final val AddPollDataManifest = "APD"//classOf[PAddPollData].getName
  final val PollEventSubscriptionManifest = "PE"//classOf[PPollEventSubscription].getName
  final val PollIntervalSubscriptionManifest = "PIS"//classOf[PPollIntervalSubscription].getName
  final val RemovePollSubDataManifest = "RPD"//classOf[PRemovePollSubData].getName

  override def identifier = 1500

  override def manifest(o: AnyRef) = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case c: PWriteLatest => c.toByteArray
    case c: PPersistentValue => c.toByteArray
    case c: PErasePath => c.toByteArray
    case c: PPersistentNode => c.toByteArray
    case c: PObject => c.toByteArray
    case c: PObjects => c.toByteArray
    case c: PTimestamp => c.toByteArray
    case c: PQlmid => c.toByteArray
    case c: PDescription => c.toByteArray
    case c: PMetaData => c.toByteArray
    case c: PInfoItem => c.toByteArray
    case c: PUnion => c.toByteArray
    case c: PEventSub => c.toByteArray
    case c: PEventSubs => c.toByteArray
    case c: PPolledSub => c.toByteArray
    case c: PSubIds => c.toByteArray
    case c: PSubStoreState => c.toByteArray
    case c: PCallback => c.toByteArray
    case c: PPollNormalEventSub => c.toByteArray
    case c: PPollNewEventSub => c.toByteArray
    case c: PPollIntervalSub => c.toByteArray
    case c: PIntervalSub => c.toByteArray
    case c: PNormalEventSub => c.toByteArray
    case c: PNewEventSub => c.toByteArray
    case c: PAddSub => c.toByteArray
    case c: PRemoveEventSub => c.toByteArray
    case c: PRemoveIntervalSub => c.toByteArray
    case c: PRemovePollSub => c.toByteArray
    case c: PPollSub => c.toByteArray
    case c: PValueList => c.toByteArray
    case c: PPathToData => c.toByteArray
    case c: PPollData => c.toByteArray
    case c: PAddPollData => c.toByteArray
    case c: PPollEventSubscription => c.toByteArray
    case c: PPollIntervalSubscription => c.toByteArray
    case c: PRemovePollSubData => c.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case WriteLatestManifest => PWriteLatest.parseFrom(bytes)
    case PersistentValueManifest => PPersistentValue.parseFrom(bytes)
    case ErasePathManifest => PErasePath.parseFrom(bytes)
    case PersistentNodeManifest => PPersistentNode.parseFrom(bytes)
    case ObjectManifest => PObject.parseFrom(bytes)
    case ObjectsManifest => PObjects.parseFrom(bytes)
    case TimestampManifest => PTimestamp.parseFrom(bytes)
    case QlmidManifest => PQlmid.parseFrom(bytes)
    case DescriptionManifest => PDescription.parseFrom(bytes)
    case MetaDataManifest => PMetaData.parseFrom(bytes)
    case InfoItemManifest => PInfoItem.parseFrom(bytes)
    case UnionManifest => PUnion.parseFrom(bytes)
    case EventSubManifest => PEventSub.parseFrom(bytes)
    case EventSubsManifest => PEventSubs.parseFrom(bytes)
    case PolledSubManifest => PPolledSub.parseFrom(bytes)
    case SubIdsManifest => PSubIds.parseFrom(bytes)
    case SubStoreStateManifest => PSubStoreState.parseFrom(bytes)
    case CallbackManifest => PCallback.parseFrom(bytes)
    case PollNormalEventSubManifest => PPollNormalEventSub.parseFrom(bytes)
    case PollNewEventSubManifest => PPollNewEventSub.parseFrom(bytes)
    case PollIntervalSubManifest => PPollIntervalSub.parseFrom(bytes)
    case IntervalSubManifest => PIntervalSub.parseFrom(bytes)
    case NormalEventSubManifest => PNormalEventSub.parseFrom(bytes)
    case NewEventSubManifest => PNewEventSub.parseFrom(bytes)
    case AddSubManifest => PAddSub.parseFrom(bytes)
    case RemoveEventSubManifest => PRemoveEventSub.parseFrom(bytes)
    case RemoveIntervalSubManifest => PRemoveIntervalSub.parseFrom(bytes)
    case RemovePollSubManifest => PRemovePollSub.parseFrom(bytes)
    case PollSubManifest => PPollSub.parseFrom(bytes)
    case ValueListManifest => PValueList.parseFrom(bytes)
    case PathToDataManifest => PPathToData.parseFrom(bytes)
    case PollDataManifest => PPollData.parseFrom(bytes)
    case AddPollDataManifest => PAddPollData.parseFrom(bytes)
    case PollEventSubscriptionManifest => PPollEventSubscription.parseFrom(bytes)
    case PollIntervalSubscriptionManifest => PPollIntervalSubscription.parseFrom(bytes)
    case RemovePollSubDataManifest => PRemovePollSubData.parseFrom(bytes)

  }
}
