package types
package odf

import database.journal.PPersistentNode

import scala.collection.Seq

trait Node {
  def createAncestors: Iterable[Node]

  def createParent: Node

  def attributes: Map[String, String]

  def path: Path

  def hasStaticData: Boolean

  def persist: PPersistentNode.NodeType // = PersistentNode(path.toString,attributes)
}
