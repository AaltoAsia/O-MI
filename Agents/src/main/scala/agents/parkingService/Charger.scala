package agents.parkingService

import types.OdfTypes._
import types._
import parkingService._
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
     val plugO = obj.get( obj.path / "Plugs" ).collect{
       case cobj: OdfObject if cobj.typeValue.contains("list") => //Typevalue is Option! do not compare with ==
        cobj.objects.collect{
          case plugObj: OdfObject if plugObj.typeValue.contains("mv:Plug") =>
            PowerPlug(plugObj)
        }
     }.toVector.flatten
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
     val tpca = obj.get( obj.path / "threephasedCurrentAvailable" ).collect{
       case ii: OdfInfoItem =>
        getBooleanFromInfoItem( ii )
     }.flatten
     val iFCC = obj.get( obj.path / "isFastChargeCapable" ).collect{
       case ii: OdfInfoItem =>
        getBooleanFromInfoItem( ii )
     }.flatten

    Charger( 
      brand,
      model, 
      lidStatus, 
       current,
       currentType,
       powerInkW,
       voltageInV,
       tpca,
       iFCC,
      plugO 
    )
  }
}

//TODO: Multiple plugs.
case class Charger(
  brand: Option[String],
  model: Option[String],
  lidStatus: Option[String],
  currentInA: Option[Double],
  currentType: Option[String],
  powerInkW: Option[Double],
  voltageInV: Option[Double],
  threePhasedCurrentAvailable: Option[Boolean],
  isFastChargeCapable: Option[Boolean],
  plugs: Seq[PowerPlug]
){
  def validFor( requested: Charger ): Boolean={
    requested.brand.forall{ str: String => 
      brand.contains( str)
    } && requested.model.forall{ str: String => 
      brand.contains( str)
    } /*&& requested.plugs.exists{ rplug: PowerPlug =>
      this.plug.forall(_.validFor(rplug))
    }*/
  }
  def toOdf( parentPath: Path ): OdfObject ={
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
    val powerInkWII = powerInkW.map{ pT =>
      OdfInfoItem(
        chargerPath / "powerInkW",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:powerInkW" )
      )
    }.toVector
    val currentII = currentInA.map{ pT =>
      OdfInfoItem(
        chargerPath / "currentInA",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:currentInA" )
      )
    }.toVector
    val currentTypeII = currentType.map{ cT =>
      OdfInfoItem(
        chargerPath / "currentType",
        Vector( OdfValue( cT, currentTime ) ),
        typeValue = Some( "mv:currentType" )
      )
    }.toVector
    val voltageInVII = voltageInV.map{ pT =>
      OdfInfoItem(
        chargerPath / "voltageInV",
        Vector( OdfValue( pT, currentTime ) ),
        typeValue = Some( "mv:voltageInV" )
      )
    }.toVector
    val tpcaII = threePhasedCurrentAvailable.map{ tpca =>
      OdfInfoItem(
        chargerPath / "threePhasedCurrentAvailable",
        Vector( OdfValue( tpca, currentTime ) ),
        typeValue = Some( "mv:threePhasedCurrentAvailable" )
      )
    }.toVector
    val iFCCII = isFastChargeCapable.map{ iFCC=>
      OdfInfoItem(
        chargerPath / "isFastChargeCapable",
        Vector( OdfValue( iFCC, currentTime ) ),
        typeValue = Some( "mv:isFastChargeCapable" )
      )
    }.toVector
    OdfObject(
      Vector( OdfQlmID( "Charger" )),
      chargerPath,
      brandII ++ modelII ++
      currentII ++ 
      currentTypeII ++
      powerInkWII ++ 
      voltageInVII ++
      tpcaII ++ 
      iFCCII, 
      plugs.map( _.toOdf( chargerPath ) ).toVector ,
      typeValue = Some( "mv:Charger" )
    )
  }
}
