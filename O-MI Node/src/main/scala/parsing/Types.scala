package parsing

abstract sealed trait ParseMsg

/** case class that represents parsing error
 *  @param msg error message that describes the problem.
 */
case class ParseError(msg: String) extends ParseMsg
case class OneTimeRead( ttl: String,
                        sensors: Seq[ OdfNode],
                        begin: String,
                        end: String,
                        newest: String,
                        oldest: String,
                        callback: String,
                        requstId: Seq[ String]
                      ) extends ParseMsg
case class Write( ttl: String,
                  sensors: Seq[OdfNode],
                  callback: String,
                  requstId: Seq[ String]
                ) extends ParseMsg
case class Subscription(  ttl: String,
                          interval: String,
                          sensors: Seq[OdfNode],
                          begin: String,
                          end: String,
                          newest: String,
                          oldest: String,
                          callback: String,
                          requstId: Seq[ String]
                        ) extends ParseMsg
case class Result(  returnValue: String,
                    returnCode: String,
                    parseMsgOp: Option[ Seq[ OdfNode] ],
                    callback: String,
                    requstId: Seq[ String]
                  ) extends ParseMsg
case class Cancel(  ttl: String,
                    requstId: Seq[ String]
                  ) extends ParseMsg

/*
trait ODFNodeType
case object NodeObject extends ODFNodeType 
case object InfoItem extends ODFNodeType   
case object MetaData extends ODFNodeType   
*/

/** case class that represents an node in ther O-DF
 *  
 *  @param path path to the node as a String e.g. "/Objects/SmartHouse/SmartFridge/PowerConsumption"
 *  @param ODFNodeType type of node can be NodeObject, InfoItem or MetaData
 *  @param value contains the calue if one exists e.g. InfoItem "PowerOn" might contain Some(1) or Some(0) as value
 *  @param time contains the timestamp with format if the node contains one
 *  @param metadata InfoItem may contain optional metadata, 
 *         metadata can contain e.g. value type, units or similar information
 */
/*
case class ODFNode( path: String, nodeType: ODFNodeType, value: Option[String], time: Option[String], metadata: Option[String])
*/
case class TimedValue(time: String, value: String)

abstract sealed trait OdfNode
case class OdfInfoItem( path: Seq[ String],
                        timedValues: Seq[ TimedValue],
                        metadata: String 
                      ) extends OdfNode
case class OdfObject( path: Seq[String],
                      childs: Seq[OdfObject],
                      sensors: Seq[OdfInfoItem],
                      metadata: String
                    ) extends OdfNode

