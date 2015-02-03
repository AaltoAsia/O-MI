package parsing

abstract sealed trait ParseMsg

/** case class that represents parsing error
 *  @param msg error message that describes the problem.
 */
case class ParseError(msg: String) extends ParseMsg
case class OneTimeRead(ttl: String, begin: Option[java.sql.Timestamp], end: Option[java.sql.Timestamp],
    sensors: Seq[ODFNode]) extends ParseMsg
case class Write(ttl: String, sensors: Seq[ODFNode]) extends ParseMsg
case class Subscription(ttl: String, interval: String, sensors: Seq[ODFNode]) extends ParseMsg
case class Result(value: String, parseMsgOp: Option[Seq[ODFNode]]) extends ParseMsg

trait ODFNodeType
case object NodeObject extends ODFNodeType 
case object InfoItem extends ODFNodeType   
case object MetaData extends ODFNodeType   

/** case class that represents an node in the O-DF
 *  
 *  @param path path to the node as a String e.g. "/Objects/SmartHouse/SmartFridge/PowerConsumption"
 *  @param ODFNodeType type of node can be NodeObject, InfoItem or MetaData
 *  @param value contains the calue if one exists e.g. InfoItem "PowerOn" might contain Some(1) or Some(0) as value
 *  @param time contains the timestamp with format if the node contains one
 *  @param metadata InfoItem may contain optional metadata, 
 *         metadata can contain e.g. value type, units or similar information
 */
case class ODFNode( path: String, nodeType: ODFNodeType, value: Option[String], time: Option[String], metadata: Option[String])


/**
 * Path is a wrapper for Seq[String] representing path to an O-DF Object
 * It abstracts path seperators ('/') from error prone actions such as joining
 * two paths or creating new Paths from user input.
 * Path can be used as a sequence via an implicit conversion or _.toSeq
 */
class Path(pathSeq: Seq[String]){
  import Path._
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

  /**
   * Creates a path string which represents this path with '/' seperators.
   * Representation doesn't start nor end with a '/'.
   */
  override def toString: String = this.mkString("/")
}


object Path {
  def apply(pathStr: String): Path = new Path(pathStr)
  def apply(pathSeq: Seq[String]): Path = new Path(pathSeq)

  import scala.language.implicitConversions // XXX: maybe a little bit stupid place for this

  implicit def PathAsSeq(p: Path): Seq[String] = p.toSeq
}
