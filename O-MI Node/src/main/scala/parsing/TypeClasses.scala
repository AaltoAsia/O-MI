package parsing

abstract sealed trait ParseMsg
case class ParseError(msg: String) extends ParseMsg
case class OneTimeRead(ttl: String, sensors: Seq[ODFNode]) extends ParseMsg
case class Write(ttl: String, sensors: Seq[ODFNode]) extends ParseMsg
case class Subscription(ttl: String, interval: String, sensors: Seq[ODFNode]) extends ParseMsg
case class Result(value: String, parseMsgOp: Option[Seq[ODFNode]]) extends ParseMsg

trait ODFNodeType
case object NodeObject extends ODFNodeType 
case object InfoItem extends ODFNodeType   
case object MetaData extends ODFNodeType   

case class ODFNode( path: String, nodeType: ODFNodeType, value: Option[String], time: Option[String], metadata: Option[String])

