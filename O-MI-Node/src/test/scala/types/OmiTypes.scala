package types.omi

import java.net.URL
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.sql.Timestamp
import java.nio.file.Paths

import parsing.OMIStreamParser

import scala.xml.Elem
import scala.concurrent.duration._
import scala.concurrent.Future
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.mutable.Specification
import org.specs2.matcher._
import scala.xml.XML
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import akka.stream.ActorMaterializer
import types.odf._
import types._
import utils._
import testHelpers.Actorstest

class OmiTypesTest(implicit ee: ExecutionEnv) extends Specification with XmlMatchers{
  implicit val system = Actorstest.createSilentAs()
  implicit val mat = ActorMaterializer()
  val testTime: Timestamp = Timestamp.valueOf("2018-08-09 16:00:01.001")
  val countForRead: Int  = 785634129
  val callback = RawCallback("http://test.com")
  val exampleDirStr = "./src/test/resources/OmiRequestExamples"
  println( Paths.get(exampleDirStr).toAbsolutePath.toString)
  //val exampleDir = Paths.get(exampleDirStr)
  //require( java.nio.file.Files.exists(exampleDir), s"$exampleDirStr does not exist" )
  //require( java.nio.file.Files.isDirectory(exampleDir), s"$exampleDirStr is not directory" )

  val descriptions = Set( Description( "testi", Some("fin")),Description( "test", Some("eng")) )
  val values = Vector(
    IntValue( 53, testTime),
    StringValue( "test", testTime),
    DoubleValue( 5.3, testTime)
  )
  def createQlmId(id: String) = OdfID(id,Some("testId"),Some("testTag"),Some(testTime),Some(testTime))
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
  case class RequestFileTest(description: String, request: OmiRequest, filepath: java.nio.file.Path )
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
      Paths.get(exampleDirStr, "ReadWithTimewindowNewestAndCallback.xml" )
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
      Paths.get( exampleDirStr, "ReadWithTimewindowOldestAndCallback.xml")
    ), 
    RequestFileTest(
      "Poll with callback", 
      PollRequest( 
        Some(callback),
        Vector(countForRead:Long)
      ), 
      Paths.get( exampleDirStr, "PollWithCallback.xml")
    ), 
    RequestFileTest(
      "Subscription with callback", 
      SubscriptionRequest( 
        360.seconds,
        odf,
        callback = Some(callback)
      ), 
      Paths.get( exampleDirStr, "SubscriptionWithCallback.xml")
    ), 
    RequestFileTest(
      "Write with callback", 
      WriteRequest( 
        odf,
        Some(callback)
      ), 
      Paths.get( exampleDirStr, "WriteWithCallback.xml")
    ), 
    RequestFileTest(
      "Call with callback", 
      CallRequest( 
        odf,
        Some(callback)
      ), 
      Paths.get( exampleDirStr, "CallWithCallback.xml")
    ), 
    RequestFileTest(
      "Delete with callback", 
      DeleteRequest( 
        odf,
        Some(callback)
      ), 
      Paths.get( exampleDirStr, "DeleteWithCallback.xml")
    )  
  )
  def correctTimestamp(timestamp: String) ={
    val form = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    //form.setTimeZone(TimeZone.getTimeZone("UTC"))

    val parsedTimestamp = form.parse(timestamp)

    form.setTimeZone(TimeZone.getDefault)
    form.applyPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")

    form.format(parsedTimestamp)
  }
  "OmiTypes" >> {
    org.specs2.specification.core.Fragments.empty.append(
      requestFileTests.map{
        case RequestFileTest(description, request, filePath) =>
          s"$description" >> {
            def fileSource =  FileIO.fromPath( filePath)
            def eventSource = fileSource.via(XmlParsing.parser)
            val eventsF : Future[Seq[ParseEvent]]= eventSource.map{
              case se: StartElement if se.attributes.contains("begin") ||  se.attributes.contains("end")  =>
                se.copy( attributesList = se.attributesList.map{
                  case attr: Attribute if attr.name == "end" || attr.name == "begin"=>
                      attr.copy(value = correctTimestamp(attr.value))
                  case attr: Attribute => attr
                })
              case pe: ParseEvent => pe
            }.runWith( Sink.collection[ParseEvent, Seq[ParseEvent]])
            "to XML test" >> {
                fileSource.map(_.utf8String).runWith(Sink.fold("")(_ + _)).map(XML loadString _).flatMap{
                  correctXml =>
                    val events =request.asXMLEvents.map{
                      case se: StartElement if se.attributes.contains("begin") ||  se.attributes.contains("end")  =>
                        se.copy( attributesList = se.attributesList.map{
                          case attr: Attribute if attr.name == "end" || attr.name == "begin"=>
                            attr.copy(value = correctTimestamp(attr.value))
                          case attr: Attribute => attr
                        })
                      case pe: ParseEvent => pe
                    }
                  parseEventsToByteSource(events).map(_.utf8String).runWith(Sink.fold("")(_ + _)).map(XML loadString _).map{
                      genXml =>
                        genXml must beEqualToIgnoringSpace( correctXml ) 
                    }
                }.await
            }
            "from XML test" >> {
              OMIStreamParser.parse(filePath ).map{ parsed => parsed should beEqualTo(request) }.await
            }
          }
      }
    )
  }
}
