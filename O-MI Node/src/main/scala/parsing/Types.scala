package parsing

/** absract trait that represent either error or request in the O-MI
  *
  */
abstract sealed trait ParseMsg

/** case class that represents parsing error
 *  @param msg error message that describes the problem.
 */
case class ParseError(msg: String) extends ParseMsg
case class OneTimeRead( ttl: String,
                        sensors: Seq[ OdfObject],
                        begin: String,
                        end: String,
                        newest: String,
                        oldest: String,
                        callback: String,
                        requstId: Seq[ String]
                      ) extends ParseMsg
case class Write( ttl: String,
                  sensors: Seq[ OdfObject],
                  callback: String,
                  requstId: Seq[ String]
                ) extends ParseMsg
case class Subscription(  ttl: String,
                          interval: String,
                          sensors: Seq[ OdfObject],
                          begin: String,
                          end: String,
                          newest: String,
                          oldest: String,
                          callback: String,
                          requstId: Seq[ String]
                        ) extends ParseMsg
case class Result(  returnValue: String,
                    returnCode: String,
                    parseMsgOp: Option[ Seq[ OdfObject] ],
                    callback: String,
                    requstId: Seq[ String]
                  ) extends ParseMsg
case class Cancel(  ttl: String,
                    requstId: Seq[ String]
                  ) extends ParseMsg


/** case classs that represnts a value of InfoItem in the O-DF
  *
  * @param optional timestamp when value was measured
  * @param measured value
  */
case class TimedValue(time: String, value: String)

/** absract trait that reprasents an node in the O-DF either Object or InfoItem
  *
  */
abstract sealed trait OdfNode

/** case class that represents an InfoItem in the O-DF
 *  
 *  @param path path to the InfoItem as a Seq[String] e.g. Seq("Objects","SmartHouse","SmartFridge","PowerConsumption")
 *  @param InfoItem's values found in xml structure, TimedValue.
 *  @param metadata Object may contain metadata, 
 *         metadata can contain e.g. value type, units or similar information
 */
case class OdfInfoItem( path: Seq[ String],
                        timedValues: Seq[ TimedValue],
                        metadata: String 
                      ) extends OdfNode
/** case class that represents an Object in the O-DF
 *  
 *  @param path path to the Object as a Seq[String] e.g. Seq("Objects","SmartHouse","SmartFridge")
 *  @param Object's childs found in xml structure.
 *  @param Object's InfoItems found in xml structure.
 *  @param metadata Object may contain metadata, 
 *         metadata can contain e.g. value type, units or similar information
 */
case class OdfObject( path: Seq[String],
                      childs: Seq[OdfObject],
                      sensors: Seq[OdfInfoItem],
                      metadata: String
                    ) extends OdfNode

