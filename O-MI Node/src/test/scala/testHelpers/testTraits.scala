package testHelpers
import org.specs2.mutable._
import org.specs2.specification.{Step, Fragments}

trait BeforeAll extends Specification {
  override def map(fs: =>Fragments) = 
    Step(beforeAll) ^ fs

  protected def beforeAll()
}