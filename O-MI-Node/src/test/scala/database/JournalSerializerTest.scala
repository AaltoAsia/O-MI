package database

import java.sql.Timestamp

import akka.stream.ActorMaterializer
import journal._
import journal.serialization.JournalProtoBufSerializer
import org.specs2.Specification
import org.specs2.specification.core.SpecStructure
import org.specs2.concurrent.ExecutionEnv
import types.Path
import types.odf._
import testHelpers._
class JournalSerializerTest(implicit ee: ExecutionEnv) extends Specification with SilentActorSystem{
  implicit val mat = ActorMaterializer()
  val JournalSerializer = new JournalProtoBufSerializer()
  def is: SpecStructure = s2"""
  Journal Serializer should serialize and deserialize protobuf classes correctly
    WriteLatest ${serializeAndDeserialize(PWriteLatest())}
    PersistentValue ${serializeAndDeserialize(PPersistentValue())}
    ErasePath ${serializeAndDeserialize(PErasePath())}
    PersistentNode ${serializeAndDeserialize(PPersistentNode())}
    Object ${serializeAndDeserialize(PObject())}
    Objects ${serializeAndDeserialize(PObjects())}
    Timestamp ${serializeAndDeserialize(PTimestamp())}
    Qlmid ${serializeAndDeserialize(PQlmid())}
    Description ${serializeAndDeserialize(PDescription())}
    MetaData ${serializeAndDeserialize(PMetaData())}
    InfoItem ${serializeAndDeserialize(PInfoItem())}
    Union ${serializeAndDeserialize(PUnion())}
    EventSub ${serializeAndDeserialize(PEventSub())}
    EventSubs ${serializeAndDeserialize(PEventSubs())}
    PolledSub ${serializeAndDeserialize(PPolledSub())}
    SubIds ${serializeAndDeserialize(PSubIds())}
    SubStoreState ${serializeAndDeserialize(PSubStoreState())}
    Callback ${serializeAndDeserialize(PCallback())}
    PollNormalEventSub ${serializeAndDeserialize(PPollNormalEventSub())}
    PollNewEventSub ${serializeAndDeserialize(PPollNewEventSub())}
    PollIntervalSub ${serializeAndDeserialize(PPollIntervalSub())}
    IntervalSub ${serializeAndDeserialize(PIntervalSub())}
    NormalEventSub ${serializeAndDeserialize(PNormalEventSub())}
    NewEventSub ${serializeAndDeserialize(PNewEventSub())}
    AddSub ${serializeAndDeserialize(PAddSub())}
    RemoveEventSub ${serializeAndDeserialize(PRemoveEventSub())}
    RemoveIntervalSub ${serializeAndDeserialize(PRemoveIntervalSub())}
    RemovePollSub ${serializeAndDeserialize(PRemovePollSub())}
    PollSub ${serializeAndDeserialize(PPollSub())}
    ValueList ${serializeAndDeserialize(PValueList())}
    PathToData ${serializeAndDeserialize(PPathToData())}
    PollData ${serializeAndDeserialize(PPollData())}
    AddPollData ${serializeAndDeserialize(PAddPollData())}
    PollEventSubscription ${serializeAndDeserialize(PPollEventSubscription())}
    PollIntervalSubscription ${serializeAndDeserialize(PPollIntervalSubscription())}
    RemovePollSubData ${serializeAndDeserialize(PRemovePollSubData())}
  Converting between odf types and protobuff types should work correctly
    InfoItem $buildInfo
    QlmnID $buildQlmid
    Object $buildObject
    Objects $buildObjects
    values $asValue"""

  private def serializeAndDeserialize(o: AnyRef) = {
    val manifest = o.getClass.getName
    val serialized = JournalSerializer.toBinary(o)
    val deserialized = JournalSerializer.fromBinary(serialized, manifest)
    o ====  deserialized
  }

  private def buildInfo = {
    val path = "Objects/test/test1"
    val orig = InfoItem(
      Path(path),
      Some("testType"),
      Vector(QlmID("test1", Some("testType"),Some("tagtype"),None,None,Map("testKey"->"testValue"))),
      Set(Description("description text",Some("english"))),
      Vector.empty,
      None,
      Map("testKey"->"testValue"))
    val persisted = orig.persist.ii
    persisted must beSome and (Models.buildInfoItemFromProtobuf(path, persisted.get) === orig )
  }
  private def buildQlmid = {
    val orig = QlmID(
      "test",
      Some("idType"),
      Some("tagtype"),
      Some(new Timestamp(87213L)),
      Some(new Timestamp(980000)),
      Map("testkey" -> "testvalue")
    )
    val persisted = orig.persist
    Models.buildQlmIDFromProtobuf(persisted) === orig
  }
  private def buildObject = {
    val path = "Objects/TestObject"
    val orig = Object(
      Path(path),
      Some("testType"),
      Set(Description("testdescription",Some("english"))),
      Map("testkey"->"testValue")
    )
    val persisted = orig.persist.obj
    persisted must beSome and (Models.buildObjectFromProtobuf(path,persisted.get) === orig)
  }
  private def buildObjects = {
    val orig = Objects(Some("v1.0.0"), Map("testKey"->"testValue"))
    val persisted = orig.persist.objs
    persisted must beSome and (Models.buildObjectsFromProtobuf(persisted.get) === orig)
  }
  private def buildImmutableOdf = {
    val orig = ImmutableODF(Seq(Objects(), InfoItem("Objects/test/test1", Vector.empty), Object(Path("Objects/test"))))
    val persisted = orig.nodes.map { case (k, v) => k.toString -> PPersistentNode(v.persist) }
    Models.buildImmutableOdfFromProtobuf(persisted) === orig
  }
  private def asValue = {
    val ts = new Timestamp(1234567)
    val floatValue = FloatValue(20.0f,ts)
    val doubleValue = DoubleValue(20.0,ts)
    val shortValue = ShortValue(20,ts)
    val intValue = IntValue(20,ts)
    val longValue = LongValue(20,ts)
    val booleanValue = BooleanValue(value = true, ts)
    val odfValue = ODFValue(ImmutableODF(Seq(Objects(Some("2.0")), Object(Path("Objects/test")))),ts)
    val stringValue = StringPresentedValue("testValue",ts, "testValuetype")
    testValue(floatValue) and
    testValue(doubleValue) and
    testValue(shortValue) and
    testValue(intValue) and
    testValue(longValue) and
    testValue(booleanValue) and
    testValue(odfValue) and
    testValue(stringValue)
  }
  private def testValue(value: Value[Any]) = {
    val persisted = value.persist
    Models.asValue(persisted) === value
  }

}
