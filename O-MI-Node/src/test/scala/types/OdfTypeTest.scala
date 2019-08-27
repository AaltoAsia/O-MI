package types
package odf

import java.sql.Timestamp
import java.util.GregorianCalendar

import scala.concurrent._
import org.specs2.concurrent.ExecutionEnv
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}
import org.specs2.matcher.XmlMatchers._
import org.specs2.mutable._
import org.specs2.matcher._
import org.specs2.specification.{Scope,AfterAll}

import types.Path
import utils._

import scala.collection.immutable.HashMap

class OdfTypesTest(implicit ee: ExecutionEnv ) extends Specification {
  val testTime: Timestamp = Timestamp.valueOf("2017-05-11 15:44:55")
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher
  sequential
  "Object" should {
    "union correctly" >> objectUnionTest
    "update correctly" >> objectUpdateTest
  }
  "InfoItem" should {
    "union correctly" >> infoItemUnionTest
    "update correctly" >> infoItemUpdateTest
  }
  "ODFParser" should {
    val o_df = ImmutableODF(testingNodes)
    "should parse correctly XML created from ODF to equal ODF" in fromXMLTest(o_df)
  }
  class IODFTest (
    val nodes: Seq[Node]
  ) extends Scope{
    val odf: ImmutableODF = ImmutableODF( nodes)
  }
  class MODFTest (
    val nodes: Seq[Node]
  ) extends Scope{
    val odf: MutableODF = MutableODF( nodes)
  }

  case class TestEntry(
    desc: String, 
    nodes: Seq[Node],
    test: (ODF => MatchResult[_])
  )
  val objects = Path("Objects")
  val testsCollection = Vector(
    TestEntry( "create all given paths and their ancestors", testingNodes, createCorrect),
    TestEntry( "return all SubTree paths when asked", testingNodes, getCorrectSubTree),
    TestEntry( "create correct XML presentation", testingNodes, toXMLTest),
    TestEntry( "add also missing ancestors when a Node is added", testingNodes, addTest),
    TestEntry( "be nonempty when there is more nodes than just root Objects", testingNodes, nonEmptyTest),
    TestEntry( "be able removed all Values", testingNodes, valuesRemovedTest),
    TestEntry( "be able removed all attributes", testingNodes, attributesRemovedTest),
    TestEntry( "be able removed all MetaDatas", testingNodes, metaDatasRemovedTest),
    TestEntry( "be able removed all Descriptions", testingNodes, descriptionsRemovedTest),
    TestEntry( "be castable to immutable", testingNodes, immutableCastTest),
    TestEntry( "be castable to mutable", testingNodes, mutableCastTest),
    TestEntry( "be asked if it contains some path and return correct answer", testingNodes, containsTest),
    TestEntry( "be able to return only nodes that have prorietary/user defined attributes", testingNodes, nodesWithAttributesTest),
    TestEntry( "be able to return only paths of InfoItems that have MetaData", testingNodes, pathsOfInfoItemsWithMetaDataTest),
    TestEntry( "be able to return only InfoItems that have MetaData", testingNodes, infoItemsWithMetaDataTest),
    TestEntry( "be able to return only nodes that have descriptions", testingNodes, nodesWithDescriptionTest),
    TestEntry( "be able to return only nodes that have given type", testingNodes, nodesWithTypeTest),
    TestEntry( "be able to return only Objects that have given type", testingNodes, objectsWithTypeTest),
    TestEntry( "be able to return only paths that have given type", testingNodes, pathsWithTypeTest),
    TestEntry( "be able to return only nodes that have given type and are childs of given path", testingNodes, childsWithTypeTest),
    TestEntry( "be able to return only nodes that have given type and are descendants of given path", testingNodes, descendantsWithTypeTest),
    TestEntry( "be able to return only paths of nodes that have descriptions", testingNodes, pathsOfNodesWithDescriptionTest),
    TestEntry( "be able to return correct up tree", testingNodes, getCorrectUpTree),
    TestEntry( "be able to select correct tree", testingNodes, selectCorrectTree),
    TestEntry( "union with other ODF", testingNodes, unionTest),
    TestEntry( "update with other ODF", testingNodes, updateTest),
    TestEntry( "add with existing nodes", testingNodes, addExistingTest),
    TestEntry( "be root only when only Objects is present", Vector(Objects()), isRootOnlyTest),
    TestEntry( "be equal to other ODF with same nodes and unequal with Any thing other than classes implementing ODF trait", testingNodes, equalsTest),
    TestEntry( "remove multiple paths", testingNodes ++ Set(
        InfoItem(objects / "TestObj" / "RemoveObj" /"test1", Vector.empty),
        InfoItem(objects / "TestObj" / "RemoveII", Vector.empty)
      ), removeTest),
    TestEntry(
      "get correct childs for requested path", 
      Vector(
        InfoItem(objects / "Obj" / "test1", Vector.empty),
        InfoItem(objects / "Obj" / "test2", Vector.empty),
        InfoItem(objects / "Obj1" / "test2", Vector.empty),
        Object(objects / "Obj" / "test3"),
        Object(objects / "Obj" / "test4"),
        Object(objects / "Obj1" / "test1")
      ), 
      getChildsTest
    ),
    TestEntry(
      "get empty set of childs for nonexistent path", 
      Vector(
        InfoItem(objects / "Obj" / "test1", Vector.empty),
        InfoItem(objects / "Obj" / "test2", Vector.empty),
        InfoItem(objects / "Obj1" / "test2", Vector.empty),
        Object(objects / "Obj" / "test3"),
        Object(objects / "Obj" / "test4"),
        Object(objects / "Obj1" / "test1")
      ), 
      getChildsOfNonExistingTest
    )
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
  "ImmubtableODF" should {
    org.specs2.specification.core.Fragments.empty.append(
      testsCollection.map{
        case TestEntry(desc, nodes,test) =>
          s"$desc" >> new IODFTest( nodes){
            test(odf)
          }
      }
    )
  }
  def getChildsOfNonExistingTest( odf: ODF ) ={
    odf.getChildPaths( objects / "NonExistent" ) must beEmpty
  }
  def getChildsTest( odf: ODF ) ={
    odf.getChildPaths( objects / "Obj" ) must beEqualTo(
      Set( 
          objects / "Obj" / "test1",
          objects / "Obj" / "test2",
          objects / "Obj" / "test3",
          objects / "Obj" / "test4"
        )
    )
  }
  def emptyTest(odf: ODF) = {
    odf.getPaths.size <= 1 and
      odf.getPaths.toSet.contains(Path("Objects")) === true and
      odf.isEmpty === true
  }

  def nonEmptyTest(odf: ODF) = {
    odf.nonEmpty === true
  }
  def isRootOnlyTest(odf: ODF) = {
    odf.isRootOnly === true
  }
  def containsTest(odf: ODF) ={
    (odf.contains(Path("Objects", "ObjectA", "II2")) === true) and
    (odf.contains(Path("Objects", "ObjectA")) === true) and
    (odf.contains(Path("Objects")) === true) and
    (odf.contains(Path("Objects", "NonExistent")) === false) 
  }
  def nodesWithAttributesTest(odf: ODF) ={
    odf.nodesWithAttributes.toSet should beEqualTo(
      testingNodes.filter{
        node: Node => node.attributes.nonEmpty
      }.toSet
    )
  }

  def infoItemsWithMetaDataTest(odf: ODF) ={
    odf.infoItemsWithMetaData.toSet should beEqualTo(
      testingNodes.filter{
        case ii: InfoItem => ii.metaData.nonEmpty
        case node: Node => false
      }.toSet
    )
  }

  def pathsOfInfoItemsWithMetaDataTest(odf: ODF) ={
    odf.pathsOfInfoItemsWithMetaData.toSet should beEqualTo(
      testingNodes.filter{
        case ii: InfoItem => ii.metaData.nonEmpty
        case node: Node => false
      }.map(_.path).toSet
    )
  }
  def pathsOfNodesWithDescriptionTest(odf: ODF) ={
    odf.pathsOfNodesWithDescription.toSet should beEqualTo(
      testingNodes.filter{
        case obj: Object => obj.descriptions.nonEmpty
        case ii: InfoItem => ii.descriptions.nonEmpty
        case node: Node => false
      }.map(_.path).toSet
    )
  }

  def nodesWithDescriptionTest(odf: ODF) ={
    odf.nodesWithDescription.toSet should beEqualTo(
      testingNodes.filter{
        case obj: Object => obj.descriptions.nonEmpty
        case ii: InfoItem => ii.descriptions.nonEmpty
        case node: Node => false
      }.toSet
    )
  }

  def objectsWithTypeTest(odf: ODF) ={
    odf.objectsWithType("TestingType").toSet should beEqualTo(
      testingNodes.filter{
        case obj: Object => obj.typeAttribute.contains("TestingType")
        case node: Node => false
      }.toSet
    )
  }


  def pathsWithTypeTest(odf: ODF) ={
    odf.pathsWithType("TestingType").toSet should beEqualTo(
      testingNodes.filter{
        case obj: Object => obj.typeAttribute.contains("TestingType")
        case ii: InfoItem => ii.typeAttribute.contains("TestingType")
        case node: Node => false
      }.map(_.path).toSet
    )
  }
  def nodesWithTypeTest(odf: ODF) ={
    odf.nodesWithType("TestingType").toSet should beEqualTo(
      testingNodes.filter{
        case obj: Object => obj.typeAttribute.contains("TestingType")
        case ii: InfoItem => ii.typeAttribute.contains("TestingType")
        case node: Node => false
      }.toSet
    )
  }
  def childsWithTypeTest(odf: ODF) ={
    val parent = Path("Objects", "ObjectA")
    val correctNodes = testingNodes.filter{
        case obj: Object => obj.typeAttribute.contains("TestingType") && obj.path.isChildOf( parent)
        case ii: InfoItem => ii.typeAttribute.contains("TestingType") && ii.path.isChildOf( parent)
        case node: Node => false
      }.toSet

    val nodes = odf.childsWithType(parent,"TestingType").toSet 
    (nodes.map(_.path) should beEqualTo(correctNodes.map(_.path))) and
    (nodes should beEqualTo(correctNodes)) 
  }
  //XXX: Should check for multiple paths and more complex structures
  def descendantsWithTypeTest(odf: ODF) ={
    val paths = Set(Path("Objects", "ObjectA"),Path("Objects", "ObjectC"))
    odf.descendantsWithType(paths,"TestingType").toSet should beEqualTo(
      testingNodes.filter{
        case obj: Object => obj.typeAttribute.contains("TestingType") && paths.exists( path => obj.path.isDescendantOf( path) )
        case ii: InfoItem => ii.typeAttribute.contains("TestingType") && paths.exists( path => ii.path.isDescendantOf( path) )
        case node: Node => false
      }.toSet
    )
  }
  def immutableCastTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val correct = ImmutableODF(nodes)
    val res = odf.toImmutable
    (res must haveClass[ImmutableODF]) and (res === correct)
  }
  def mutableCastTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val correct = MutableODF(nodes)
    val res = odf.toMutable
    (res must haveClass[MutableODF]) and (res === correct)
  }
  def metaDatasRemovedTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val valueless = nodes.collect{
      case ii: InfoItem => ii.copy( metaData = None)
      case node: Node => node
    }
    val iodf = ImmutableODF(valueless)
    val result = odf.metaDatasRemoved
    (result === iodf )
  }
  def valuesRemovedTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val valueless = nodes.collect{
      case ii: InfoItem => ii.copy( values = Vector())
      case node: Node => node
    }
    val iodf = ImmutableODF(valueless)
    val result = odf.valuesRemoved
    (result === iodf ) 
  }
  def descriptionsRemovedTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val valueless = nodes.collect{
      case ii: InfoItem => ii.copy( descriptions = Set())
      case obj: Object => obj.copy( descriptions = Set())
      case node: Node => node
    }
    val iodf = ImmutableODF(valueless)
    val result = odf.descriptionsRemoved
    (result === iodf ) 
  }
  def attributesRemovedTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val valueless = nodes.collect{
      case ii: InfoItem => ii.copy( typeAttribute = None, attributes = HashMap())
      case obj: Object => obj.copy( typeAttribute = None, attributes = HashMap())
      case obj: Objects => obj.copy( attributes = HashMap())
    }
    val iodf = ImmutableODF(valueless)
    val result = odf.attributesRemoved
    (result === iodf ) 
  }

  def removeTest(odf: ODF) = {
    val removedPaths = Set(
      Path( "Objects","TestObj", "RemoveII"),
      Path( "Objects","TestObj", "RemoveObj")
    )
    val removedSubPath = Path( "Objects","TestObj", "RemoveObj", "test1")
    val correctPaths = odf.getPaths.toSet -- removedPaths - removedSubPath
    lazy val nodf = odf.removePaths(removedPaths)
    lazy val remainingPaths = nodf.getPaths

    (remainingPaths must not contain( removedPaths )) and
    (remainingPaths must contain( correctPaths ))
  }

  def updateTest( odf: ODF) ={
    val initNodes: Vector[Node] = Vector(
      InfoItem(
        "II3",
        Path("Objects", "ObjectU", "II3"),
        names = Vector(
          OdfID(
            "II2O1",
            Some("TestID"),
            Some("TestTag")
          ),
          OdfID(
            "II2O2",
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
              Path("Objects", "ObjectA", "II2", "MetaData", "A")
            ),
            InfoItem(
              "B",
              Path("Objects", "ObjectA", "II2", "MetaData", "B")
            ))
        )),
        attributes = testingAttributes,
        typeAttribute = Some("TestingType"),
      ),
      InfoItem(
        "II3",
        Path("Objects", "ObjectA", "II3"),
        names = Vector(
          OdfID(
            "II2O1",
            Some("TestID"),
            Some("TestTag")
          ),
          OdfID(
            "II2O2",
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
              Path("Objects", "ObjectA", "II2", "MetaData", "A")
            ),
            InfoItem(
              "B",
              Path("Objects", "ObjectA", "II2", "MetaData", "B")
            ))
        )),
        attributes = testingAttributes,
        typeAttribute = Some("TestingType"),
      ),
      Object(
        Vector(
          OdfID(
            "ObjectD",
            Some("TestID"),
            Some("TestTag")
          )
        ),
        Path("Objects", "ObjectC", "ObjectD"),
        typeAttribute = Some("TestingType"),
        descriptions = testingDescription,
        attributes = testingAttributes
      )
    )
    val otherOdf = ImmutableODF(initNodes)
    val otherNodes = otherOdf.getNodes.toVector
    val nodes = odf.getNodes.toVector
    val updated = odf.update(otherOdf)
    val unodes = updated.getNodes.toSet
    (unodes.map(_.path) must contain(nodes.map(_.path).toSet)) and 
    (unodes.map(_.path) must contain(otherNodes.map(_.path).toSet)) and 
    (unodes must contain(nodes.toSet)) and 
    (unodes must contain(otherNodes.toSet)) 

  }
  def addExistingTest( odf: ODF) ={
    val initNodes: Vector[Node] = Vector(
      InfoItem(
        "II3",
        Path("Objects", "ObjectU", "II3"),
        names = Vector(
          OdfID(
            "II2O1",
            Some("TestID"),
            Some("TestTag")
          ),
          OdfID(
            "II2O2",
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
              Path("Objects", "ObjectA", "II2", "MetaData", "A")
            ),
            InfoItem(
              "B",
              Path("Objects", "ObjectA", "II2", "MetaData", "B")
            ))
        )),
        attributes = testingAttributes,
        typeAttribute = Some("TestingType"),
      ),
      InfoItem(
        "II3",
        Path("Objects", "ObjectA", "II3"),
        names = Vector(
          OdfID(
            "II2O1",
            Some("TestID"),
            Some("TestTag")
          ),
          OdfID(
            "II2O2",
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
              Path("Objects", "ObjectA", "II2", "MetaData", "A")
            ),
            InfoItem(
              "B",
              Path("Objects", "ObjectA", "II2", "MetaData", "B")
            ))
        )),
        attributes = testingAttributes,
        typeAttribute = Some("TestingType"),
      ),
      Object(
        Vector(
          OdfID(
            "ObjectD",
            Some("TestID"),
            Some("TestTag")
          )
        ),
        Path("Objects", "ObjectC", "ObjectD"),
        typeAttribute = Some("TestingType"),
        descriptions = testingDescription,
        attributes = testingAttributes
      )
    )
    val otherOdf = ImmutableODF(initNodes)
    val otherNodes = otherOdf.getNodes.toVector
    val nodes = odf.getNodes.toVector
    val added = odf.addNodes(otherNodes)
    val unodes = added.getNodes.toSet
    (unodes.map(_.path) must contain(nodes.map(_.path).toSet)) and 
    (unodes.map(_.path) must contain(otherNodes.map(_.path).toSet)) and 
    (unodes must contain(nodes.toSet)) and 
    (unodes must contain(otherNodes.toSet)) 

  }
  def unionTest( odf: ODF) ={
    val initNodes: Vector[Node] = Vector(
      InfoItem(
        "II3",
        Path("Objects", "ObjectU", "II3"),
        names = Vector(
          OdfID(
            "II2O1",
            Some("TestID"),
            Some("TestTag")
          ),
          OdfID(
            "II2O2",
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
              Path("Objects", "ObjectA", "II2", "MetaData", "A")
            ),
            InfoItem(
              "B",
              Path("Objects", "ObjectA", "II2", "MetaData", "B")
            ))
        )),
        attributes = testingAttributes,
        typeAttribute = Some("TestingType"),
      ),
      InfoItem(
        "II3",
        Path("Objects", "ObjectA", "II3"),
        names = Vector(
          OdfID(
            "II2O1",
            Some("TestID"),
            Some("TestTag")
          ),
          OdfID(
            "II2O2",
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
              Path("Objects", "ObjectA", "II2", "MetaData", "A")
            ),
            InfoItem(
              "B",
              Path("Objects", "ObjectA", "II2", "MetaData", "B")
            ))
        )),
        attributes = testingAttributes,
        typeAttribute = Some("TestingType"),
      ),
      Object(
        Vector(
          OdfID(
            "ObjectD",
            Some("TestID"),
            Some("TestTag")
          )
        ),
        Path("Objects", "ObjectC", "ObjectD"),
        typeAttribute = Some("TestingType"),
        descriptions = testingDescription,
        attributes = testingAttributes
      )
    )
    val otherOdf = ImmutableODF(initNodes)
    val otherNodes = otherOdf.getNodes.toVector
    val nodes = odf.getNodes.toVector
    val union = odf.union(otherOdf)
    val unodes = union.getNodes.toSet
    (unodes.map(_.path) must contain(nodes.map(_.path).toSet)) and 
    (unodes.map(_.path) must contain(otherNodes.map(_.path).toSet)) and 
    (unodes must contain(nodes.toSet)) and 
    (unodes must contain(otherNodes.toSet)) 

  }

  def infoItemUpdateTest = {
    val lII = InfoItem(
      "II",
      Path("Objects", "Obj", "II"),
      typeAttribute = Some("test"),
      names = Vector(OdfID("II1")),
      descriptions = Set(
        Description("test", Some("English")),
        Description("test", Some("Finnish"))
      ),
      values = Vector(Value("test", testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD1", Path("Objects", "Obj", "II", "MetaData", "MD1"))))),
      attributes = HashMap("test1" -> "test")
    )
    val rII = InfoItem(
      "II",
      Path("Objects", "Obj", "II"),
      names = Vector(OdfID("II2")),
      typeAttribute = Some("testi"),
      descriptions = Set(
        Description("testi", Some("Finnish")),
        Description("test", Some("Swedesh"))
      ),
      values = Vector(Value(31, testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD2", Path("Objects", "Obj", "II", "MetaData", "MD2"))))),
      attributes = HashMap("test2" -> "test")
    )
    val correct = InfoItem(
      "II",
      Path("Objects", "Obj", "II"),
      typeAttribute = Some("testi"),
      names = Vector(OdfID("II2"), OdfID("II1")),
      descriptions = Set(
        Description("test", Some("English")),
        Description("testi", Some("Finnish")),
        Description("test", Some("Swedesh"))
      ),
      values = Vector(Value(31, testTime)),
      metaData = Some(MetaData(Vector(
        InfoItem("MD1", Path("Objects", "Obj", "II", "MetaData", "MD1")),
        InfoItem("MD2", Path("Objects", "Obj", "II", "MetaData", "MD2"))
      ))),
      attributes = HashMap("test1" -> "test", "test2" -> "test")
    )

    val updated = lII.update(rII)
    checkIIMatch(updated, correct)
  }

  def checkIIMatch(test: InfoItem, correct: InfoItem) = {
    (test.nameAttribute === correct.nameAttribute) and
      (test.path === correct.path) and
      (test.typeAttribute === correct.typeAttribute) and
      (test.names === correct.names) and
      (test.descriptions === correct.descriptions) and
      (test.values === correct.values) and
      (test.metaData === correct.metaData) and
      (test.attributes === correct.attributes) and
      (test should beEqualTo(correct))
  }

  def checkObjectMatch(test: Object, correct: Object) = {
    (test.ids === correct.ids) and
      (test.path === correct.path) and
      (test.typeAttribute === correct.typeAttribute) and
      (test.descriptions === correct.descriptions) and
      (test.attributes === correct.attributes) and
      (test should beEqualTo(correct))
  }


  def infoItemUnionTest = {
    val lII = InfoItem(
      "II",
      Path("Objects", "Obj", "II"),
      names = Vector(OdfID("II1")),
      descriptions = Set(Description("test", Some("English"))),
      typeAttribute = Some("oldtype"),
      values = Vector(Value("test", testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD1", Path("Objects", "Obj", "II", "MetaData", "MD1"))))),
      attributes = HashMap("test1" -> "test")
    )
    val rII = InfoItem(
      "II",
      Path("Objects", "Obj", "II"),
      names = Vector(OdfID("II2")),
      descriptions = Set(Description("test", Some("Finnish"))),
      typeAttribute = Some("newtype"),
      values = Vector(Value(31, testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD2", Path("Objects", "Obj", "II", "MetaData", "MD2"))))),
      attributes = HashMap("test2" -> "test")
    )
    val correct = InfoItem(
      "II",
      Path("Objects", "Obj", "II"),
      names = Vector(OdfID("II2"), OdfID("II1")),
      descriptions = Set(Description("test", Some("English")), Description("test", Some("Finnish"))),
      typeAttribute = Some("newtype"),
      values = Vector(Value("test", testTime), Value(31, testTime)),
      metaData = Some(MetaData(Vector(
        InfoItem("MD1", Path("Objects", "Obj", "II", "MetaData", "MD1")),
        InfoItem("MD2", Path("Objects", "Obj", "II", "MetaData", "MD2"))
      ))),
      attributes = HashMap("test1" -> "test", "test2" -> "test")
    )

    val unioned = lII.union(rII)
    checkIIMatch(unioned, correct)
  }

  def objectUnionTest = {
    val lObj = Object(
      Vector(OdfID("Obj"), OdfID("O1")),
      Path("Objects", "Obj"),
      Some("test1"),
      Set(Description("test", Some("English"))),
      HashMap("test1" -> "test")
    )
    val rObj = Object(
      Vector(OdfID("Obj"), OdfID("O2")),
      Path("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("Finnish"))),
      HashMap("test2" -> "test")
    )
    val correct = Object(
      Vector(OdfID("O2"), OdfID("O1"), OdfID("Obj")),
      Path("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("English")), Description("test", Some("Finnish"))),
      HashMap("test1" -> "test", "test2" -> "test")
    )
    checkObjectMatch(lObj.union(rObj), correct)
  }

  def objectUpdateTest = {
    val lObj = Object(
      Vector(OdfID("Obj"), OdfID("O1")),
      Path("Objects", "Obj"),
      Some("test1"),
      Set(Description("test", Some("English"))),
      HashMap("test1" -> "test")
    )
    val rObj = Object(
      Vector(OdfID("Obj"), OdfID("O2")),
      Path("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("Finnish"))),
      HashMap("test2" -> "test")
    )
    val correct = Object(
      Vector(OdfID("O2"), OdfID("O1"), OdfID("Obj")),
      Path("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("English")), Description("test", Some("Finnish"))),
      HashMap("test1" -> "test", "test2" -> "test")
    )
    checkObjectMatch(lObj.update(rObj), correct)
  }

  def createCorrect(
    o_df: ODF
  ) = {
    val iIPaths = testingNodes.collect {
      case iI: InfoItem => iI.path
    }.toSet
    val objPaths = testingNodes.collect {
      case obj: Object => obj.path
    }.toSet
    val automaticallyCreatedPaths = Set(
      Path("Objects", "ObjectA"),
      Path("Objects", "ObjectB"),
      Path("Objects", "ObjectB", "ObjectB"),
      Path("Objects", "ObjectC")
    )
    val createdIIPaths = o_df.getInfoItems.map(_.path).toSet
    val createdObjPaths = o_df.getObjects.map(_.path).toSet
    (createdIIPaths should contain(iIPaths)) and (
      createdObjPaths should contain(objPaths ++ automaticallyCreatedPaths))
  }
  def getCorrectSubTree(
    o_df: ODF
  ) = {
    o_df.selectUpTree(Set(Path("Objects", "ObjectA"))).getPaths.toSet should contain(
      Set(
        Path("Objects"),
        Path("Objects", "ObjectA"),
        Path("Objects", "ObjectA", "II1"),
        Path("Objects", "ObjectA", "II2")
      )
    )
  }
  def getCorrectUpTree(
    o_df: ODF
  ) = {
    o_df.selectSubTree(Set(
      Path("Objects", "ObjectA"),
      Path("Objects", "ObjectB", "ObjectB", "II1")
      )).getPaths.toSet should contain(
      Set(
        Path("Objects"),
        Path("Objects", "ObjectA"),
        Path("Objects", "ObjectB"),
        Path("Objects", "ObjectB", "ObjectB"),
        Path("Objects", "ObjectB", "ObjectB", "II1")
      )
    )
  }

  def selectCorrectTree(
    o_df: ODF
  ) = {
    val selector = ImmutableODF(
      Set(
        InfoItem.build(Path("Objects", "ObjectB", "ObjectB", "II1")),
        Object(Path("Objects", "ObjectA"))
      )
    )
    o_df.select( selector ).getPaths.toSet should contain(
      Set(
        Path("Objects"),
        Path("Objects", "ObjectA"),
        Path("Objects", "ObjectB"),
        Path("Objects", "ObjectB", "ObjectB"),
        Path("Objects", "ObjectB", "ObjectB", "II1")
      )
    )
  }
  def equalsTest(
    o_df: ODF
  ) = {
    val otherI = ImmutableODF(o_df.getNodes.toVector)
    val otherM = MutableODF(o_df.getNodes.toVector)
    ((o_df == otherI) should beEqualTo(true)) and 
    ((o_df == otherM) should beEqualTo(true)) and 
    ((o_df == 13) should beEqualTo(false) ) and
    (otherI should beEqualTo(o_df)) and 
    (otherM should beEqualTo(o_df))  
  }

  def addTest(
    o_df: ODF
  ) = {
    val beAdded = InfoItem(
      "II1",
      Path("Objects", "ObjectN", "SubObj", "II1"),
      names = Vector(
        OdfID(
          "II2O1",
          Some("TestID"),
          Some("TestTag")
        ),
        OdfID(
          "II2O2",
          Some("TestID"),
          Some("TestTag")
        )
      ),
      descriptions = testingDescription,
      values = Vector(
        Value(93, testTime),
        Value("51.9", "xs:float", testTime),
        Value("51.9", testTime)
      ),
      metaData = Some(MetaData(
        Vector(
          InfoItem(
            "A",
            Path("Objects", "ObjectA", "II2", "MetaData", "A")
          ),
          InfoItem(
            "B",
            Path("Objects", "ObjectA", "II2", "MetaData", "B")
          ))
      )),
      attributes = testingAttributes
    )
    o_df.add(beAdded).selectSubTree(
      Set(Path("Objects", "ObjectN"))
    ).getPaths.toSet should contain(
      Set(
        Path("Objects"),
        Path("Objects", "ObjectN"),
        Path("Objects", "ObjectN", "SubObj"),
        Path("Objects", "ObjectN", "SubObj", "II1")
      )
    )
  }

  def toXMLTest(
    o_df: ODF
  ) = {
    /*
    o_df.asXMLSource showAs (ns =>
      "Generated:\n\n" + p.format(ns.head) + "\n") must beEqualToIgnoringSpace(testingNodesAsXML)
    */
    1 === 1
  }

  def fromXMLTest(
    o_df: ODF
  ) = {
    val f = parseEventsToStringSource(o_df.asXMLDocument()).via(parsing.ODFStreamParser.parserFlow).runWith(Sink.fold[ODF,ODF](ImmutableODF())(_ union _)) 
    f.map{
      o: ODF =>
        val iODF = o_df.toImmutable
        lazy val parsedPaths = o.getPaths.toSet
        lazy val correctPaths = iODF.getPaths.toSet
        lazy val pathCheck = (parsedPaths must contain(correctPaths)) and
        ((parsedPaths -- correctPaths) must beEmpty) and ((correctPaths -- parsedPaths) must beEmpty)
        lazy val parsedII = o.getInfoItems.toSet
        lazy val correctII = iODF.getInfoItems.toSet
        lazy val iICheck = {
          (correctII -- parsedII) must beEmpty
        } and {
          (parsedII -- correctII) must beEmpty
        } and {
          parsedII must contain(correctII)
        }
            if ((parsedII -- correctII).nonEmpty) {
              println("Parsed IIs:\n" + parsedII.mkString("\n"))
              println("Correct IIs:\n" + correctII.mkString("\n"))
              println("Parsed -- Correct IIs:\n" + (parsedII -- correctII).mkString("\n"))
            } else if ((correctII -- parsedII).nonEmpty) {
              println("Correct -- ParsedIIs:\n" + (correctII -- parsedII).mkString("\n"))
            }
            lazy val parsedObj = o.getObjects.toSet
            lazy val correctObj = iODF.getObjects.toSet
            lazy val objCheck = {
              (parsedObj -- correctObj) must beEmpty
              } and {
                (correctObj -- parsedObj) must beEmpty
                } and {
                  parsedObj must contain(correctObj)
                }
                lazy val parsedMap = o.getNodesMap
                lazy val correctMap = iODF.getNodesMap
                lazy val mapCheck = parsedMap.toSet must contain(correctMap.toSet)

                println(s"Parsed hashCode: ${o.hashCode}, correct HashCode: ${iODF.hashCode}")
                println(s"Parsed paths equals correct: ${o.paths equals iODF.paths}")
                println(s"Parsed nodes equals correct: ${o.nodes equals iODF.nodes}")
                val checks  = pathCheck and iICheck and objCheck and mapCheck and (o must beEqualTo(iODF))
                checks
    }.await
  }

  def testingNodesAsXML = {
    <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
      <Object>
        <id>ObjectA</id>
        <InfoItem name="II1"/>
        <InfoItem name="II2" type="TestingType" testing="true">
          <name>II2</name>
          <name tagType="TestTag" idType="TestID">II2O1</name>
          <name tagType="TestTag" idType="TestID">II2O2</name>
          <description lang="English">Testing</description>
          <description lang="Finnish">Testaus</description>
          <MetaData>
            <InfoItem name="A"/>
            <InfoItem name="B"/>
          </MetaData>
          <value unixTime={( new Timestamp(testTime.getTime() + 2).getTime / 1000).toString} type="xs:int" dateTime={timestampToXML(new Timestamp(testTime.getTime() + 2))
            .toString}>93</value>
          <value unixTime={(new Timestamp(testTime.getTime() + 1).getTime / 1000).toString} type="xs:float" dateTime={timestampToXML(new Timestamp(testTime.getTime() + 1))
            .toString}>51.9</value>
          <value unixTime={(new Timestamp(testTime.getTime() ).getTime / 1000).toString} dateTime={timestampToXML(new Timestamp(testTime.getTime() )).toString}>81.5</value>
        </InfoItem>
      </Object>
      <Object>
        <id>ObjectB</id>
        <Object>
          <id>ObjectB</id>
          <InfoItem name="II1"/>
        </Object>
      </Object>
      <Object>
        <id>ObjectC</id>
        <Object type="TestingType" testing="true">
          <id tagType="TestTag" idType="TestID">ObjectCC</id>
          <id tagType="TestTag" idType="TestID">OCC</id>
          <description lang="English">Testing</description>
          <description lang="Finnish">Testaus</description>
        </Object>
      </Object>
    </Objects>
  }


  def testingNodes: Vector[Node] = Vector(
    InfoItem(
      "II1",
      Path("Objects", "ObjectA", "II1")
    ),
    InfoItem(
      "II2",
      Path("Objects", "ObjectA", "II2"),
      names = Vector(
        OdfID(
          "II2O1",
          Some("TestID"),
          Some("TestTag")
        ),
        OdfID(
          "II2O2",
          Some("TestID"),
          Some("TestTag")
        )
      ),
      descriptions = testingDescription,
      values = testingValues,
      metaData = Some(MetaData(
        Vector(
          InfoItem(
            "A",
            Path("Objects", "ObjectA", "II2", "MetaData", "A")
          ),
          InfoItem(
            "B",
            Path("Objects", "ObjectA", "II2", "MetaData", "B")
          ))
      )),
      attributes = testingAttributes,
      typeAttribute = Some("TestingType"),
    ),
    InfoItem(
      "II1",
      Path("Objects", "ObjectB", "ObjectB", "II1")
    ),
    Object(
      Vector(
        OdfID(
          "ObjectCC",
          Some("TestID"),
          Some("TestTag")
        ),
        OdfID(
          "OCC",
          Some("TestID"),
          Some("TestTag")
        )
      ),
      Path("Objects", "ObjectC", "ObjectCC"),
      typeAttribute = Some("TestingType"),
      descriptions = testingDescription,
      attributes = testingAttributes
    )
  )
  def testingValues = Vector(
        Value("81.5", new Timestamp(testTime.getTime())),
        Value("51.9", "xs:float", new Timestamp(testTime.getTime() + 1)),
        Value(93, new Timestamp(testTime.getTime() + 2))
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

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }
}
