package agents.parking

import agents.MVError
import types._
import types.odf._

import scala.util.Try

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
  def toOdf(parentPath: Path, prefixes: Map[String,String]): Seq[Node] = {
    import Plug.mvType
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }
    val path: Path= parentPath / id
    Seq(
      Object( 
        Vector( QlmID( id)),
        path,
        typeAttribute = Some(s"${mvType(prefix)}")
      )
    ) ++ plugType.map{ m => 
      val nII = "plugType"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( Value( m, s"${prefix}PlugType", currentTimestamp ))
      )
    } ++ currentInA.map{ m => 
      val nII = "currentInA"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( DoubleValue( m, currentTimestamp ))
      )
    } ++ currentType.map{ m => 
      val nII = "currentType"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( StringValue( m, currentTimestamp ))
      )
    } ++ powerInkW.map{ m => 
      val nII = "powerInkW"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( DoubleValue( m, currentTimestamp ))
      )
    } ++ voltageInV.map{ m => 
      val nII = "voltageInV"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( DoubleValue( m, currentTimestamp ))
      )
    } ++ threePhasedCurrentAvailable.map{ m => 
      val nII = "threePhasedCurrentAvailable"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( BooleanValue( m, currentTimestamp ))
      )
    } ++ isFastChargeCapable.map{ m => 
      val nII = "isFastChargeCapable"
      InfoItem( 
        nII,
        path / nII,
        typeAttribute = Some(s"${prefix.getOrElse("")}$nII"),
        values = Vector( BooleanValue( m, currentTimestamp ))
      )
      } 
  }
}

object Plug{
  def mvType(  prefix: Option[String] )={
    val preStr = prefix.getOrElse("")
    s"${preStr}Plug"
  }
  def parseOdf(path: Path, odf: ImmutableODF, prefixes: Map[String,Set[String]]): Try[Plug] ={
    Try{
      val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
        prefix: Set[String] => 
          prefix.map{
            str => if( str.endsWith(":") ) str else str + ":"
          }
      }.toSet.flatten
      odf.get(path) match{
        case Some(obj: Object) if prefix.exists{
          prefix: String =>
          obj.typeAttribute.contains(mvType(Some(prefix)))
        } =>
          Plug(
            obj.path.last,
            getStringOption("plugType",path,odf),
            getDoubleOption("currentInA",path,odf),
            getStringOption("currentType",path,odf),
            getDoubleOption("powerInkW",path,odf),
            getDoubleOption("voltageInV",path,odf),
            getBooleanOption("threePhasedCurrentAvailable",path,odf),
            getBooleanOption("isFastChargeCapable",path,odf)
          )
        case Some(obj: Object) => 
          throw MVError( s"Plug path $path has wrong type attribute ${obj.typeAttribute}")
        case Some(obj: Node) => 
          throw MVError( s"Plug path $path should be Object with type ${mvType(None)}")
        case None => 
          throw MVError( s"Plug path $path not found from given O-DF")
      }
    }
  }
}
