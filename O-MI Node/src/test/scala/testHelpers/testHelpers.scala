package testHelpers
import org.specs2.mutable._
import org.specs2.specification.{Step, Fragments}
import akka.testkit.TestKit
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.specs2.specification.Scope

trait BeforeAll extends Specification {
  override def map(fs: =>Fragments) = 
    Step(beforeAll) ^ fs

  protected def beforeAll()
}

abstract class Actors extends TestKit(ActorSystem("testsystem", ConfigFactory.parseString("""
  akka.loggers = ["akka.testkit.TestEventListener"]
  """))) with After with Scope{
    def after = system.shutdown()
  }