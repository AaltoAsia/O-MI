package types 
package odf

import java.sql.Timestamp
import java.util.{Date, GregorianCalendar}
import javax.xml.datatype.{DatatypeFactory, XMLGregorianCalendar}

import scala.xml.Utility.trim
import scala.collection.immutable.HashMap
import org.specs2.matcher._
import org.specs2.matcher.XmlMatchers._

import org.specs2._
import types.{Path => OdfPath}
import types.OdfTypes._

class OdfTypesTest extends mutable.Specification{
  val testTime : Timestamp = Timestamp.valueOf("2017-05-11 15:44:55")
  sequential
  "Object" should {
    "union correctly" >> objectUnionTest
    "update correctly" >> objectUpdateTest
  }
  "InfoItem" should {
    "union correctly" >> infoItemUnionTest
    "update correctly" >> infoItemUpdateTest
  }
  "ImmubtableODF" should {
    val o_df = ImmutableODF( testingNodes )
    "create all given paths and their ancestors" in createCorrect(o_df)
    "return all SubTree paths when asked" in getCorrectSubTree(o_df)
    "create correct XML presentation" in toXMLTest(o_df)
    "add also missing ancestors when a Node is added" in addTest(o_df) 
  }
  "MubtableODF" should {
    val o_df = MutableODF( testingNodes )
    "create all given paths and their ancestors" in createCorrect(o_df) 
    "return all SubTree paths when asked" in getCorrectSubTree(o_df)
    "create correct XML presentation" in toXMLTest(o_df)
    "add also missing ancestors when a Node is added" in addTest(o_df) 
    "be empty if contains only Objects" in emptyTestMutable
    "be nonEmpty if contains any additional nodes to Objects" in nonEmptyTestMutable
  }
  "ODFParser" should {
    val o_df = ImmutableODF( testingNodes )
    "should parse correctly XML created from ODF to equal ODF" in fromXMLTest(o_df) 
  } 
  "Converters" should {
    //TODO: What shoul be tested? Conversion may lose data.
    "convert from old to new and have same XML" in convertedOldHasSameXML
    "convert from new to old and have same XML" in convertedNewHasSameXML
    "Convert from old to new and back to old and stay the same" in repeatedOldConvertTest
    "Convert from new to old and back to new and stay the same" in repeatedNewConvertTest
  }

  def emptyTestMutable ={
    val odf = MutableODF(
      Vector(
        Objects()
      )
    )
    odf.getPaths.size <= 1 and 
    odf.getPaths.contains(OdfPath("Objects")) === true and 
    odf.isEmpty === true
  }
  def nonEmptyTestMutable ={
    val odf = MutableODF(
      Vector(
        InfoItem(
          "test",
          OdfPath("Objects","Obj","test")
        )
      )
    )
    odf.nonEmpty === true
  }
  def convertedNewHasSameXML ={
    val p = new scala.xml.PrettyPrinter(120, 4)
    val newType = ImmutableODF( testingNodes )
    val newTypeWithoutNamesForIIs = ImmutableODF( testingNodes.map{
      case obj: Objects => obj
      case obj: Object => obj.copy( descriptions = obj.descriptions.headOption.toSet)
      case ii: InfoItem => ii.copy( names = Vector(), descriptions = ii.descriptions.headOption.toSet)
    } )
    val oldType = NewTypeConverter.convertODF( newType )
    oldType.asXML showAs {
      ns =>
        "Original:\n\n" + p.format(newTypeWithoutNamesForIIs.asXML.head) + "\n\n" ++
          "Converted:\n\n" + p.format(ns.head) + "\n"

    } must beEqualToIgnoringSpace( newTypeWithoutNamesForIIs.asXML )
  }
  def convertedOldHasSameXML ={
    val p = new scala.xml.PrettyPrinter(120, 4)
    val oldType : OdfObjects = parsing.OdfParser.parse( testingNodesAsXML.toString ) match {
      case Right( o ) => o
      case Left( errors: Seq[ParseError] ) =>
        println( "PARSING FAILED:\n" + errors.mkString("\n") )
        throw new Exception("Parsing failed!")
    }
    val newType = OldTypeConverter.convertOdfObjects(oldType) 
    newType.asXML showAs {
      ns =>
        "Original:\n\n" + p.format(oldType.asXML.head) + "\n\n" ++
          "Converted:\n\n" + p.format(ns.head) + "\n"

    } must beEqualToIgnoringSpace( oldType.asXML )
  }
  def  repeatedNewConvertTest ={
    val newType = ImmutableODF( testingNodes )
    val newTypeWithoutNamesForIIs = ImmutableODF( testingNodes.map{
      case obj: Objects => obj
      case obj: Object => obj.copy( descriptions = obj.descriptions.headOption.toSet)
      case ii: InfoItem => ii.copy( names = Vector(), descriptions = ii.descriptions.headOption.toSet)
    } )
    val iODF = newTypeWithoutNamesForIIs
    val oldType = NewTypeConverter.convertODF( iODF )
    val backToNew = OldTypeConverter.convertOdfObjects( oldType )
    lazy val parsedOdfPaths = backToNew.getPaths.toSet 
    lazy val correctOdfPaths = iODF.getPaths.toSet
    lazy val pathCheck = (parsedOdfPaths must contain( correctOdfPaths ) ) and
    ( (parsedOdfPaths -- correctOdfPaths) must beEmpty ) and ( (correctOdfPaths -- parsedOdfPaths) must beEmpty )
    lazy val parsedII = backToNew.getInfoItems.toSet 
    lazy val correctII = iODF.getInfoItems.toSet
    lazy val iICheck ={
      (parsedII -- correctII ) must beEmpty } and {
      (correctII -- parsedII) must beEmpty } and {
      parsedII must contain(correctII)
    } 

    lazy val parsedObj = backToNew.getObjects.toSet 
    lazy val correctObj = iODF.getObjects.toSet
    lazy val objCheck ={
      (parsedObj -- correctObj ) must beEmpty } and {
      (correctObj -- parsedObj ) must beEmpty } and {
      parsedObj must contain(correctObj)
    } 

    lazy val parsedMap = backToNew.getNodesMap
    lazy val correctMap = iODF.getNodesMap
    lazy val mapCheck = parsedMap.toSet must contain(correctMap.toSet)
  
        println(  s"Convert hashCode: ${backToNew.hashCode}, correct HashCode: ${iODF.hashCode }")
        println(  s"Convert equals correct: ${backToNew equals iODF}")
        println(  s"Convert paths equals correct: ${backToNew.paths equals iODF.paths}")
        println(  s"Convert nodes equals correct: ${backToNew.nodes equals iODF.nodes}")
    pathCheck and iICheck and objCheck and mapCheck and (backToNew must beEqualTo(iODF)) 
  }


  def  repeatedOldConvertTest ={
    val oldType : OdfObjects = parsing.OdfParser.parse( testingNodesAsXML.toString ) match {
      case Right( o ) => o
      case Left( errors: Seq[ParseError] ) =>
        println( "PARSING FAILED:\n" + errors.mkString("\n") )
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
  def infoItemUpdateTest ={
    val lII = InfoItem(
      "II",
      OdfPath( "Objects","Obj","II"),
      typeAttribute = Some( "test"),
      names =         Vector(QlmID( "II1" )),
      descriptions =  Set(
        Description("test", Some("English")),
        Description("test", Some("Finnish"))
      ),
      values =        Vector(Value( "test",testTime )),
      metaData =      Some(MetaData(Vector(InfoItem( "MD1", OdfPath( "Objects","Obj","II","MetaData","MD1"))))),
      attributes =    HashMap( "test1" -> "test" )
      )
    val rII =  InfoItem(
      "II",
      OdfPath( "Objects","Obj","II"),
     names =         Vector(QlmID( "II2" )),
     typeAttribute = Some( "testi"),
     descriptions =  Set(
       Description("testi", Some("Finnish")),
       Description("test", Some("Swedesh"))
     ),
     values =        Vector(Value( 31,testTime )),
     metaData =      Some(MetaData(Vector(InfoItem( "MD2", OdfPath( "Objects","Obj","II","MetaData","MD2"))))),
     attributes =    HashMap( "test2" -> "test" )
    )
    val correct = InfoItem(
      "II",
      OdfPath( "Objects","Obj","II"),
     typeAttribute = Some( "testi"),
     names =         Vector(QlmID("II2"),QlmID( "II1" )),
     descriptions =  Set(
        Description("test", Some("English")),
        Description("testi", Some("Finnish")),
        Description("test", Some("Swedesh"))
      ),
     values =        Vector(Value( 31,testTime )),
     metaData =      Some(MetaData(Vector(
     InfoItem( "MD1", OdfPath( "Objects","Obj","II","MetaData","MD1")),
       InfoItem( "MD2", OdfPath( "Objects","Obj","II","MetaData","MD2"))
     ))),
     attributes = HashMap( "test1" -> "test", "test2" -> "test" )
    )
    
    val updated = lII.update(rII) 
    checkIIMatch(updated,correct)
  }
  def checkIIMatch(test: InfoItem, correct: InfoItem) ={
    (test.nameAttribute === correct.nameAttribute) and
    (test.path === correct.path) and
    (test.typeAttribute === correct.typeAttribute) and
    (test.names === correct.names) and
    (test.descriptions === correct.descriptions) and
    (test.values === correct.values) and
    (test.metaData === correct.metaData) and
    (test.attributes === correct.attributes) and
    (test should beEqualTo( correct)) 
  }
  def checkObjectMatch(test: Object,correct: Object ) = {
    (test.ids === correct.ids) and
    (test.path === correct.path) and
    (test.typeAttribute === correct.typeAttribute) and
    (test.descriptions === correct.descriptions) and
    (test.attributes === correct.attributes) and
    (test should beEqualTo( correct ) ) 
  }


  def infoItemUnionTest ={
    val lII = InfoItem(
      "II",
      OdfPath( "Objects","Obj","II"),
      names =         Vector(QlmID( "II1" )),
      descriptions =  Set(Description("test", Some("English"))),
      typeAttribute = Some("oldtype"),
      values =        Vector(Value( "test",testTime )),
      metaData =      Some(MetaData(Vector(InfoItem( "MD1", OdfPath( "Objects","Obj","II","MetaData","MD1"))))),
      attributes =    HashMap( "test1" -> "test" )
      )
    val rII =  InfoItem(
      "II",
      OdfPath( "Objects","Obj","II"),
     names =         Vector(QlmID( "II2" )),
     descriptions =  Set(Description("test", Some("Finnish"))),
     typeAttribute = Some("newtype"),
     values =        Vector(Value( 31,testTime )),
     metaData =      Some(MetaData(Vector(InfoItem( "MD2", OdfPath( "Objects","Obj","II","MetaData","MD2"))))),
     attributes =    HashMap( "test2" -> "test" )
    )
    val correct = InfoItem(
      "II",
      OdfPath( "Objects","Obj","II"),
     names =         Vector(QlmID("II2"),QlmID( "II1" )),
     descriptions =  Set(Description("test", Some("English")),Description("test", Some("Finnish"))),
     typeAttribute = Some("newtype"),
     values =        Vector(Value( "test",testTime ),Value( 31,testTime )),
     metaData =      Some(MetaData(Vector(
     InfoItem( "MD1", OdfPath( "Objects","Obj","II","MetaData","MD1")),
       InfoItem( "MD2", OdfPath( "Objects","Obj","II","MetaData","MD2"))
     ))),
     attributes = HashMap( "test1" -> "test", "test2" -> "test" )
    )
    
    val unioned = lII.union(rII) 
    checkIIMatch(unioned,correct)
  }

  def objectUnionTest ={
    val lObj = Object(
      Vector( QlmID( "Obj" ), QlmID( "O1" )),
      OdfPath( "Objects","Obj" ),
      Some( "test1" ),
      Set(Description("test", Some("English"))),
      HashMap( "test1" -> "test" )
    )
    val rObj = Object(
      Vector( QlmID( "Obj" ), QlmID( "O2" )),
      OdfPath( "Objects","Obj" ),
      Some( "test2" ),
      Set(Description("test", Some("Finnish"))),
      HashMap( "test2" -> "test" )
    )
    val correct = Object(
      Vector( QlmID( "O2" ), QlmID( "O1" ), QlmID( "Obj" )),
      OdfPath( "Objects","Obj" ),
      Some( "test2" ),
      Set(Description("test", Some("English")),Description("test", Some("Finnish"))),
      HashMap( "test1" -> "test", "test2" -> "test" )
    )
    checkObjectMatch(lObj.union(rObj),correct) 
  }
  def objectUpdateTest ={
    val lObj = Object(
      Vector( QlmID( "Obj" ), QlmID( "O1" )),
      OdfPath( "Objects","Obj" ),
      Some( "test1" ),
      Set(Description("test", Some("English"))),
      HashMap( "test1" -> "test" )
    )
    val rObj = Object(
      Vector( QlmID( "Obj" ), QlmID( "O2" )),
      OdfPath( "Objects","Obj" ),
      Some( "test2" ),
      Set(Description("test", Some("Finnish"))),
      HashMap( "test2" -> "test" )
    )
    val correct = Object(
      Vector( QlmID( "O2" ), QlmID( "O1" ), QlmID( "Obj" )),
      OdfPath( "Objects","Obj" ),
      Some( "test2" ),
      Set(Description("test", Some("English")),Description("test", Some("Finnish"))),
      HashMap( "test1" -> "test", "test2" -> "test" )
    )
    checkObjectMatch(lObj.update(rObj),correct) 
  }

  def createCorrect[M<:scala.collection.Map[OdfPath,Node],S <: scala.collection.SortedSet[OdfPath]](
    o_df: ODF
  ) = {
    val iIOdfPaths = testingNodes.collect{ 
      case iI: InfoItem => iI.path
    }.toSet
    val objOdfPaths = testingNodes.collect{ 
      case obj: Object => obj.path
    }.toSet
    val automaticallyCreatedOdfPaths = Set(
      OdfPath("Objects","ObjectA"),
      OdfPath("Objects","ObjectB"),
      OdfPath("Objects","ObjectB","ObjectB"),
      OdfPath("Objects","ObjectC")
    )
    val createdIIOdfPaths = o_df.getInfoItems.map( _.path).toSet 
    val createdObjOdfPaths = o_df.getObjects.map( _.path).toSet
    (createdIIOdfPaths should contain(iIOdfPaths) )and ( 
    createdObjOdfPaths should contain(objOdfPaths ++ automaticallyCreatedOdfPaths) )
  }
  def getCorrectSubTree[M<:scala.collection.Map[OdfPath,Node],S <: scala.collection.SortedSet[OdfPath]](
    o_df: ODF
  ) = {
    o_df.selectSubTree( Seq(OdfPath("Objects","ObjectA"))).getPaths.toSet should contain(
      Set(
        OdfPath("Objects"),
        OdfPath("Objects","ObjectA"),
        OdfPath("Objects","ObjectA","II1"),
        OdfPath("Objects","ObjectA","II2")
      )
    )
  }
  def addTest[M<:scala.collection.Map[OdfPath,Node],S <: scala.collection.SortedSet[OdfPath]](
    o_df: ODF
  ) = {
    val beAdded = InfoItem( 
        "II1",
        OdfPath("Objects","ObjectN","SubObj","II1"),
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
          Value( 93, testTime, testingAttributes ), 
          Value( "51.9", "xs:float", testTime ), 
          Value( "51.9", testTime, testingAttributes)
        ),
        metaData = Some(MetaData(
          Vector(
          InfoItem( 
            "A",
            OdfPath("Objects","ObjectA","II2","MetaData","A")
          ),
          InfoItem( 
            "B",
            OdfPath("Objects","ObjectA","II2","MetaData","B")
          ))
        )),
        attributes = testingAttributes
      )
    o_df.add( beAdded ).selectSubTree( 
      Seq(OdfPath("Objects","ObjectN"))
    ).getPaths.toSet should contain(
      Set(
        OdfPath("Objects"),
        OdfPath("Objects","ObjectN"),
        OdfPath("Objects","ObjectN","SubObj"),
        OdfPath("Objects","ObjectN","SubObj","II1")
      )
    )
  }
  def toXMLTest[M<:scala.collection.Map[OdfPath,Node],S <: scala.collection.SortedSet[OdfPath]](
    o_df: ODF
  ) = {
    val p = new scala.xml.PrettyPrinter(120, 4)
    o_df.asXML showAs (ns =>
      "Generated:\n\n" + p.format(ns.head) + "\n") must beEqualToIgnoringSpace( testingNodesAsXML )
  }
  def fromXMLTest[M<:scala.collection.Map[OdfPath,Node],S <: scala.collection.SortedSet[OdfPath]](
    o_df: ODF
  ) ={
    ODFParser.parse( o_df.asXML.toString ) should beRight{
      o: ImmutableODF => 
        val iODF = o_df.immutable 
        lazy val parsedOdfPaths = o.getPaths.toSet 
        lazy val correctOdfPaths = iODF.getPaths.toSet
        lazy val pathCheck = (parsedOdfPaths must contain( correctOdfPaths ) ) and
        ( (parsedOdfPaths -- correctOdfPaths) must beEmpty ) and ( (correctOdfPaths -- parsedOdfPaths) must beEmpty )
        lazy val parsedII = o.getInfoItems.toSet 
        lazy val correctII = iODF.getInfoItems.toSet
        lazy val iICheck ={
         (parsedII -- correctII) must beEmpty } and {
          (correctII -- parsedII)must beEmpty } and {
          parsedII must contain(correctII)
        } 
        if( (parsedII -- correctII).nonEmpty ){
          println("Parsed IIs:\n" + parsedII.mkString("\n"))
          println("Correct IIs:\n" + correctII.mkString("\n"))
          println("Parsed -- Correct IIs:\n" + (parsedII -- correctII).mkString("\n"))
        } else if((correctII -- parsedII).nonEmpty){
          println("Correct -- ParsedIIs:\n" + (correctII -- parsedII).mkString("\n"))
        }
        lazy val parsedObj = o.getObjects.toSet 
        lazy val correctObj = iODF.getObjects.toSet
        lazy val objCheck ={
         (parsedObj -- correctObj ) must beEmpty } and {
          (correctObj -- parsedObj ) must beEmpty } and {
          parsedObj must contain(correctObj)
        } 
        lazy val parsedMap = o.getNodesMap
        lazy val correctMap = iODF.getNodesMap
        lazy val mapCheck = parsedMap.toSet must contain(correctMap.toSet)

        println(  s"Parsed hashCode: ${o.hashCode}, correct HashCode: ${iODF.hashCode }")
        println(  s"Parsed paths equals correct: ${o.paths equals iODF.paths}")
        println(  s"Parsed nodes equals correct: ${o.nodes equals iODF.nodes}")
        pathCheck and iICheck and objCheck and mapCheck and (o must beEqualTo( iODF )) 
    }
  }
 
  def testingNodesAsXML ={
  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
        <Object>
            <id>ObjectA</id>
            <InfoItem name="II1"/>
            <InfoItem name="II2">
                <name>II2</name>
                <name tagType="TestTag" idType="TestID">II2O1</name>
                <name tagType="TestTag" idType="TestID">II2O2</name>
                <description lang="English">Testing</description>
                <description lang="Finnish">Testaus</description>
                <MetaData>
                    <InfoItem name="A"/>
                    <InfoItem name="B"/>
                </MetaData>
                <value unixTime={(testTime.getTime / 1000).toString} type="xs:int" dateTime={timestampToXML(testTime).toString} >93</value>
                <value unixTime={(testTime.getTime / 1000).toString} type="xs:float" dateTime={timestampToXML(testTime).toString} >51.9</value>
                <value unixTime={(testTime.getTime / 1000).toString} dateTime={timestampToXML(testTime).toString} >81.5</value>
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
            <Object type="TestingType">
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
        OdfPath("Objects","ObjectA","II1")
      ),
      InfoItem( 
        "II2",
        OdfPath("Objects","ObjectA","II2"),
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
          Value( 93, testTime), 
          Value( "51.9", "xs:float", testTime ), 
          Value( "81.5", testTime)
        ),
        metaData = Some(MetaData(
          Vector(
          InfoItem( 
            "A",
            OdfPath("Objects","ObjectA","II2","MetaData","A")
          ),
          InfoItem( 
            "B",
            OdfPath("Objects","ObjectA","II2","MetaData","B")
          ))
        ))
      ),
      InfoItem( 
        "II1",
        OdfPath("Objects","ObjectB","ObjectB","II1")
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
        OdfPath("Objects","ObjectC","ObjectCC"),
        typeAttribute = Some("TestingType"),
        descriptions  = testingDescription
     )
    )

  def testingAttributes = Map{
    "testing" -> "true"
  }
  def testingDescription =Set(
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
          OdfPath("Objects","SmartHouse"), Vector(
            OdfInfoItem(
              OdfPath("Objects","SmartHouse","PowerConsumption"), Vector(
                OdfValue(
                  "180", "xs:string",
                    testTime)), None, None), OdfInfoItem(
              OdfPath("Objects","SmartHouse","Moisture"), Vector(
                OdfValue(
                  "0.20", "xs:string",
                    testTime)), None, None)), Vector(
            OdfObject(
            Vector(),
              OdfPath("Objects","SmartHouse","SmartFridge"), Vector(
                OdfInfoItem(
                  OdfPath("Objects","SmartHouse","SmartFridge","PowerConsumption"), Vector(
                    OdfValue(
                      "56", "xs:string",
                        testTime)), None, None)), Vector(), None, None), OdfObject(
            Vector(),
              OdfPath("Objects","SmartHouse","SmartOven"), Vector(
                OdfInfoItem(
                  OdfPath("Objects","SmartHouse","SmartOven","PowerOn"), Vector(
                    OdfValue(
                      "1", "xs:string",
                        testTime)), None, None)), Vector(), None, None)), None, None), OdfObject(
        Vector(),
          OdfPath("Objects","SmartCar"), Vector(
            OdfInfoItem(
              OdfPath("Objects","SmartCar","Fuel"),
              Vector(OdfValue(
                  "30",
                  "xs:string",
                  testTime
              )), 
              None, 
              Some(OdfMetaData(
                Vector(OdfInfoItem(
                  OdfPath("Objects","SmartCar","Fuel","MetaData","Units"),
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
          OdfPath("Objects","SmartCottage"), Vector(), Vector(
            OdfObject(
            Vector(),
              OdfPath("Objects","SmartCottage","Heater"), Vector(), Vector(), None, None), OdfObject(
            Vector(),
              OdfPath("Objects","SmartCottage","Sauna"), Vector(), Vector(), None, None), OdfObject(
            Vector(),
              OdfPath("Objects","SmartCottage","Weather"), Vector(), Vector(), None, None)), None, None)), None)
  }
 def timestampToXML(timestamp: Timestamp) : XMLGregorianCalendar ={
   val cal = new GregorianCalendar()
   cal.setTime(timestamp)
   DatatypeFactory.newInstance().newXMLGregorianCalendar(cal)
 }
}
