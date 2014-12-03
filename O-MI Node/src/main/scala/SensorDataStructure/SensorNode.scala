package SensorDataStructure

import scala.concurrent.stm._ 
/** Abstract base class for sensors' data atructures
  *
  * @param Path to were node is. Last part is kye for this.
  *
  */
abstract sealed class SensorNode(
                        val path: String
                      )
{
    def key = path.split("/").last
}
/** Data structure for storing sensor data. a leaf.
  * 
  * @tparam Numeric basic data type of actual sensor value
  * @param Path to were sensor is. Last part is kye for this.
  * @param SI unit
  *        Should be in UCUM format.
  *        Empty if unknown.
  * @param Actual value from sensor
  */
case class SensorData[T](
                        override val path: String,
                        val unit: String,
                        val value: T// is a basic numeric data type
                      ) extends SensorNode(path)
/** Data structure were sensors exist. a node.
  * 
  * @param Path to were sensor is. Last part is kye for this.
  */
case class SensorMap(override val path: String) extends SensorNode(path){
    val content : TMap[String, SensorNode] = TMap.empty[String, SensorNode];
    def get(pathTo: String) : Option[SensorNode]=
    {
      val spl = pathTo.split("/");
      val key = spl.head;
      val after = spl.tail;
      content.single.get(key) match {
          case Some(node : SensorData[_]) => Some(node); //_ is a basic numeric data type
          case Some(nodemap : SensorMap) => nodemap.get(after.mkString("/"));
          case _ => None;
      }
    }
}

