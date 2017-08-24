package types
package odf

import scala.collection.{ Seq, Map }
import scala.collection.immutable.{ HashMap, Map =>IMap}

import parsing.xmlGen.scalaxb.DataRecord
import parsing.xmlGen.xmlTypes.InfoItemType

object InfoItem{
  def apply(path: Path, values: Vector[Value[Any]] ): InfoItem ={
    InfoItem(
      path.last,
      path,
      values = values
    )
  }
  def apply(
    path: Path,
    typeAttribute: Option[String],
    names: Vector[QlmID],
    descriptions: Vector[Description],
    values: Vector[Value[Any]],
    metaData: Option[MetaData],
    attributes: IMap[String,String]
  ): InfoItem ={
    InfoItem(
      path.last,
      path,
      typeAttribute,
      names,
      descriptions,
      values,
      metaData,
      attributes
    )
  }
}

case class InfoItem( 
  val nameAttribute: String,
  val path: Path,
  val typeAttribute: Option[String] = None,
  val names: Vector[QlmID] = Vector.empty,
  val descriptions: Vector[Description]= Vector.empty,
  val values: Vector[Value[Any]]= Vector.empty,
  val metaData: Option[MetaData] = None,
  val attributes: IMap[String,String] = HashMap.empty
) extends Node with Unionable[InfoItem]{
  assert( nameAttribute == path.last )
  def updateValues( vals: Vector[Value[Any]] ) = this.copy(values = vals)
  def update( that: InfoItem ): InfoItem={
    val pathsMatches = path == that.path
    assert( nameAttribute == that.nameAttribute && pathsMatches)
    InfoItem(
      nameAttribute,
      path,
      that.typeAttribute.orElse( typeAttribute ),
      QlmID.unionReduce( that.names ++ names).toVector.filter{ case id => id.id.nonEmpty},
      Description.unionReduce(that.descriptions ++ descriptions).toVector.filter{ case desc => desc.text.nonEmpty},
      if( that.values.nonEmpty ) that.values else values,
      that.metaData.flatMap{
        case md: MetaData => 
          metaData.map{
            case current: MetaData => 
              current update md 
          }.orElse( that.metaData )
      }.orElse( metaData ),
      if( that.attributes.nonEmpty ) attributes ++ that.attributes else attributes
    )

  }
  def intersection( that: InfoItem ): InfoItem ={
    val typeMatches = typeAttribute.forall{
      case typeStr: String => 
        that.typeAttribute.forall{
          case otherTypeStr: String => typeStr == otherTypeStr
        }
    }
    val pathsMatches = path == that.path
    assert( nameAttribute == that.nameAttribute && pathsMatches && typeMatches )
    new InfoItem(
      nameAttribute,
      path,
      that.typeAttribute.orElse( typeAttribute),
      if( that.names.nonEmpty ){
        QlmID.unionReduce( that.names ++ names).toVector.filter{ case id => id.id.nonEmpty}
      } else Vector.empty,
      if( that.descriptions.nonEmpty ){
        Description.unionReduce(that.descriptions ++ descriptions).toVector.filter{ case desc => desc.text.nonEmpty}
      } else Vector.empty,
      values,
      (metaData, that.metaData) match{
        case (Some( md ), Some( omd )) => Some( omd.union(md) )
        case ( Some(md), None) => Some( MetaData( Vector()))
        case (None, _) => None 
      },
      that.attributes ++ attributes 
    )
  }

  def union( that: InfoItem ): InfoItem ={
    val typeMatches = typeAttribute.forall{
      case typeStr: String => 
        that.typeAttribute.forall{
          case otherTypeStr: String => typeStr == otherTypeStr
        }
    }
    val pathsMatches = path == that.path
    assert( nameAttribute == that.nameAttribute && pathsMatches && typeMatches )
    new InfoItem(
      nameAttribute,
      path,
      typeAttribute,
      QlmID.unionReduce(names ++ that.names).toVector,
      Description.unionReduce(descriptions ++ that.descriptions).toVector,
      values ++ that.values,
      (metaData, that.metaData) match{
        case (Some( md ), Some( omd )) => Some( md.union(omd) )
        case (md,omd) => md.orElse(omd)
      },
      attributes ++ that.attributes
    )
  }
  def createAncestors: Seq[Node] = {
        path.getAncestors.collect{
          case ancestorPath: Path if ancestorPath.nonEmpty => 
            if( ancestorPath == Path("Objects")){
              Objects()
            } else {
              new Object(
                Vector(
                  new QlmID(
                    ancestorPath.last
                  )
                ),
              ancestorPath
            )
            }
        }.toVector
  }
  def createParent: Node = {
    val parentPath = path.init
    if( parentPath == new Path( "Objects") ){
      new Objects()
    } else {
      new Object(
        Vector(
          new QlmID(
            parentPath.last
          )
        ),
        parentPath
      )
    }
  }

  def asInfoItemType: InfoItemType = {
    InfoItemType(
      this.names.map{
        qlmid => qlmid.asQlmIDType
      },
      this.descriptions.map{ 
        case des: Description => 
          des.asDescriptionType 
      }.toVector,
      this.metaData.map(_.asMetaDataType).toSeq,
      //Seq(QlmIDType(path.lastOption.getOrElse(throw new IllegalArgumentException(s"OdfObject should have longer than one segment path: $path")))),
      this.values.map{ 
        value : Value[Any] => value.asValueType
      }.toSeq,
      HashMap(
        "@names" -> DataRecord(
          nameAttribute
        ),
        "@type" -> DataRecord(
          typeAttribute
        )
      ) ++ attributesToDataRecord( this.attributes )
    )
  }

  def hasStaticData: Boolean ={
    attributes.nonEmpty ||
    metaData.nonEmpty ||
    names.nonEmpty ||
    typeAttribute.nonEmpty ||
    descriptions.nonEmpty 
  }
}
