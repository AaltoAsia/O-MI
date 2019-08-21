package types.omi

import java.net.URL
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.sql.Timestamp

import parsing.OMIStreamParser

import scala.xml.Elem
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.mutable.Specification
import org.specs2.matcher._
import types.odf._
import types._

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import testHelpers.Actorstest
import scala.xml.XML

class OmiTypesTest(implicit ee: ExecutionEnv) extends Specification with XmlMatchers{
  implicit val system = Actorstest.createSilentAs()
  implicit val mat = ActorMaterializer()
  val testTime: Timestamp = Timestamp.valueOf("2018-08-09 16:00:01.001")
  val countForRead: Int  = 785634129
  val callback = RawCallback("http://test.com")
  val exampleDirStr = "/OmiRequestExamples"
  //val exampleDir = java.nio.file.Paths.get(exampleDirStr)
  //require( java.nio.file.Files.exists(exampleDir), s"$exampleDirStr does not exist" )
  //require( java.nio.file.Files.isDirectory(exampleDir), s"$exampleDirStr is not directory" )

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
  }
  val odf = ImmutableODF(
    Vector(
      Objects()/*,
      createObj("Obj1",Path("Objects")),
      createII( "II1",Path("Objects/Obj1")),
      createII( "II2",Path("Objects/Obj1")),
      createObj("SubObj1",Path("Objects/Obj1")),
      createII( "II1",Path("Objects/Obj1/SubObj1")),
      createII( "II2",Path("Objects/Obj1/SubObj1")),
      createObj("SubObj2",Path("Objects/Obj1")),
      createII( "II1",Path("Objects/Obj1/SubObj2")),
      createII( "II2",Path("Objects/Obj1/SubObj2")),
      createObj("Obj2",Path("Objects")),
      createII( "II1",Path("Objects/Obj2")),
      createII( "II2",Path("Objects/Obj2")),
      createObj("SubObj1",Path("Objects/Obj2")),
      createII( "II1",Path("Objects/Obj2/SubObj1")),
      createII( "II2",Path("Objects/Obj2/SubObj1")),
      createObj("SubObj2",Path("Objects/Obj2")),
      createII( "II1",Path("Objects/Obj2/SubObj2")),
      createII( "II2",Path("Objects/Obj2/SubObj2"))
      */
    )
  )
  case class RequestFileTest(description: String, request: OmiRequest, filepath: URL )
  def setTimezoneToSystemLocale(in: String): String = {
    val date = """(end|begin)\s*=\s*"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?(?:(?:\+|-)\d{2}:\d{2})?)"""".r

    val replaced = date replaceAllIn(in, _ match {

      case date(pref, timestamp) => {

        val form = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        //form.setTimeZone(TimeZone.getTimeZone("UTC"))

        val parsedTimestamp = form.parse(timestamp)

        form.setTimeZone(TimeZone.getDefault)
        form.applyPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        val newTimestamp = form.format(parsedTimestamp)

        (pref + "=\"" + newTimestamp + "\"")
      }
    })
    replaced
  }
  val requestFileTests: Vector[RequestFileTest] = Vector(
    RequestFileTest(
      "Read with begin, end, newest and callback", 
      ReadRequest( 
        odf,
        Some(testTime),
        Some(testTime),
        Some(countForRead),
        None,
        None,
        Some(callback)
      ),
      getClass.getResource(exampleDirStr + "/ReadWithTimewindowNewestAndCallback.xml" )
    ),
    RequestFileTest(
      "Read with begin, end, oldest and callback", 
      ReadRequest( 
        odf,
        Some(testTime),
        Some(testTime),
        None,
        Some(countForRead),
        None,
        Some(callback)
      ), 
      getClass.getResource( exampleDirStr + "/ReadWithTimewindowOldestAndCallback.xml")
    ), 
    RequestFileTest(
      "Poll with callback", 
      PollRequest( 
        Some(callback),
        Vector(countForRead:Long)
      ), 
      getClass.getResource( exampleDirStr + "/PollWithCallback.xml")
    ), 
    RequestFileTest(
      "Subscription with callback", 
      SubscriptionRequest( 
        360.seconds,
        odf,
        callback = Some(callback)
      ), 
      getClass.getResource( exampleDirStr + "/SubscriptionWithCallback.xml")
    ), 
    RequestFileTest(
      "Write with callback", 
      WriteRequest( 
        odf,
        Some(callback)
      ), 
      getClass.getResource( exampleDirStr + "/WriteWithCallback.xml")
    ), 
    RequestFileTest(
      "Call with callback", 
      CallRequest( 
        odf,
        Some(callback)
      ), 
      getClass.getResource( exampleDirStr + "/CallWithCallback.xml")
    ), 
    RequestFileTest(
      "Delete with callback", 
      DeleteRequest( 
        odf,
        Some(callback)
      ), 
      getClass.getResource( exampleDirStr + "/DeleteWithCallback.xml")
    )  
  )
  "OmiTypes" >> {
    org.specs2.specification.core.Fragments.empty.append(
      requestFileTests.map{
        case RequestFileTest(description, request, fileUrl) =>
          s"$description" >> {
            val timeCorrected = setTimezoneToSystemLocale(OmiParser.XMLParser.load( fileUrl ).toString)
              val xml: Elem = OmiParser.XMLParser.loadString(timeCorrected)
            "to XML test" >> {
              request.asXML must beEqualToIgnoringSpace( xml )
            }
            "to streaming XML test" >> {
              request.asXMLSource.runWith(Sink.fold("")(_ + _)).map(XML loadString _) must beEqualToIgnoringSpace( xml ).await(1,20.seconds)
            }
            //This may not be needed? 
            //Timestamp do not match 
            "from XML test" >> {OmiParser.parse(xml.toString) must beRight( contain( request ) )}
          }
      }
    )
  }
}
