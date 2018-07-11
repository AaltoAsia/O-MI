//from https://gist.github.com/gmalouf/51a8722b50f6a9d30404
/*
 spray testkit is compiled against older version of specs2 need to manually define specs2Interface
  */
package spray.testkit

import akka.http.scaladsl.testkit.TestFrameworkInterface
import org.specs2.execute.{Failure, FailureException}
import org.specs2.specification.core.{Fragments, SpecificationStructure}
import org.specs2.specification.create.DefaultFragmentFactory

trait Specs2Interface extends TestFrameworkInterface with SpecificationStructure {

  def failTest(msg: String) = {
    val trace = new Exception().getStackTrace.toList
    val fixedTrace = trace.drop(trace.indexWhere(_.getClassName.startsWith("org.specs2")) - 1)
    throw FailureException(Failure(msg, stackTrace = fixedTrace))
  }

  override def map(fs: ⇒ Fragments) = super.map(fs).append(DefaultFragmentFactory.step(cleanUp()))
}

trait NoAutoHtmlLinkFragments extends org.specs2.specification.dsl.ReferenceDsl {
  override def linkFragment(alias: String) = super.linkFragment(alias)

  override def seeFragment(alias: String) = super.seeFragment(alias)
}