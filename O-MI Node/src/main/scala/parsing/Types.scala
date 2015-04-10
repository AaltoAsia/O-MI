package parsing
import java.sql.Timestamp

/**
 * Contains Types that are returned by the parsers and useful traits
 * that can be used anywhere in the project.
 */
object Types {

  /**
   * Trait for subscription like classes. Offers a common interface for subscription types.
   */
  trait SubLike {
    // Note: defs can be implemented also as val and lazy val
    def interval: Double
    def ttl: Double
    def isIntervalBased  = interval >= 0.0
    def isEventBased = interval == -1
    def ttlToMillis: Long = (ttl * 1000).toLong
    def intervalToMillis: Long = (interval * 1000).toLong
  }

  /** absract trait that represent either error or request in the O-MI
    *
    */
  abstract sealed trait ParseMsg

  /**
   * Trait that represents any Omi request. Provides some data that are common
   * for all omi requests.
   */
  sealed trait OmiRequest {
    def ttl: Double
    def callback: Option[String]
    def hasCallback = callback.isDefined
  }

  /** case class that represents parsing error
   *  @param msg error message that describes the problem.
   */
  case class ParseError(msg: String) extends ParseMsg

  /** case class that represents Read request without interval, aka. One-Time-Read request,
   *  @param ttl Time-To-Live in seconds.
   *  @param senosrs Sensors to be readen.
   *  @param begin Optional. Values measured after begin's Timestamp are requested.
   *         Defines begin of Timeframe. If not given, but end is given,
   *         oldest value's timestamp is expected value. If neither of 
   *         end or begin is given, request will return only current value.
   *         Oldest and newest can affect the request.
   *  @param end Optional. Values measured before end Timestamp are requested. Defines
   *         end of Timeframe. If not given, current time is expected value. 
   *  @param newest Optional count of newest values request from timeframe.
   *  @param oldest Optional count of oldest values request from timeframe.
   *  @param callback Optional callback address were responses are send.
   *  @param requestId Optional value that indicates which older request answered.
   */
  case class OneTimeRead( ttl: Double,
                          sensors: Seq[ OdfObject],
                          begin: Option[Timestamp] = None,
                          end: Option[Timestamp] = None,
                          newest: Option[Int] = None,
                          oldest: Option[Int] = None,
                          callback: Option[String] = None,
                          requestId: Seq[ String] = Seq.empty
                        ) extends ParseMsg with OmiRequest

  /** Case class that represents Write request.
   *  @param ttl Time-To-Live in seconds.
   *  @param senosrs Objects to be writen.
   *  @param callback Optional callback address were responses are send.
   */
  case class Write( ttl: Double,
                    sensors: Seq[ OdfObject],
                    callback: Option[String] = None
                  ) extends ParseMsg with OmiRequest

  /** Case class that represents Read request with interval, aka. Subcription request.
   *  @param ttl Time-To-Live in seconds.
   *  @param interval Interval of responses in seconds.
   *  @param senosrs Sensors to be readen.
   *  @param callback Optional callback address were responses are send.
   *  @param requestId Optional value that indicates which older request answered.
   */
  case class Subscription(  ttl: Double,
                            interval: Double,
                            sensors: Seq[ OdfObject],
                            callback: Option[String] = None,
                            requestId: Seq[ String] = Seq.empty
                            ) extends ParseMsg with SubLike with OmiRequest

  /** Case class that represents Write request.
   *  @param ttl Time-To-Live in seconds.
   *  @param requestId RequestIds of older requests to be canceled.
   */
  case class Cancel(  ttl: Double,
                      requestId: Seq[ String]
                      ) extends ParseMsg with OmiRequest {

                        /** Note: Callbacks are not supported for Cancel */
                        val callback = None
                      }

  case class Result(  returnValue: String,
                      returnCode: String,
                      parseMsgOp: Option[ Seq[ OdfObject] ],
                      requestId: Seq[ String] = Seq.empty
                    ) extends ParseMsg


  /** case classs that represnts a value of InfoItem in the O-DF
    *
    * @param time optional timestamp when value was measured
    * @param value measured value
    */
  case class TimedValue( time: Option[Timestamp], value: String)

  /** case classs that represnts MetaData of InfoItem
    *
    * @param data xml MetaData node stored as string.
    */
  case class InfoItemMetaData(data: String)


  /** absract trait that reprasents an node in the O-DF either Object or InfoItem
    *
    */
  abstract sealed trait OdfNode


  /** case class that represents an InfoItem in the O-DF
   *  
   *  @param path path to the InfoItem as a Seq[String] e.g. Seq("Objects","SmartHouse","SmartFridge","PowerConsumption")
   *  @param timedValues InfoItem's values found in xml structure, TimedValue.
   *  @param metadata Object may contain metadata, 
   *         metadata can contain e.g. value type, units or similar information
   */
  case class OdfInfoItem( path: Path,
                          timedValues: Seq[ TimedValue],
                          metadata: Option[InfoItemMetaData] = None
                        ) extends OdfNode

  /** case class that represents an Object in the O-DF
   *  
   *  @param path path to the Object as a Seq[String] e.g. Seq("Objects","SmartHouse","SmartFridge")
   *  @param childs Object's childs found in xml structure.
   *  @param sensors Object's InfoItems found in xml structure.
   */
  case class OdfObject( path: Path,
                        childs: Seq[OdfObject],
                        sensors: Seq[OdfInfoItem]
                      ) extends OdfNode

  type  OdfParseResult = Either[ParseError, OdfObject]

  /**
   * Path is a wrapper for Seq[String] representing path to an O-DF Object
   * It abstracts path seperators ('/') from error prone actions such as joining
   * two paths or creating new Paths from user input.
   * Path can be used as a sequence via an implicit conversion or _.toSeq
   */
  class Path(pathSeq: Seq[String]){
    import Path._
    /**
     * Removes extra path elements and holds the Path as Seq[String]
     */
    val toSeq = {
      val normalized = pathSeq.filterNot(_ == "")
      normalized.toSeq
    }

    def this(pathStr: String) = this{
      pathStr.split("/")
    }

    /**
     * Join two paths.
     * @param otherPath other path to join at the end of this one
     * @return new path with other joined to this path
     */
    def /(otherPath: Path): Path = Path(this ++ otherPath)

    def /(otherPathStr: String): Path = {
      this / Path(otherPathStr)
    }
    override def equals(that: Any): Boolean = that match{
      case thatPath: Path => thatPath.toSeq.equals(this.toSeq)
      case _ => false
    }

    /**
     * Creates a path string which represents this path with '/' seperators.
     * Representation doesn't start nor end with a '/'.
     */
    override def toString: String = this.mkString("/")
  }

  /** Helper object for Path, contains implicit conversion between Path and Seq[String]
    */
  object Path {
    def apply(pathStr: String): Path = new Path(pathStr)
    def apply(pathSeq: Seq[String]): Path = new Path(pathSeq)
    val empty = new Path(Seq.empty)

    import scala.language.implicitConversions // XXX: maybe a little bit stupid place for this

    implicit def PathAsSeq(p: Path): Seq[String] = p.toSeq
    implicit def SeqAsPath(s: Seq[String]): Path = Path(s)
  }
}
