package types
package odf

import scala.collection.{ Seq, Map }

trait Node{
  def createAncestors: Seq[Node] 
  def createParent: Node 
  def attributes: Map[String,String]
  def path: Path
  def hasStaticData: Boolean 
}
