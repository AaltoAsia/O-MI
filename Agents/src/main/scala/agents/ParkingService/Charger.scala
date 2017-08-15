package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

object Charger{

  def apply( obj: OdfObject ) : Charger ={
     val name = obj.id.headOption
     val brand = obj.get( obj.path / "Brand" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val model = obj.get( obj.path / "Model" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val lidStatus = obj.get( obj.path / "LidStatus" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val plugO = obj.get( obj.path / "Plug" ).collect{
       case cobj: OdfObject =>
         PowerPlug( cobj )
     }

    Charger( brand, model, lidStatus, plugO )
  }
}
case class Charger(
  brand: Option[String],
  model: Option[String],
  lidStatus: Option[String],
  plug: Option[PowerPlug]
){
  def toOdf( parentPath: Path ) ={
    val chargerPath = parentPath / "Charger"
    val brandII = brand.map{
      str => 
        OdfInfoItem(
          chargerPath / "Brand",
          Vector( OdfValue( str, currentTime )),
          typeValue = Some( "mv:Brand" )
        )
    }.toVector
    val modelII = model.map{
      str => 
        OdfInfoItem(
          chargerPath / "Model",
          Vector( OdfValue( str, currentTime )),
          typeValue = Some( "mv:Model" )
        )
    }
    val lidStatusII = lidStatus.map{
      str => 
        OdfInfoItem(
          chargerPath / "LidStatus",
          Vector( OdfValue( str, currentTime ))
        )
    }
    OdfObject(
      Vector( QlmID( "Charger" )),
      chargerPath,
      brandII ++ modelII,
      plug.map( _.toOdf( chargerPath ) ).toVector ,
      typeValue = Some( "mv:Charger" )
    )
  }
}
