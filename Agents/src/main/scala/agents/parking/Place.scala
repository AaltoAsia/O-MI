
package agents.parking
trait Place{
  val geo: Option[GeoCoordinates]
  val address: Option[PostalAddress]
  def mvType = "schema:Place"
}
