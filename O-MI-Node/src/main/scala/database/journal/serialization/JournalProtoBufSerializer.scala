package database.journal.serialization

import akka.serialization.SerializerWithStringManifest
import database.journal.{ErasePath, PersistentValue, WriteLatest}

class JournalProtoBufSerializer extends SerializerWithStringManifest {
  final val WriteLatestManifest     = classOf[WriteLatest].getName
  final val PersistentValueManifest = classOf[PersistentValue].getName
  final val ErasePathManifest       = classOf[ErasePath].getName

  override def identifier = 1500

  override def manifest(o: AnyRef) = o.getClass.getName

  override def toBinary(o: AnyRef): Array[Byte] = o match{
    case c: WriteLatest     => c.toByteArray
    case c: PersistentValue => c.toByteArray
    case c: ErasePath       => c.toByteArray
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest match {
    case WriteLatestManifest      => WriteLatest.parseFrom(bytes)
    case PersistentValueManifest  => PersistentValue.parseFrom(bytes)
    case ErasePathManifest        => ErasePath.parseFrom(bytes)

  }
}
