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

class OdfTypesTest extends Specification {
  val testTime: Timestamp = Timestamp.valueOf("2017-05-11 15:44:55")
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
  "Converters" should {
    //TODO: What shoul be tested? Conversion may lose data.
    "convert from old to new and have same XML" in convertedOldHasSameXML
    "convert from new to old and have same XML" in convertedNewHasSameXML
    "Convert from old to new and back to old and stay the same" in repeatedOldConvertTest
    "Convert from new to old and back to new and stay the same" in repeatedNewConvertTest
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
  val objects = OdfPath("Objects")
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
      odf.getPaths.contains(OdfPath("Objects")) === true and
      odf.isEmpty === true
  }

  def nonEmptyTest(odf: ODF) = {
    odf.nonEmpty === true
  }
  def isRootOnlyTest(odf: ODF) = {
    odf.isRootOnly === true
  }
  def containsTest(odf: ODF) ={
    (odf.contains(OdfPath("Objects", "ObjectA", "II2")) === true) and
    (odf.contains(OdfPath("Objects", "ObjectA")) === true) and
    (odf.contains(OdfPath("Objects")) === true) and
    (odf.contains(OdfPath("Objects", "NonExistent")) === false) 
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
    val parent = OdfPath("Objects", "ObjectA")
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
    val paths = Set(OdfPath("Objects", "ObjectA"),OdfPath("Objects", "ObjectC"))
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
    val res = odf.immutable 
    (res must haveClass[ImmutableODF]) and (res === correct)
  }
  def mutableCastTest( odf: ODF ) ={
    val nodes = odf.getNodes
    val correct = MutableODF(nodes)
    val res = odf.mutable 
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

  def convertedNewHasSameXML = {
    val p = new scala.xml.PrettyPrinter(120, 4)
    val newType = ImmutableODF(testingNodes)
    val newTypeWithoutNamesForIIs = ImmutableODF(testingNodes.map {
      case obj: Objects => obj
      case obj: Object => obj.copy(descriptions = obj.descriptions.headOption.toSet)
      case ii: InfoItem => ii.copy(names = Vector(), descriptions = ii.descriptions.headOption.toSet)
    })
    val oldType = NewTypeConverter.convertODF(newType)
    oldType.asXML showAs {
      ns =>
        "Original:\n\n" + p.format(newTypeWithoutNamesForIIs.asXML.head) + "\n\n" ++
          "Converted:\n\n" + p.format(ns.head) + "\n"

    } must beEqualToIgnoringSpace(newTypeWithoutNamesForIIs.asXML)
  }

  def convertedOldHasSameXML = {
    val p = new scala.xml.PrettyPrinter(120, 4)
    val oldType: OdfObjects = parsing.OdfParser.parse(testingNodesAsXML.toString) match {
      case Right(o) => o
      case Left(errors: Seq[_]) =>
        println("PARSING FAILED:\n" + errors.mkString("\n"))
        throw new Exception("Parsing failed!")
    }
    val newType = OldTypeConverter.convertOdfObjects(oldType)
    newType.asXML showAs {
      ns =>
        "Original:\n\n" + p.format(oldType.asXML.head) + "\n\n" ++
          "Converted:\n\n" + p.format(ns.head) + "\n"

    } must beEqualToIgnoringSpace(oldType.asXML)
  }
  def removeTest(odf: ODF) = {
    val removedPaths = Set(
      OdfPath( "Objects","TestObj", "RemoveII"),
      OdfPath( "Objects","TestObj", "RemoveObj")
    )
    val removedSubPath = OdfPath( "Objects","TestObj", "RemoveObj", "test1")
    val correctPaths = odf.getPaths.toSet -- removedPaths - removedSubPath
    lazy val nodf = odf.removePaths(removedPaths)
    lazy val remainingPaths = nodf.getPaths

    (remainingPaths must not contain( removedPaths )) and
    (remainingPaths must contain( correctPaths ))
  }

  def repeatedNewConvertTest = {
    //val newType = ImmutableODF(testingNodes)
    val newTypeWithoutNamesForIIs = ImmutableODF(testingNodes.map {
      case obj: Objects => obj
      case obj: Object => obj.copy(descriptions = obj.descriptions.headOption.toSet)
      case ii: InfoItem => ii.copy(names = Vector(), descriptions = ii.descriptions.headOption.toSet)
    })
    val iODF = newTypeWithoutNamesForIIs
    val oldType = NewTypeConverter.convertODF(iODF)
    val backToNew = OldTypeConverter.convertOdfObjects(oldType)
    lazy val parsedOdfPaths = backToNew.getPaths.toSet
    lazy val correctOdfPaths = iODF.getPaths.toSet
    lazy val pathCheck = (parsedOdfPaths must contain(correctOdfPaths)) and
      ((parsedOdfPaths -- correctOdfPaths) must beEmpty) and ((correctOdfPaths -- parsedOdfPaths) must beEmpty)
    lazy val parsedII = backToNew.getInfoItems.toSet
    lazy val correctII = iODF.getInfoItems.toSet
    lazy val iICheck = {
      (parsedII -- correctII) must beEmpty
    } and {
      (correctII -- parsedII) must beEmpty
    } and {
      parsedII must contain(correctII)
    }

    lazy val parsedObj = backToNew.getObjects.toSet
    lazy val correctObj = iODF.getObjects.toSet
    lazy val objCheck = {
      (parsedObj -- correctObj) must beEmpty
    } and {
      (correctObj -- parsedObj) must beEmpty
    } and {
      parsedObj must contain(correctObj)
    }

    lazy val parsedMap = backToNew.getNodesMap
    lazy val correctMap = iODF.getNodesMap
    lazy val mapCheck = parsedMap.toSet must contain(correctMap.toSet)

    println(s"Convert hashCode: ${backToNew.hashCode}, correct HashCode: ${iODF.hashCode}")
    println(s"Convert equals correct: ${backToNew equals iODF}")
    println(s"Convert paths equals correct: ${backToNew.paths equals iODF.paths}")
    println(s"Convert nodes equals correct: ${backToNew.nodes equals iODF.nodes}")
    pathCheck and iICheck and objCheck and mapCheck and (backToNew must beEqualTo(iODF))
  }


  def repeatedOldConvertTest = {
    val oldType: OdfObjects = parsing.OdfParser.parse(testingNodesAsXML.toString) match {
      case Right(o) => o
      case Left(errors: Seq[_]) =>
        println("PARSING FAILED:\n" + errors.mkString("\n"))
        throw new Exception("Parsing failed!")
    }
    val newType = OldTypeConverter.convertOdfObjects(oldType)
    val backToOld = NewTypeConverter.convertODF(newType)
    val p = new scala.xml.PrettyPrinter(120, 4)
    backToOld.asXML showAs {
      ns =>
        "Original:\n\n" + p.format(oldType.asXML.head) + "\n\n" ++
          "Converted:\n\n" + p.format(ns.head) + "\n"

    } must beEqualToIgnoringSpace(
      oldType.asXML
    )

  }

  def infoItemUpdateTest = {
    val lII = InfoItem(
      "II",
      OdfPath("Objects", "Obj", "II"),
      typeAttribute = Some("test"),
      names = Vector(QlmID("II1")),
      descriptions = Set(
        Description("test", Some("English")),
        Description("test", Some("Finnish"))
      ),
      values = Vector(Value("test", testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD1", OdfPath("Objects", "Obj", "II", "MetaData", "MD1"))))),
      attributes = HashMap("test1" -> "test")
    )
    val rII = InfoItem(
      "II",
      OdfPath("Objects", "Obj", "II"),
      names = Vector(QlmID("II2")),
      typeAttribute = Some("testi"),
      descriptions = Set(
        Description("testi", Some("Finnish")),
        Description("test", Some("Swedesh"))
      ),
      values = Vector(Value(31, testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD2", OdfPath("Objects", "Obj", "II", "MetaData", "MD2"))))),
      attributes = HashMap("test2" -> "test")
    )
    val correct = InfoItem(
      "II",
      OdfPath("Objects", "Obj", "II"),
      typeAttribute = Some("testi"),
      names = Vector(QlmID("II2"), QlmID("II1")),
      descriptions = Set(
        Description("test", Some("English")),
        Description("testi", Some("Finnish")),
        Description("test", Some("Swedesh"))
      ),
      values = Vector(Value(31, testTime)),
      metaData = Some(MetaData(Vector(
        InfoItem("MD1", OdfPath("Objects", "Obj", "II", "MetaData", "MD1")),
        InfoItem("MD2", OdfPath("Objects", "Obj", "II", "MetaData", "MD2"))
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
      OdfPath("Objects", "Obj", "II"),
      names = Vector(QlmID("II1")),
      descriptions = Set(Description("test", Some("English"))),
      typeAttribute = Some("oldtype"),
      values = Vector(Value("test", testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD1", OdfPath("Objects", "Obj", "II", "MetaData", "MD1"))))),
      attributes = HashMap("test1" -> "test")
    )
    val rII = InfoItem(
      "II",
      OdfPath("Objects", "Obj", "II"),
      names = Vector(QlmID("II2")),
      descriptions = Set(Description("test", Some("Finnish"))),
      typeAttribute = Some("newtype"),
      values = Vector(Value(31, testTime)),
      metaData = Some(MetaData(Vector(InfoItem("MD2", OdfPath("Objects", "Obj", "II", "MetaData", "MD2"))))),
      attributes = HashMap("test2" -> "test")
    )
    val correct = InfoItem(
      "II",
      OdfPath("Objects", "Obj", "II"),
      names = Vector(QlmID("II2"), QlmID("II1")),
      descriptions = Set(Description("test", Some("English")), Description("test", Some("Finnish"))),
      typeAttribute = Some("newtype"),
      values = Vector(Value("test", testTime), Value(31, testTime)),
      metaData = Some(MetaData(Vector(
        InfoItem("MD1", OdfPath("Objects", "Obj", "II", "MetaData", "MD1")),
        InfoItem("MD2", OdfPath("Objects", "Obj", "II", "MetaData", "MD2"))
      ))),
      attributes = HashMap("test1" -> "test", "test2" -> "test")
    )

    val unioned = lII.union(rII)
    checkIIMatch(unioned, correct)
  }

  def objectUnionTest = {
    val lObj = Object(
      Vector(QlmID("Obj"), QlmID("O1")),
      OdfPath("Objects", "Obj"),
      Some("test1"),
      Set(Description("test", Some("English"))),
      HashMap("test1" -> "test")
    )
    val rObj = Object(
      Vector(QlmID("Obj"), QlmID("O2")),
      OdfPath("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("Finnish"))),
      HashMap("test2" -> "test")
    )
    val correct = Object(
      Vector(QlmID("O2"), QlmID("O1"), QlmID("Obj")),
      OdfPath("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("English")), Description("test", Some("Finnish"))),
      HashMap("test1" -> "test", "test2" -> "test")
    )
    checkObjectMatch(lObj.union(rObj), correct)
  }

  def objectUpdateTest = {
    val lObj = Object(
      Vector(QlmID("Obj"), QlmID("O1")),
      OdfPath("Objects", "Obj"),
      Some("test1"),
      Set(Description("test", Some("English"))),
      HashMap("test1" -> "test")
    )
    val rObj = Object(
      Vector(QlmID("Obj"), QlmID("O2")),
      OdfPath("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("Finnish"))),
      HashMap("test2" -> "test")
    )
    val correct = Object(
      Vector(QlmID("O2"), QlmID("O1"), QlmID("Obj")),
      OdfPath("Objects", "Obj"),
      Some("test2"),
      Set(Description("test", Some("English")), Description("test", Some("Finnish"))),
      HashMap("test1" -> "test", "test2" -> "test")
    )
    checkObjectMatch(lObj.update(rObj), correct)
  }

  def createCorrect(
    o_df: ODF
  ) = {
    val iIOdfPaths = testingNodes.collect {
      case iI: InfoItem => iI.path
    }.toSet
    val objOdfPaths = testingNodes.collect {
      case obj: Object => obj.path
    }.toSet
    val automaticallyCreatedOdfPaths = Set(
      OdfPath("Objects", "ObjectA"),
      OdfPath("Objects", "ObjectB"),
      OdfPath("Objects", "ObjectB", "ObjectB"),
      OdfPath("Objects", "ObjectC")
    )
    val createdIIOdfPaths = o_df.getInfoItems.map(_.path).toSet
    val createdObjOdfPaths = o_df.getObjects.map(_.path).toSet
    (createdIIOdfPaths should contain(iIOdfPaths)) and (
      createdObjOdfPaths should contain(objOdfPaths ++ automaticallyCreatedOdfPaths))
  }

  def getCorrectSubTree(
    o_df: ODF
  ) = {
    o_df.selectSubTree(Set(OdfPath("Objects", "ObjectA"))).getPaths.toSet should contain(
      Set(
        OdfPath("Objects"),
        OdfPath("Objects", "ObjectA"),
        OdfPath("Objects", "ObjectA", "II1"),
        OdfPath("Objects", "ObjectA", "II2")
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
      OdfPath("Objects", "ObjectN", "SubObj", "II1"),
      names = Vector(
        QlmID(
          "II2O1",
          Some("TestID"),
          Some("TestTag")
        ),
        QlmID(
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
            OdfPath("Objects", "ObjectA", "II2", "MetaData", "A")
          ),
          InfoItem(
            "B",
            OdfPath("Objects", "ObjectA", "II2", "MetaData", "B")
          ))
      )),
      attributes = testingAttributes
    )
    o_df.add(beAdded).selectSubTree(
      Set(OdfPath("Objects", "ObjectN"))
    ).getPaths.toSet should contain(
      Set(
        OdfPath("Objects"),
        OdfPath("Objects", "ObjectN"),
        OdfPath("Objects", "ObjectN", "SubObj"),
        OdfPath("Objects", "ObjectN", "SubObj", "II1")
      )
    )
  }

  def toXMLTest(
    o_df: ODF
  ) = {
    val p = new scala.xml.PrettyPrinter(120, 4)
    o_df.asXML showAs (ns =>
      "Generated:\n\n" + p.format(ns.head) + "\n") must beEqualToIgnoringSpace(testingNodesAsXML)
  }

  def fromXMLTest(
    o_df: ODF
  ) = {
    ODFParser.parse(o_df.asXML.toString) should beRight {
      o: ImmutableODF =>
        val iODF = o_df.immutable
        lazy val parsedOdfPaths = o.getPaths.toSet
        lazy val correctOdfPaths = iODF.getPaths.toSet
        lazy val pathCheck = (parsedOdfPaths must contain(correctOdfPaths)) and
          ((parsedOdfPaths -- correctOdfPaths) must beEmpty) and ((correctOdfPaths -- parsedOdfPaths) must beEmpty)
        lazy val parsedII = o.getInfoItems.toSet
        lazy val correctII = iODF.getInfoItems.toSet
        lazy val iICheck = {
          (parsedII -- correctII) must beEmpty
        } and {
          (correctII -- parsedII) must beEmpty
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
        pathCheck and iICheck and objCheck and mapCheck and (o must beEqualTo(iODF))
    }
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
          <value unixTime={(testTime.getTime / 1000).toString} type="xs:int" dateTime={timestampToXML(testTime)
            .toString}>93</value>
          <value unixTime={(testTime.getTime / 1000).toString} type="xs:float" dateTime={timestampToXML(testTime)
            .toString}>51.9</value>
          <value unixTime={(testTime.getTime / 1000).toString} dateTime={timestampToXML(testTime).toString}>81.5</value>
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
        ),
        QlmID(
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
        ),
        QlmID(
          "OCC",
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

  val oldodf: OdfObjects = {
    /*Right(
      Vector(
        WriteRequest(
          10.0, */ OdfObjects(
      Vector(
        OdfObject(
          Vector(),
          OdfPath("Objects", "SmartHouse"), Vector(
            OdfInfoItem(
              OdfPath("Objects", "SmartHouse", "PowerConsumption"), Vector(
                OdfValue(
                  "180", "xs:string",
                  testTime)), None, None), OdfInfoItem(
              OdfPath("Objects", "SmartHouse", "Moisture"), Vector(
                OdfValue(
                  "0.20", "xs:string",
                  testTime)), None, None)), Vector(
            OdfObject(
              Vector(),
              OdfPath("Objects", "SmartHouse", "SmartFridge"), Vector(
                OdfInfoItem(
                  OdfPath("Objects", "SmartHouse", "SmartFridge", "PowerConsumption"), Vector(
                    OdfValue(
                      "56", "xs:string",
                      testTime)), None, None)), Vector(), None, None), OdfObject(
              Vector(),
              OdfPath("Objects", "SmartHouse", "SmartOven"), Vector(
                OdfInfoItem(
                  OdfPath("Objects", "SmartHouse", "SmartOven", "PowerOn"), Vector(
                    OdfValue(
                      "1", "xs:string",
                      testTime)), None, None)), Vector(), None, None)), None, None), OdfObject(
          Vector(),
          OdfPath("Objects", "SmartCar"), Vector(
            OdfInfoItem(
              OdfPath("Objects", "SmartCar", "Fuel"),
              Vector(OdfValue(
                "30",
                "xs:string",
                testTime
              )),
              None,
              Some(OdfMetaData(
                Vector(OdfInfoItem(
                  OdfPath("Objects", "SmartCar", "Fuel", "MetaData", "Units"),
                  Vector(OdfValue(
                    "Litre",
                    "xs:string",
                    testTime
                  ))
                ))
              ))
            )),
          Vector(), None, None), OdfObject(
          Vector(),
          OdfPath("Objects", "SmartCottage"), Vector(), Vector(
            OdfObject(
              Vector(),
              OdfPath("Objects", "SmartCottage", "Heater"), Vector(), Vector(), None, None), OdfObject(
              Vector(),
              OdfPath("Objects", "SmartCottage", "Sauna"), Vector(), Vector(), None, None), OdfObject(
              Vector(),
              OdfPath("Objects", "SmartCottage", "Weather"), Vector(), Vector(), None, None)), None, None)), None)
  }

  def timestampToXML(timestamp: Timestamp): XMLGregorianCalendar = {
    val cal = new GregorianCalendar()
    cal.setTime(timestamp)
    DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
  }
  /*
  val descriptions = Set( Description( "testi", Some("fin")),Description( "test", Some("eng")) )
  val values = Vector(
    IntValue( 53, testTime),
    StringValue( "test", testTime),
    DoubleValue( 5.3, testTime)
  )
  def createQlmId(id: String) = QlmID(id,Some("testId"),Some("testTag"),Some(testTime),Some(testTime))
  def createObj( id: String, parentPath: Path ) ={
    Object(
        Vector(createQlmId(id)),
        parentPath / id, 
        Some("testObj"),
        descriptions
      )
  }
  def createII( name: String, parentPath: Path, md: Boolean= true): InfoItem ={
    InfoItem(
      name,
      parentPath / name,
      Some("testII"),
      Vector(createQlmId(name+"O")),
      descriptions,
      values,
      if( md ) {
        Some(MetaData(
          Vector(
            createII("II1",parentPath / name / "MetaData", false),
            createII("II2",parentPath / name / "MetaData", false)
          )
        ))
      } else None
    )
  }*/
}
