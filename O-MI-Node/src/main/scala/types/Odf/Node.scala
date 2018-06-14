package types
package odf

import database.journal.PersistentNode

import scala.collection.Seq

trait Node{
  def createAncestors: Seq[Node] 
  def createParent: Node 
  def attributes: Map[String,String]
  def path: Path
  def hasStaticData: Boolean
  def persist: PersistentNode// = PersistentNode(path.toString,attributes)
}
