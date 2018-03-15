package types
package odf

import scala.collection.{ Seq, Map }
import scala.collection.immutable.HashMap 
import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes.{ObjectsType, ObjectType}

case class Objects(
                    version: Option[String] = None,
  attributes: Map[String,String] = HashMap.empty
) extends Node {
  val path: Path = new Path( "Objects")
  def hasStaticData: Boolean = attributes.nonEmpty 
  def update( that: Objects ) : Objects ={
    Objects( that.version.orElse(version), attributes ++ that.attributes)
  }
  def createParent: Node = {
    this
  }
  def createAncestors: Seq[Node] = {
    Vector()
  }
  def intersection( that: Objects ) : Objects ={
    Objects(
      that.version.orElse(version), //What if versions are differents?
      that.attributes ++ attributes
    )
  }
  def union( that: Objects ) : Objects ={
    Objects(
      version.orElse(that.version), //What if versions are differents?
      attributes ++ that.attributes
    )
  }
  implicit def asObjectsType( objects: Seq[ObjectType]) : ObjectsType ={
    ObjectsType(
      objects,
      attributes = attributesToDataRecord( attributes ) ++ version.map {
        version: String => "@version" -> DataRecord(version)
      }  
    )
  }
  def readTo(to: Objects ): Objects ={
    to.copy( 
      this.version.orElse(to.version),
      this.attributes ++ to.attributes
    )
  }
}
