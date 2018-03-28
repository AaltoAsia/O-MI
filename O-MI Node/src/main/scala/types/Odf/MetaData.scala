package types
package odf

import scala.collection.immutable.{ Set, HashSet }
import parsing.xmlGen.xmlTypes.MetaDataType

object MetaData{
  def empty: MetaData = MetaData(Vector.empty)
}

case class MetaData(
                     infoItems: Vector[InfoItem] = Vector.empty
) extends Unionable[MetaData] {
  def isEmpty = infoItems.isEmpty
  def nonEmpty = infoItems.nonEmpty
  lazy val nameToII: Map[String, InfoItem] = infoItems.map{ ii => ii.nameAttribute ->ii }.toMap
  lazy val names: Set[String] = infoItems.map{ ii => ii.nameAttribute }.toSet
  def update( that: MetaData ): MetaData ={
    
    val intersectingNames = names.intersect( that.names )
    val intersectedII = intersectingNames.flatMap {
      name: String =>
        (nameToII.get(name), that.nameToII.get(name)) match {
          case (Some(ii), Some(tii)) => Some(ii.update(tii))
          case (ii, tii) => tii.orElse(ii)
        }
    }
    val iis = ((names -- intersectingNames).flatMap {
        name: String =>
          nameToII.get(name)
      } ++
        (that.names -- intersectingNames).flatMap {
          name: String =>
            that.nameToII.get(name)
        } ++ intersectedII).toVector
    MetaData(
      iis.map{ ii => ii.copy( values = ii.values.sortBy(_.timestamp.getTime).headOption.toVector ) }
    )
  }
  def union( that: MetaData ): MetaData ={

    val intersectingNames = names.intersect( that.names )
    val intersectedII = intersectingNames.flatMap {
      name: String =>
        (nameToII.get(name), that.nameToII.get(name)) match {
          case (Some(ii), Some(tii)) => Some(ii.union(tii))
          case (ii, tii) => ii.orElse(tii)
        }
    }
    val iis = ((names -- intersectingNames).flatMap {
        name: String =>
          nameToII.get(name)
      } ++
        (that.names -- intersectingNames).flatMap {
          name: String =>
            that.nameToII.get(name)
        } ++ intersectedII).toVector
    MetaData(
      iis.map{ ii => ii.copy( values = ii.values.sortBy(_.timestamp.getTime).headOption.toVector )}
    )
  }
  implicit def asMetaDataType : MetaDataType = MetaDataType( infoItems.map(_.asInfoItemType) )
}
