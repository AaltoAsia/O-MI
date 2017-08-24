package types
package odf

import scala.collection.immutable.{ Set, HashSet }
import parsing.xmlGen.xmlTypes.MetaDataType

case class MetaData(
  val infoItems: Vector[InfoItem] = Vector.empty 
) extends Unionable[MetaData] {
  lazy val nameToII: Map[String, InfoItem] = infoItems.map{ ii => ii.nameAttribute ->ii }.toMap
  lazy val names: Set[String] = infoItems.map{ ii => ii.nameAttribute }.toSet
  def update( that: MetaData ): MetaData ={
    
    val intersectingNames = names.intersect( that.names )
    val intersectedII = intersectingNames.flatMap{
      case name: String =>
        ( nameToII.get(name), that.nameToII.get(name) ) match {
          case (Some( ii ), Some( tii) ) => Some( ii.update(tii) )
          case (ii,tii) =>  tii.orElse(ii)
        }
    }
    new MetaData( 
      ((names -- intersectingNames).flatMap{
        case name: String => 
          nameToII.get(name)
      } ++
      (that.names -- intersectingNames).flatMap{
        case name: String => 
          that.nameToII.get(name)
      } ++ intersectedII).toVector
    )
  }
  def union( that: MetaData ): MetaData ={

    val intersectingNames = names.intersect( that.names )
    val intersectedII = intersectingNames.flatMap{
      case name: String =>
        ( nameToII.get(name), that.nameToII.get(name) ) match {
          case (Some( ii ), Some( tii) ) => Some( ii.union(tii) )
          case (ii,tii) =>  ii.orElse(tii)
        }
    }
    new MetaData( 
      ((names -- intersectingNames).flatMap{
        case name: String => 
          nameToII.get(name)
      } ++
      (that.names -- intersectingNames).flatMap{
        case name: String => 
          that.nameToII.get(name)
      } ++ intersectedII).toVector
    )
  }
  implicit def asMetaDataType : MetaDataType = MetaDataType( infoItems.map(_.asInfoItemType ).toSeq )
}
