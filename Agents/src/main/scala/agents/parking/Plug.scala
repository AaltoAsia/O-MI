package agents.parking

import scala.util.Try
import types.odf._
import types._

case class Plug(
  id: String,
  plugType: Option[String] = None,
  currentInA: Option[Double] = None,
  currentType: Option[String] = None,
  powerInkW: Option[Double] = None,
  voltageInV: Option[Double] = None,
  threePhasedCurrentAvailable: Option[Boolean] = None,
  isFastChargeCapable: Option[Boolean] = None
){

  def update( other: Plug ): Plug ={
    require( id == other.id)
    Plug(
      id,
      other.plugType.orElse(plugType),
      other.currentInA.orElse(currentInA),
      other.currentType.orElse(currentType),
      other.powerInkW.orElse(powerInkW),
      other.voltageInV.orElse(voltageInV),
      other.threePhasedCurrentAvailable.orElse(threePhasedCurrentAvailable),
      other.isFastChargeCapable.orElse(isFastChargeCapable)
    )
  }
  def toOdf(parentPath: Path): Seq[Node] = {
    val path: Path= parentPath / id
    Seq(
      Object( 
        Vector( QlmID( id)),
        path,
        typeAttribute = Some(Plug.mvType)
      )
    ) ++ plugType.map{ m => 
      val nII = "plugType"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( Value( m, "mv:PlugType", currentTimestamp ))
      )
    } ++ currentInA.map{ m => 
      val nII = "currentInA"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( m, currentTimestamp ))
      )
    } ++ currentType.map{ m => 
      val nII = "currentType"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( StringValue( m, currentTimestamp ))
      )
    } ++ powerInkW.map{ m => 
      val nII = "powerInkW"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( m, currentTimestamp ))
      )
    } ++ voltageInV.map{ m => 
      val nII = "voltageInV"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( m, currentTimestamp ))
      )
    } ++ threePhasedCurrentAvailable.map{ m => 
      val nII = "threePhasedCurrentAvailable"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( BooleanValue( m, currentTimestamp ))
      )
    } ++ isFastChargeCapable.map{ m => 
      val nII = "isFastChargeCapable"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( BooleanValue( m, currentTimestamp ))
      )
      } 
  }
}

object Plug{
  def mvType = "mv:Plug"
  def parseOdf(path: Path, odf: ImmutableODF): Try[Plug] ={
    Try{
      odf.get(path) match{
        case Some(obj: Object) if obj.typeAttribute.contains(mvType) =>
          Plug(
            obj.path.last,
            getStringOption("plugType",path,odf),
            getDoubleOption("currentInA",path,odf),
            getStringOption("currentType",path,odf),
            getDoubleOption("powerInkW",path,odf),
            getDoubleOption("voltageInV",path,odf),
            getBooleanOption("threePhasedCurrentAvailable",path,odf),
            getBooleanOption("isFastChargeCopable",path,odf)
          )
        case Some(obj: Object) => 
          throw MVError( s"Plug path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"Plug path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"Plug path $path not found from given O-DF")
      }
    }
  }
}
