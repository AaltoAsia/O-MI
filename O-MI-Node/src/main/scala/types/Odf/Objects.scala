package types
package odf

import scala.language.implicitConversions

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
  /**
    * true if first is greater or equeal version number
    */
  private def vcomp(x:String, y:String): Boolean =
    (x.split("\\.") zip y.split("\\."))
      .foldLeft(true){
        case (true, (a,b)) if a >=b => true; case _ => false
      }

  def intersection( that: Objects ) : Objects ={
    Objects(
      (this.version, that.version) match {
        case (o@Some(oldv), n@Some(newv)) =>
          if (vcomp(oldv, newv)) n
          else o
        case _ => this.version orElse that.version
      },
      that.attributes ++ attributes
    )
  }
  def union( that: Objects ) : Objects ={
    Objects(
      (this.version, that.version) match {
        case (o@Some(oldv), n@Some(newv)) =>
          if (vcomp(oldv, newv)) o
          else n
        case _ => optionUnion(this.version,that.version)
      },
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
