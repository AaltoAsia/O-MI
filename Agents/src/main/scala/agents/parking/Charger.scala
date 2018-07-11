package agents.parking

import scala.util.{Try, Failure, Success}
import types.odf._
import types._

case class Charger(
  id: String,
  brand: Option[String],
  model: Option[String],
  lidStatus: Option[String],
  currentInA: Option[Double],
  currentType: Option[String],
  powerInkW: Option[Double],
  voltageInV: Option[Double],
  threePhasedCurrentAvailable: Option[Boolean],
  isFastChargeCapable: Option[Boolean],
  plugs: Seq[Plug]
){
  def update( other: Charger ): Charger ={
    require( id == other.id )
    Charger(
      id,
      other.brand.orElse(brand),
      other.model.orElse(model),
      other.lidStatus.orElse(lidStatus),
      other.currentInA.orElse(currentInA),
      other.currentType.orElse(currentType),
      other.powerInkW.orElse(powerInkW),
      other.voltageInV.orElse(voltageInV),
      other.threePhasedCurrentAvailable.orElse(threePhasedCurrentAvailable),
      other.isFastChargeCapable.orElse(isFastChargeCapable),
      (this.plugs ++ other.plugs).groupBy(_.id).map{
        case (id, plugs: Seq[Plug] ) =>
          plugs.fold(Plug(id)){
            case (l: Plug, r: Plug) => l.update(r)
          }
      }.toSeq
    )
  }

  def toOdf(parentPath: Path): Seq[Node] = {
    val path: Path= parentPath / id
    Seq(
      Object( 
        Vector( QlmID( id)),
        path,
        typeAttribute = Some(Charger.mvType)
      )
    ) ++ brand.map{ b => 
      val nII = "brand"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( StringValue( b, currentTimestamp))
      )
    }.toSeq ++ model.map{ m => 
      val nII = "model"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( StringValue( m, currentTimestamp))
      )
    } ++ currentInA.map{ m => 
      val nII = "currentInA"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( m, currentTimestamp))
      )
    } ++ currentType.map{ m => 
      val nII = "currentType"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( StringValue( m, currentTimestamp))
      )
    } ++ powerInkW.map{ m => 
      val nII = "powerInkW"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( m, currentTimestamp))
      )
    } ++ voltageInV.map{ m => 
      val nII = "voltageInV"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( DoubleValue( m, currentTimestamp))
      )
    } ++ threePhasedCurrentAvailable.map{ m => 
      val nII = "threePhasedCurrentAvailable"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( BooleanValue( m, currentTimestamp))
      )
    } ++ isFastChargeCapable.map{ m => 
      val nII = "isFastChargeCapable"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"mv:$nII"),
        values = Vector( BooleanValue( m, currentTimestamp))
      )
      } ++ plugs.flatMap{ p => p.toOdf(path)}
  }
}

object Charger{
  def mvType = "mv:Charger"
  def parseOdf(path: Path, odf: ImmutableODF): Try[Charger] ={
    Try{
      odf.get(path) match{
        case Some(obj: Object) if obj.typeAttribute.contains(mvType) =>
          val plugs: Seq[Plug] = odf.get( path / "Plugs").map{
            case obj: Object => 
              val ( success, fails ) = odf.getChilds( path / "Plugs").map{
                case node: Node => Plug.parseOdf(node.path,odf)
              }.partition{ 
                case Success(_) => true
                case Failure(_) => false
              }
              if( fails.nonEmpty ){
                throw ParseError.combineErrors(
                  fails.map{
                    case Failure( pe: ParseError ) => pe
                    case Failure( e ) => throw e
                  }
                )
                
              } else {
                success.collect{
                  case Success( plug: Plug ) => plug
                }
              }

            case obj: Node => 
              throw MVError( s"Plugs path $path/Plugs should be Object.")
          }.toSeq.flatten
          Charger(
            path.last,
            getStringOption("brand",path,odf),
            getStringOption("model",path,odf),
            getStringOption("lidStatus",path,odf),
            getDoubleOption("currentInA",path,odf),
            getStringOption("currentType",path,odf),
            getDoubleOption("powerInkW",path,odf),
            getDoubleOption("voltageInV",path,odf),
            getBooleanOption("threePhasedCurrentAvailable",path,odf),
            getBooleanOption("isFastChargeCopable",path,odf),
            plugs
          )
        case Some(obj: Object) => 
          throw MVError( s"Charger path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"Charger path $path should be Object with type $mvType")
        case None => 
          throw MVError( s"Charger path $path not found from given O-DF")
      }
    }
  }
}
