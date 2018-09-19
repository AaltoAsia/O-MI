package agents.parking

object UserGroup extends Enumeration{
  type UserGroup = Value
  val CarsharingUsers, PersonsWithDisabledParkingPermit, TaxiDrivers, Women, Inhabitants, Families, Unknown = Value   
  def apply( str: String, prefixes: Map[String,Set[String]] ): Option[UserGroup] ={ 
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      prefix: Set[String] => 
        prefix.map{
          str => if( str.endsWith(":") ) str else str + ":"
        }
    }.toSet.flatten
    val u = prefix.fold(str){
      case (v: String, prefix: String ) => v.replace(prefix,"")
    }
    values.find( ug => ug.toString == u ) 
  }
  def mvType = s"UserGroup"
  def toMvType( v: UserGroup, prefixes: Map[String,String]  ): String ={ 
    val prefix = prefixes.get("http://www.schema.mobivoc.org/").map{
      str => if( str.endsWith(":") ) str else str + ":"
    }.getOrElse("")
    s"${prefix}$v"
  }
}
