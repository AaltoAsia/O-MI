package types
package odf

import java.sql.Timestamp
import java.util.GregorianCalendar

import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}
import org.specs2.matcher.XmlMatchers._
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.specification.{Scope,AfterAll}

import types.OdfTypes._
import types.{Path => OdfPath}

import scala.collection.immutable.HashMap

class MutableODFTest extends Specification {
  val defaultTime: Timestamp = Timestamp.valueOf("2017-05-11 15:44:55")
  sequential
  class MODFTest (
    val nodes: Seq[Node]
  ) extends Scope{
    val odf: MutableODF = MutableODF( nodes)
  }

  case class TestEntry(
    desc: String, 
    nodes: Seq[Node],
    test: (MutableODF => MatchResult[_])
  )
  val objects = OdfPath("Objects")
  val testsCollection = Vector(
    TestEntry( "update O-DF with other O-DF", testingNodes(), testTest)
  )
  "MubtableODF" should {
    org.specs2.specification.core.Fragments.empty.append(
      testsCollection.map{
        case TestEntry(desc, nodes,test) =>
          s"$desc" >> new MODFTest( nodes){
            test(odf)
          }
      }
    )
   
  }
  def testTest(odf: MutableODF) ={
    val newTime: Timestamp = Timestamp.valueOf("2018-05-11 15:44:55")
    val correct = MutableODF(testingNodes(newTime)) 
    odf.update(correct)
    (odf should beEqualTo(correct)) 
  }
  def testingNodes(testTime:Timestamp = defaultTime): Vector[Node] = Vector(
    InfoItem(
      "II1",
      OdfPath("Objects", "ObjectA", "II1")
    ),
    InfoItem(
      "II2",
      OdfPath("Objects", "ObjectA", "II2"),
      names = Vector(
        QlmID(
          "II2O1",
          Some("TestID"),
          Some("TestTag")
        )
      ),
      descriptions = testingDescription,
      values = Vector(
        Value(93, testTime),
        Value("51.9", "xs:float", testTime),
        Value("81.5", testTime)
      ),
      metaData = Some(MetaData(
        Vector(
          InfoItem(
            "A",
            OdfPath("Objects", "ObjectA", "II2", "MetaData", "A")
          ),
          InfoItem(
            "B",
            OdfPath("Objects", "ObjectA", "II2", "MetaData", "B")
          ))
      )),
      attributes = testingAttributes,
      typeAttribute = Some("TestingType"),
    ),
    InfoItem(
      "II1",
      OdfPath("Objects", "ObjectB", "ObjectB", "II1")
    ),
    Object(
      Vector(
        QlmID(
          "ObjectCC",
          Some("TestID"),
          Some("TestTag")
        )
      ),
      OdfPath("Objects", "ObjectC", "ObjectCC"),
      typeAttribute = Some("TestingType"),
      descriptions = testingDescription,
      attributes = testingAttributes
    )
  )

  def testingAttributes = Map {
    "testing" -> "true"
  }

  def testingDescription = Set(
    Description(
      "Testing",
      Some("English")
    ),
    Description(
      "Testaus",
      Some("Finnish")
    )
  )
}
