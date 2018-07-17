
package agents.parking
object UserGroup extends Enumeration{
  type UserGroup = Value
  val CarsharingUsers, PersonsWithDisabledParkingPermit, TaxiDrivers, Women, Inhabitants, Families, Unknown = Value   
  def apply( str: String ): Option[UserGroup] ={ 
    val u = str.replace("mv:","")
    values.find( ug => ug.toString == u ) 
  }
  def mvType = "mv:UserGroup"
  def toMvType( v: UserGroup ): String = s"mv:$v"
}
