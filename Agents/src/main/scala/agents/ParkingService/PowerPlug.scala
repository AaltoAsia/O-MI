package agents
package parkingService

import scala.math
import types.OdfTypes._
import types._

object PowerPlug{
  
  def apply( obj: OdfObject ) : PowerPlug ={

     val pt = obj.get( obj.path / "plugType" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val current = obj.get( obj.path / "currentInA" ).collect{
       case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
     }.flatten
     val currentType = obj.get( obj.path / "currentType" ).collect{
       case ii: OdfInfoItem =>
        getStringFromInfoItem( ii )
     }.flatten
     val powerInkW = obj.get( obj.path / "powerInkW" ).collect{
       case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
     }.flatten
     val voltageInV = obj.get( obj.path / "voltageInV" ).collect{
       case ii: OdfInfoItem =>
        getDoubleFromInfoItem( ii )
     }.flatten
     val tpca = obj.get( obj.path / "three-phasedCurrentAvailable" ).collect{
       case ii: OdfInfoItem =>
        getBooleanFromInfoItem( ii )
     }.flatten
     val iFCC = obj.get( obj.path / "isFastChargeCapable" ).collect{
       case ii: OdfInfoItem =>
        getBooleanFromInfoItem( ii )
     }.flatten
     PowerPlug(
       obj.path.last,
       pt,
       current,
       currentType,
       powerInkW,
       voltageInV,
       tpca,
       iFCC
     )
  }

}

case class PowerPlug(
  id: String,
  plugType: Option[String],
  currentInA: Option[Double],
  currentType: Option[String],
  powerInkW: Option[Double],
  voltageInV: Option[Double],
  threePhasedCurrentAvailable: Option[Boolean],
  isFastChargeCapable: Option[Boolean]
){
  def validFor( requested: PowerPlug ): Boolean={
    requested.plugType.forall{ str: String => 
      plugType.contains( str)
    } 
  }
  def toOdf( parentPath: Path ): OdfObject ={
    val plugPath = parentPath / OdfQlmID(id)
    val pTII = plugType.map{ pT =>
      OdfInfoItem(
        plugPath / "plugType",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:PlugType" )
      )
    }.toVector
    val powerInkWII = powerInkW.map{ pT =>
      OdfInfoItem(
        plugPath / "powerInkW",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:powerInkW" )
      )
    }.toVector
    val currentII = currentInA.map{ pT =>
      OdfInfoItem(
        plugPath / "currentInA",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:currentInA" )
      )
    }.toVector
    val currentTypeII = currentType.map{ cT =>
      OdfInfoItem(
        plugPath / "currentType",
        Vector( OdfValue( cT, currentTime ) ),
        typeValue = Some( "mv:currentType" )
      )
    }.toVector
    val voltageInVII = voltageInV.map{ pT =>
      OdfInfoItem(
        plugPath / "voltageInV",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:voltageInV" )
      )
    }.toVector
    val tpcaII = threePhasedCurrentAvailable.map{ tpca =>
      OdfInfoItem(
        plugPath / "three-phasedCurrentAvailable",
        Vector( OdfValue( tpca, currentTime ) ),
        typeValue = Some( "mv:three-phasedCurrentAvailable" )
      )
    }.toVector
    val iFCCII = isFastChargeCapable.map{ iFCC=>
      OdfInfoItem(
        plugPath / "isFastChargeCapable",
        Vector( OdfValue( iFCC, currentTime ) ),
        typeValue = Some( "mv:isFastChargeCapable" )
      )
    }.toVector

    OdfObject(
      Vector( OdfQlmID(id)),
      plugPath,
      pTII ++ 
      currentII ++ 
      currentTypeII ++
      powerInkWII ++ 
      voltageInVII ++
      tpcaII ++ 
      iFCCII, 
      typeValue = Some( "mv:Plug" )
    )

  }
}
