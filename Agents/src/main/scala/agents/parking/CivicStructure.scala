package agents.parking

trait CivicStructure extends Place{
  val openingHoursSpecifications: Seq[OpeningHoursSpecification]
  override def mvType = "mv:CivicStructure"
}
