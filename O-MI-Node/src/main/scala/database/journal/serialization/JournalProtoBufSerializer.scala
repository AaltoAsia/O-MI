package database.journal.serialization

import akka.serialization.SerializerWithStringManifest
import database.journal.{PErasePath, PPersistentValue, PWriteLatest}

class JournalProtoBufSerializer extends SerializerWithStringManifest {
  final val WriteLatestManifest     = classOf[PWriteLatest].getName
  final val PersistentValueManifest = classOf[PPersistentValue].getName
  final val ErasePathManifest       = classOf[PErasePath].getName

  override def identifier = 1500

  override def manifest(o: AnyRef) = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] = o match{
    case c: PWriteLatest     => c.toByteArray
    case c: PPersistentValue => c.toByteArray
    case c: PErasePath       => c.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case WriteLatestManifest      => PWriteLatest.parseFrom(bytes)
    case PersistentValueManifest  => PPersistentValue.parseFrom(bytes)
    case ErasePathManifest        => PErasePath.parseFrom(bytes)

  }
}
