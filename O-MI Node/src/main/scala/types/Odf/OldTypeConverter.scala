package types
package odf

import types.OdfTypes._
object OldTypeConverter{
  def convertOdfObjects( odfObjects: OdfObjects ): ImmutableODF ={
    val objects = Objects(
      odfObjects.version
    )
    val objs = odfObjects.objects.flatMap{
      case odfObject: OdfObject => 
        convertOdfObject( odfObject )
    }
    ImmutableODF( Vector(objects) ++ objs )
  }
  def convertOdfObject( odfObject: OdfObject ): Seq[Node] ={
    var ids =odfObject.id.map{ id => convertOdfQlmID( id ) }
    if( !ids.map{_.id}.toSet.contains(odfObject.path.last) ){
      
      ids= ids ++ Vector( QlmID(
      odfObject.path.last
       ) )

    }
    val obj = Object(
      ids,
      convertPath( odfObject.path ),
      odfObject.typeValue,
      odfObject.description.map{
        des => convertOdfDescription(des)
      }.toVector
    )

    val iIs: Seq[InfoItem] = odfObject.infoItems.map{ iI => convertOdfInfoItem( iI )}
    val objects: Seq[Node] = odfObject.objects.flatMap{ iI => convertOdfObject( iI )}
    Vector(obj) ++ objects ++ iIs
  }
  def convertPath( p: types.Path ) : Path ={
    Path( p.toSeq )
  }
  def convertOdfDescription(des : OdfDescription) : Description ={
    Description(
      des.value,
      des.lang
    )
  }
  def convertOdfQlmID(
    id : OdfQlmID
  ) : QlmID ={
    QlmID(
      id.value,
      id.idType,
      id.tagType,
      id.startDate,
      id.endDate,
      id.attributes
    )
  }
  def convertOdfInfoItem( odfII: OdfInfoItem ) : InfoItem ={
    InfoItem(
      odfII.path.last,
      convertPath(odfII.path),
      odfII.typeValue,
      Vector(),
      odfII.description.map{
        des => convertOdfDescription(des)
      }.toVector,
      odfII.values.map{
        value => convertOdfValue( value )
      },
      odfII.metaData.map{
        md => convertOdfMetaData( md )
      }
    )
  }
  def convertOdfValue( odfValue: OdfValue[Any] ) : Value[Any] ={
    odfValue.value match{
      case odfObjects: OdfObjects =>
      Value(
        convertOdfObjects( odfObjects ),
        odfValue.timestamp,
        odfValue.attributes
      )
      case other: Any =>
      Value(
        odfValue.value,
        odfValue.typeValue,
        odfValue.timestamp,
        odfValue.attributes
      )
    }
  }

  def convertOdfMetaData( odfMD: OdfMetaData ): MetaData ={
    MetaData(
      odfMD.infoItems.map{
        ii => convertOdfInfoItem( ii )
      }.toVector
    )
  }
}
