package SensorDataStructure

import scala.concurrent.stm._ 
/*
  SensorMap
	  SensorNodes

  SensorNodes
	  Key: path part: /refrigerator123/
	  either STM::SensorData or STM::SensorMap
*/
abstract sealed class SensorNode
case class SensorData[T](
                        val path: String,
                        val unit: String,
                        val value: T
                      ) extends SensorNode
case class SensorMap extends SensorNode{
    val content : TMap[String, SensorNode] = TMap.empty[String, SensorNode];
    override def apply(path: String)) =
    {
      val spl = path.split("/");
      val key = spl.head;
      val after = spl.tail;
      content(key) match {
          case node : SensorData => node
          case nodemap : SensorMap => nodemap(after)
      }
    }
}

