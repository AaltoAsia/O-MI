package parsing

import org.specs2._
import scala.io.Source
import parsing._
import types._
import types.OmiTypes._
import types.OdfTypes._
import types.Path._
import java.sql.Timestamp
import scala.xml.Utility.trim
//import java.lang.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable

import scala.concurrent.duration._
import testHelpers.DeactivatedTimeConversions

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
/*
 * Test class for testing parsing parsing package
 * tests e1   - e99 are for testing OmiParser general methods
 * tests e100 - e199 are for testing write request
 * tests e200 - e299 are for testing response messages
 * tests e300 - e399 are for testing read requests
 * tests e400 - e499 are for testing OdfParser class
 */

@RunWith(classOf[JUnitRunner])
class ParserTest extends Specification with DeactivatedTimeConversions {
  //  lazy val omi_subscription_test_file = Source.fromFile("src/test/resources/parsing/omi_subscription_test.xml").getLines.mkString("\n")
  //  lazy val omi_read_test_file = Source.fromFile("src/test/resources/parsing/omi_read_test.xml").getLines.mkString("\n")
  //  lazy val omi_write_test_file = Source.fromFile("src/test/resources/parsing/omi_write_test.xml").getLines.mkString("\n")
  //  lazy val omi_response_test_file = Source.fromFile("src/test/resources/parsing/omi_response_test.xml").getLines.mkString("\n")
  //  lazy val omi_cancel_test_file = Source.fromFile("src/test/resources/parsing/omi_cancel_test.xml").getLines.mkString("\n")
  //  lazy val odf_test_file = Source.fromFile("src/test/resources/parsing/odf_test.xml").getLines.mkString("\n")
  val write_response_odf: OdfObjects = {
    /*Right(
      Iterable(
        WriteRequest(
          10.0, */ OdfObjects(
      Iterable(
        OdfObject(
          Path("Objects/SmartHouse"), Iterable(
            OdfInfoItem(
              Path("Objects/SmartHouse/PowerConsumption"), Iterable(
                OdfValue(
                  "180", "xs:string", Some(
                    Timestamp.valueOf("2014-12-18 15:34:52")))), None, None), OdfInfoItem(
              Path("Objects/SmartHouse/Moisture"), Iterable(
                OdfValue(
                  "0.20", "xs:string", Some(
                    new Timestamp(1418916892L * 1000)))), None, None)), Iterable(
            OdfObject(
              Path("Objects/SmartHouse/SmartFridge"), Iterable(
                OdfInfoItem(
                  Path("Objects/SmartHouse/SmartFridge/PowerConsumption"), Iterable(
                    OdfValue(
                      "56", "xs:string", Some(
                        Timestamp.valueOf("2014-12-18 15:34:52")))), None, None)), Iterable(), None, None), OdfObject(
              Path("Objects/SmartHouse/SmartOven"), Iterable(
                OdfInfoItem(
                  Path("Objects/SmartHouse/SmartOven/PowerOn"), Iterable(
                    OdfValue(
                      "1", "xs:string", Some(
                        Timestamp.valueOf("2014-12-18 15:34:52")))), None, None)), Iterable(), None, None)), None, None), OdfObject(
          Path("Objects/SmartCar"), Iterable(
            OdfInfoItem(
              Path("Objects/SmartCar/Fuel"), Iterable(
                OdfValue(
                  "30", "xs:string", Some(
                    Timestamp.valueOf("2014-12-18 15:34:52")))), None, Some(
                OdfMetaData(
                  """<MetaData xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><InfoItem name="Units"><value type="xs:String">Litre</value></InfoItem></MetaData>""")))), Iterable(), None, None), OdfObject(
          Path("Objects/SmartCottage"), Iterable(), Iterable(
            OdfObject(
              Path("Objects/SmartCottage/Heater"), Iterable(), Iterable(), None, None), OdfObject(
              Path("Objects/SmartCottage/Sauna"), Iterable(), Iterable(), None, None), OdfObject(
              Path("Objects/SmartCottage/Weather"), Iterable(), Iterable(), None, None)), None, None)), None)
  }
  val readOdf: OdfObjects = {
    OdfObjects(
      Iterable(
        OdfObject(
          Path("Objects/SmartHouse"),
          Iterable(
            OdfInfoItem(
              Path("Objects/SmartHouse/PowerConsumption"),
              Iterable(),
              None,
              None),
            OdfInfoItem(
              Path("Objects/SmartHouse/Moisture"),
              Iterable(),
              None,
              None)),
          Iterable(
            OdfObject(
              Path("Objects/SmartHouse/SmartFridge"),
              Iterable(
                OdfInfoItem(
                  Path("Objects/SmartHouse/SmartFridge/PowerConsumption"),
                  Iterable(),
                  None,
                  None)),
              Iterable(),
              None,
              None),
            OdfObject(
              Path("Objects/SmartHouse/SmartOven"),
              Iterable(
                OdfInfoItem(
                  Path("Objects/SmartHouse/SmartOven/PowerConsumption"),
                  Iterable(),
                  None,
                  None)),
              Iterable(),
              None,
              None)),
          None,
          None),
        OdfObject(
          Path("Objects/SmartCar"),
          Iterable(
            OdfInfoItem(
              Path("Objects/SmartCar/Fuel"),
              Iterable(),
              None,
              None)),
          Iterable(),
          None,
          None),
        OdfObject(
          Path("Objects/SmartCottage"),
          Iterable(),
          Iterable(),
          None,
          None)),
      None)
  }
  /*
    OdfObject(Path("Objects/SmartHouse"),
      Iterable(
        OdfObject(Path("Objects/SmartHouse/SmartFridge"),
          Iterable(),
          Iterable(
            OdfInfoItem(Path("Objects/SmartHouse/SmartFridge/PowerConsumption"),
              Iterable(
                TimedValue(
                  None,
                  "56"
                )
              )
            )
          )
        ),
        OdfObject(Path("Objects/SmartHouse/SmartOven"),
          Iterable(),
          Iterable(
            OdfInfoItem(Path("Objects/SmartHouse/SmartOven/PowerOn"),
              Iterable(
                TimedValue(
                  Some(Timestamp.valueOf("2014-12-18 15:34:52")),
                  "1"
                )
              )            )
          )
        )
      ),
      Iterable(    
        OdfInfoItem(Path("Objects/SmartHouse/PowerConsumption"),
          Iterable(
            TimedValue(
              Some(Timestamp.valueOf("2014-12-18 15:34:52")),
              "180"
            )
          )
        ),
        OdfInfoItem(Path("Objects/SmartHouse/Moisture"),
          Iterable(
            TimedValue(
              Some(new Timestamp(1418916892.toLong*1000)),
              "0.20"
            )
          )
      )
      )
    ),
    OdfObject(Path("Objects/SmartCar"),
      Iterable(),
      Iterable(
        OdfInfoItem(Path("Objects/SmartCar/Fuel"),
          Iterable(
            TimedValue(
              Some(Timestamp.valueOf("2014-12-18 15:34:52")),
              "30")
            ),
              Some(InfoItemMetaData(trim(
	    <MetaData>
		<InfoItem name="Units">
		    <value type="xs:String">Litre</value>
		</InfoItem>
	    </MetaData>).toString
              )
          )
        )
      )
    ),
    OdfObject(Path("Objects/SmartCottage"),
      Iterable(
        OdfObject(Path("Objects/SmartCottage/Heater"), Iterable(), Iterable()),
        OdfObject(Path("Objects/SmartCottage/Sauna"), Iterable(), Iterable()),
        OdfObject(Path("Objects/SmartCottage/Weather"), Iterable(), Iterable())
      ),
      Iterable()
    )*/

  def is = s2"""
  This is Specification to check the parsing functionality.

  OmiParser should give certain result for
    message with
      incorrect XML       $e1
      incorrect prefix    $e2
      incorrect label     $e3
      missing request     $e4
      missing ttl         $e5
      unknown omi message $e6
    write request with
      correct message     $e100
      missing msgformat   $e101
      missing omi:msg     $e103
      missing Objects     $e104 
      no objects to parse $e105
      correct without callback $e106
    response message with
      correct message     $e200
      missing Objects     $e204
      missing result node $e205
      no objects to parse $e206
      missing return code $e207
    read request with
      correct message     $e300
      missing msgformat   $e301
      missing omi:msg     $e303
      missing Objects     $e304
      no objects to parse $e305
      correct subscription $e306
    cancel request with
      correct request     $e500 
  OdfParser should give certain result for
    message with
      correct format      $e400
      incorrect XML       $e401
      incorrect label     $e402

      
    """

  def e1 = {
    val temp = OmiParser.parse("incorrect xml")
    temp should be equalTo Left(Iterable(ParseError("OmiParser: Invalid XML")))

  }

  /*
   * case ParseError("Incorrect prefix :: _ ) matches to list that has that parse error in the head position    
   */
  def e2 = {
    val temp = OmiParser.parse(omiReadTest.replace("omi:omiEnvelope", "pmi:omiEnvelope"))
    temp.isLeft === true
    temp.left.get.head.msg must startWith("OmiParser: Invalid XML, schema failure: The prefix \"pmi\" for")

  }

  def e3 = {
    val temp = OmiParser.parse(omiReadTest.replace("omi:omiEnvelope", "omi:Envelope"))
    temp.isLeft === true
    temp.left.get.head.msg must endWith(
      "Cannot find the declaration of element 'omi:Envelope'.")

  }

  def e4 = {
    val temp = OmiParser.parse(
      """<omi:omiEnvelope ttl="10" version="1.0" xsi:schemaLocation="omi.xsd omi.xsd" xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
         </omi:omiEnvelope>
      """)
    temp.isLeft === true
    temp.left.get.head.msg must endWith("One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")

    //    temp.head.asInstanceOf[ParseError].msg must endWith("One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")

  }

  def e5 = {
    val temp = OmiParser.parse(omiReadTest.replace("""ttl="10"""", """ttl="""""))
    temp.isLeft === true
    temp.left.get.head.msg must endWith("'' is not a valid value for 'double'.")

    //    temp.head.asInstanceOf[ParseError].msg must endWith("'' is not a valid value for 'double'.")

  }

  def e6 = {
    val temp = OmiParser.parse(omiResponseTest.replace("omi:response", "omi:respnse"))
    temp.isLeft === true
    temp.left.get.head.msg must endWith("One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")

    //    temp.head.asInstanceOf[ParseError].msg must endWith("One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")

  }

  def e100 = {
    OmiParser.parse(omiWriteTest) should be equalTo Right(Iterable(WriteRequest(10.0.seconds, write_response_odf, Some("http://testing.test"))))
    //    Right(Iterable(WriteRequest(10.seconds, OdfObjects(Iterable(), Some("test")), Some("http://testing.test"))))
    //      Iterable(
    //      Write("10", Iterable(
    //        OdfObject(Iterable("Objects","SmartHouse","SmartFridge","PowerConsumption"), InfoItem, Some("56"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartHouse/SmartOven/PowerOn", InfoItem, Some("1"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartHouse/PowerConsumption", InfoItem, Some("180"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartHouse/Moisture", InfoItem, Some("0.20"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCar/Fuel", InfoItem, Some("30"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCottage/Heater", NodeObject, Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCottage/Sauna", NodeObject, Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCottage/Weather", NodeObject, Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")))),
    //        "test",
    //        Iterable()))
  }
  def e101 = {
    val temp = OmiParser.parse(omiWriteTest.replace("""omi:write msgformat="odf"""", "omi:write"))
    temp.isLeft === true
    temp.left.get.iterator().next() should be equalTo ParseError("OmiParser: Missing msgformat attribute.")

    //    temp.head should be equalTo (ParseError("No msgformat parameter found in write."))

  }

  //  def e102 = {
  //    val temp = OmiParser.parse(omiWriteTest.replace("""msgformat="odf"""", """msgformat="pdf""""))
  //    temp.head should be equalTo (ParseError("Unknown message format."))
  //  }

  def e103 = {
    val temp = OmiParser.parse(omiWriteTest.replace("omi:msg", "omi:msn"))
    temp.isLeft === true
    temp.left.get.iterator().next() should be equalTo ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msn'. One of '{\"omi.xsd\":nodeList, \"omi.xsd\":requestID, \"omi.xsd\":msg}' is expected.")

    //    temp.head should be equalTo (ParseError("Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msn'. One of '{\"omi.xsd\":nodeList, \"omi.xsd\":requestID, \"omi.xsd\":msg}' is expected."))
  }

  def e104 = {
    val temp = OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:write msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
""")
    temp.isLeft === true
    temp.left.get.iterator().next() should be equalTo ParseError("No Objects child found in msg.")

    //    temp.head should be equalTo (ParseError("No Objects child found in msg."))

  }

  def e105 = {
    val temp = OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:write msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
""")
    temp.isRight === true
    //    temp.right.get.iterator().next() match{
    //      case WriteRequest(a,b,c) => println(a);println(b.getClass);println(b.eq(OdfObjects()))
    //      case _ => println("WWWWWWWWWWWWWWWWWWWWWWWW\n\n\n")
    //    }
    temp.right.get.iterator().next() should be equalTo WriteRequest(10.0.seconds, OdfObjects())

  }

  def e106 = {
    OmiParser.parse(omiWriteTest.replaceAll("callback=\"http://testing.test\"", "")) should be equalTo Right(Iterable(WriteRequest(10.0.seconds, write_response_odf)))
  }

  def e200 = {
    val temp = OmiParser.parse(omiResponseTest)
    temp.isRight === true
    temp.right.get.iterator().next() should be equalTo ResponseRequest(Iterable(OmiResult("", "200", None, Iterable.empty[Long], Some(write_response_odf))))
    //    OmiParser.parse(omiResponseTest) should be equalTo Right(Iterable(
    //      ResponseRequest(Iterable(OmiResult("","200", None, seqAsJavaList(Seq.empty),Some(write_response_odf))))))
  }

  /*
  //Missing msgformat is allowed
  def e201 = {
    val temp = OmiParser.parse(omiResponseTest.replace("msgformat=\"odf\"", " "))
    temp.head should be equalTo (ParseError("No msgformat parameter found in result."))

  }
  */

  //  def e202 = {
  //    val temp = OmiParser.parse(omiResponseTest.replace("""msgformat="odf"""", """msgformat="pdf""""))
  //    temp.head should be equalTo (ParseError("Unknown message format."))
  //
  //  }

  //  def e203 = {
  //    OmiParser.parse(omiResponseTest.replace("omi:msg", "omi:msn")) match {
  //      case ParseError("No message node found in response node.") :: _ => true
  //      case _ => false
  //    }
  //  }

  def e204 = {
    val temp = OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:response>
      <omi:result msgformat="odf" > 
      <omi:return returnCode="200" /> 
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
      </omi:result> 
  </omi:response>
</omi:omiEnvelope>
""")
    temp should be equalTo Left(Iterable(ParseError("No Objects child found in msg.")))

  }

  def e205 = {
    val temp = OmiParser.parse(omiResponseTest.replace("<omi:return returnCode=\"200\"/>", ""))
    temp.isLeft === true

    temp.left.get.head should be equalTo ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msg'. One of '{\"omi.xsd\":return}' is expected.")

  }

  def e206 = {
    val temp = OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:response>
      <omi:result msgformat="odf" > 
      <omi:return returnCode="200" />
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
      </omi:result> 
  </omi:response>
</omi:omiEnvelope>
""")
    temp.isRight === true

    temp.right.get.head should be equalTo ResponseRequest(Iterable(OmiResult("", "200", None, asJavaIterable(Iterable.empty[Long]), Some(OdfObjects(asJavaIterable(Iterable.empty[OdfObject]))))))

  }

  def e207 = {
    val temp = OmiParser.parse(omiResponseTest.replace("returnCode=\"200\"", ""))
    temp.isLeft === true
    temp.left.get.head should be equalTo ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.4: Attribute 'returnCode' must appear on element 'omi:return'.")
  }

  def e300 = {
    val temp = OmiParser.parse(omiReadTest) // should be equalTo Right(Iterable(ResponseRequest(Iterable(OmiResult("", "")))))

    temp.isRight === true
    temp.right.get.head should be equalTo ReadRequest(10.0.seconds, readOdf)

    //        Iterable(
    //      OneTimeRead(10, Iterable(
    //        OdfObject(
    //          Iterable("Objects", "SmartHouse"),
    //          Iterable(
    //            OdfObject(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "SmartFridge"),
    //              Iterable(),
    //              Iterable(
    //                OdfInfoItem(
    //                  Iterable(
    //                    "Objects",
    //                    "SmartHouse",
    //                    "SmartFridge",
    //                    "PowerConsumption"),
    //                  Iterable()
    //                ))),
    //            OdfObject(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "SmartOven"),
    //              Iterable(),
    //              Iterable(
    //                OdfInfoItem(
    //                  Iterable(
    //                    "Objects",
    //                    "SmartHouse",
    //                    "SmartOven",
    //                    "PowerConsumption"),
    //                  Iterable()
    //                )))),
    //          Iterable(
    //            OdfInfoItem(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "PowerConsumption"),
    //              Iterable()
    //            ),
    //            OdfInfoItem(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "Moisture"),
    //              Iterable()
    //            ))),
    //        OdfObject(
    //          Iterable(
    //            "Objects",
    //            "SmartCar"),
    //          Iterable(),
    //          Iterable(
    //            OdfInfoItem(
    //              Iterable(
    //                "Objects",
    //                "SmartCar",
    //                "Fuel"),
    //              Iterable()
    //            ))),
    //        OdfObject(
    //          Iterable(
    //            "Objects",
    //            "SmartCottage"),
    //          Iterable(),
    //          Iterable()))
    //      )))
  }

  def e301 = {
    val temp = OmiParser.parse(omiReadTest.replace("""omi:read msgformat="odf"""", "omi:read"))
    temp should be equalTo Left(Iterable(ParseError("OmiParser: Missing msgformat attribute.")))

  }

  //  def e302 = {
  //    val temp = OmiParser.parse(omiReadTest.replace("""msgformat="odf"""", """msgformat="pdf""""))
  //    temp.head should be equalTo (ParseError("Unknown message format."))
  //
  //  }

  def e303 = {
    val temp = OmiParser.parse(omiReadTest.replace("omi:msg", "omi:msn"))
    temp.isLeft === true
    temp.left.get.head should be equalTo ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msn'. One of '{\"omi.xsd\":nodeList, \"omi.xsd\":requestID, \"omi.xsd\":msg}' is expected.")

  }

  def e304 = {
    val temp = OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:read msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:read>
</omi:omiEnvelope>
""")
    temp should be equalTo Left(Iterable(ParseError("No Objects child found in msg.")))

  }

  def e305 = {
    val temp = OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:read msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
  </omi:read>
</omi:omiEnvelope>
""")
    temp should be equalTo Right(Iterable(ReadRequest(10.0.seconds, OdfObjects())))

  }

  def e306 = {
    val omiSubscriptionTest =
      """<?xml version="1.0" encoding="UTF-8"?>
      <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
        <omi:read msgformat="odf" interval="40.0" callback="http://testing.test" begin="2014-12-18T15:34:52" end="2014-12-18T15:34:52" oldest="3" newest="5">
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects>
              <Object>
                <id>SmartHouse</id>
                <InfoItem name="PowerConsumption">
                </InfoItem>
                <InfoItem name="Moisture">
                </InfoItem>
                <Object>
                  <id>SmartFridge</id>
                  <InfoItem name="PowerConsumption">
                  </InfoItem>
                </Object>
                <Object>
                  <id>SmartOven</id>
                  <InfoItem name="PowerConsumption">
                  </InfoItem>
                </Object>
              </Object>
              <Object>
                <id>SmartCar</id>
                <InfoItem name="Fuel">
                </InfoItem>
              </Object>
              <Object>
                <id>SmartCottage</id>
              </Object>
            </Objects>
          </omi:msg>
        </omi:read>
      </omi:omiEnvelope>"""

    val temp = OmiParser.parse(omiSubscriptionTest)
    temp.isRight === true
    temp.right.get.head should be equalTo SubscriptionRequest(10.0.seconds, 40.seconds, readOdf, Some(5), Some(3), Some("http://testing.test"))
    //      SubscriptionRequest(1.seconds, 2, read)))
    //      Subscription(10, 40, Iterable(
    //        OdfObject(
    //          Iterable("Objects", "SmartHouse"),
    //          Iterable(
    //            OdfObject(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "SmartFridge"),
    //              Iterable(),
    //              Iterable(
    //                OdfInfoItem(
    //                  Iterable(
    //                    "Objects",
    //                    "SmartHouse",
    //                    "SmartFridge",
    //                    "PowerConsumption"),
    //                  Iterable()
    //                ))),
    //            OdfObject(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "SmartOven"),
    //              Iterable(),
    //              Iterable(
    //                OdfInfoItem(
    //                  Iterable(
    //                    "Objects",
    //                    "SmartHouse",
    //                    "SmartOven",
    //                    "PowerConsumption"),
    //                  Iterable()
    //                )))),
    //          Iterable(
    //            OdfInfoItem(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "PowerConsumption"),
    //              Iterable()
    //            ),
    //            OdfInfoItem(
    //              Iterable(
    //                "Objects",
    //                "SmartHouse",
    //                "Moisture"),
    //              Iterable()
    //            ))),
    //        OdfObject(
    //          Iterable(
    //            "Objects",
    //            "SmartCar"),
    //          Iterable(),
    //          Iterable(
    //            OdfInfoItem(
    //              Iterable(
    //                "Objects",
    //                "SmartCar",
    //                "Fuel"),
    //              Iterable()
    //            ))),
    //        OdfObject(
    //          Iterable(
    //            "Objects",
    //            "SmartCottage"),
    //          Iterable(),
    //          Iterable())),
    //      Some("http://testing.test")
    //      )))
  }
  def e400 = {
    val odfTest =
      """<?xml version="1.0" encoding="UTF-8"?>
      <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="odf.xsd" xs:schemaLocation="odf.xsd odf.xsd">
        <Object>
          <id>SmartHouse</id>
          <InfoItem name="PowerConsumption">
            <value dateTime="2014-12-18T15:34:52">180</value>
          </InfoItem>
          <InfoItem name="Moisture">
            <value unixTime="1418916892">0.20</value>
          </InfoItem>
          <Object>
            <id>SmartFridge</id>
            <InfoItem name="PowerConsumption">
              <value dateTime="2014-12-18T15:34:52">56</value>
            </InfoItem>
          </Object>
          <Object>
            <id>SmartOven</id>
            <InfoItem name="PowerOn">
              <value dateTime="2014-12-18T15:34:52">1</value>
            </InfoItem>
          </Object>
        </Object>
        <Object>
          <id>SmartCar</id>
          <InfoItem name="Fuel">
            <MetaData>
              <InfoItem name="Units">
                <value type="xs:String">Litre</value>
              </InfoItem>
            </MetaData>
            <value dateTime="2014-12-18T15:34:52">30</value>
          </InfoItem>
        </Object>
        <Object>
          <id>SmartCottage</id>
          <Object>
            <id>Heater</id>
          </Object>
          <Object>
            <id>Sauna</id>
          </Object>
          <Object>
            <id>Weather</id>
          </Object>
        </Object>
      </Objects>"""

    val temp = OdfParser.parse(odfTest)
    temp.isRight === true
    temp.right.get should be equalTo write_response_odf
    //    OdfParser.parse(odf_test_file)  should be equalTo( write_response_odf.map( o => Right(o) ))
  }

  def e401 = {
    val temp = OdfParser.parse("incorrect xml")
    temp should be equalTo Left(Iterable(ParseError("Invalid XML")))

  }
  def e402 = {
    val temp = OdfParser.parse("""
      <Object>
        <Object>
      <id>SmartHouse</id>
      <InfoItem name="PowerConsumption">
      </InfoItem>
      <InfoItem name="Moisture">
      </InfoItem>
        </Object>
        <Object>
      <id>SmartCar</id>
      <InfoItem name="Fuel">
      </InfoItem>
        </Object>
        <Object>
      <id>SmartCottage</id>
        </Object>
    </Object>
""")
    temp should be equalTo Left(Iterable(ParseError("OdfParser: Invalid XML, schema failure: cvc-elt.1: Cannot find the declaration of element 'Object'.")))

  }
  // empty id seems to be ok
  //  def e403 = {
  //    val temp = OdfParser.parse("""
  //    <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="odf.xsd" xs:schemaLocation="odf.xsd odf.xsd">
  //        <Object>
  //        <id></id>
  //        </Object>
  //    </Objects>
  //""")
  //    temp should be equalTo Left(Iterable(ParseError("OdfParser: id's value not found in Object.")))
  //
  //  }
  // empty name for infoItem seems to be ok
  //  def e404 = {
  //    val temp = OdfParser.parse("""
  //    <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="odf.xsd" xs:schemaLocation="odf.xsd odf.xsd">
  //        <Object>
  //        <id>SmartHouse</id>
  //        <InfoItem name="">
  //        </InfoItem>
  //        </Object>
  //    </Objects>
  //""")
  //    temp should be equalTo Left(Iterable(ParseError("No name parameter found in InfoItem.")))
  //
  //  }

  def e500 = {
    val omiCancelTest =
      """<?xml version="1.0" encoding="UTF-8"?>
    <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:cancel>
        <omi:requestID>123</omi:requestID>
        <omi:requestID>456</omi:requestID>
      </omi:cancel>
    </omi:omiEnvelope>"""
    val temp = OmiParser.parse(omiCancelTest)
    temp.isRight === true
    val temp2 = temp.right.get.head.asInstanceOf[CancelRequest]
    //Some type problem here with iterators
    temp2 should be equalTo CancelRequest(10.0.seconds, asJavaIterable(Iterable(123, 456)))
  }

  lazy val omiReadTest =
    """<?xml version="1.0" encoding="UTF-8"?>
    <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:read msgformat="odf">
        <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
          <Objects>
            <Object>
              <id>SmartHouse</id>
              <InfoItem name="PowerConsumption">
              </InfoItem>
              <InfoItem name="Moisture">
              </InfoItem>
              <Object>
                <id>SmartFridge</id>
                <InfoItem name="PowerConsumption">
                </InfoItem>
              </Object>
              <Object>
                <id>SmartOven</id>
                <InfoItem name="PowerConsumption">
                </InfoItem>
              </Object>
            </Object>
            <Object>
              <id>SmartCar</id>
              <InfoItem name="Fuel">
              </InfoItem>
            </Object>
            <Object>
              <id>SmartCottage</id>
            </Object>
          </Objects>
        </omi:msg>
      </omi:read>
    </omi:omiEnvelope>"""

  lazy val omiWriteTest =
    """<?xml version="1.0" encoding="UTF-8"?>
    <omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:write msgformat="odf" callback="http://testing.test">
        <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
          <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="odf.xsd" xs:schemaLocation="odf.xsd odf.xsd">
            <Object>
              <id>SmartHouse</id>
              <InfoItem name="PowerConsumption">
                <value dateTime="2014-12-18T15:34:52">180</value>
              </InfoItem>
              <InfoItem name="Moisture">
                <value unixTime="1418916892">0.20</value>
              </InfoItem>
              <Object>
                <id>SmartFridge</id>
                <InfoItem name="PowerConsumption">
                  <value dateTime="2014-12-18T15:34:52">56</value>
                </InfoItem>
              </Object>
              <Object>
                <id>SmartOven</id>
                <InfoItem name="PowerOn">
                  <value dateTime="2014-12-18T15:34:52">1</value>
                </InfoItem>
              </Object>
            </Object>
            <Object>
              <id>SmartCar</id>
              <InfoItem name="Fuel">
                <MetaData>
                  <InfoItem name="Units">
                    <value type="xs:String">Litre</value>
                  </InfoItem>
                </MetaData>
                <value dateTime="2014-12-18T15:34:52">30</value>
              </InfoItem>
            </Object>
            <Object>
              <id>SmartCottage</id>
              <Object>
                <id>Heater</id>
              </Object>
              <Object>
                <id>Sauna</id>
              </Object>
              <Object>
                <id>Weather</id>
              </Object>
            </Object>
          </Objects>
        </omi:msg>
      </omi:write>
    </omi:omiEnvelope>"""

  lazy val omiResponseTest =
    """<?xml version="1.0" encoding="UTF-8"?>
    <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
      <omi:response>
        <omi:result msgformat="odf">
          <omi:return returnCode="200"/>
          <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
            <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="odf.xsd" xs:schemaLocation="odf.xsd odf.xsd">
              <Object>
                <id>SmartHouse</id>
                <InfoItem name="PowerConsumption">
                  <value dateTime="2014-12-18T15:34:52">180</value>
                </InfoItem>
                <InfoItem name="Moisture">
                  <value unixTime="1418916892">0.20</value>
                </InfoItem>
                <Object>
                  <id>SmartFridge</id>
                  <InfoItem name="PowerConsumption">
                    <value dateTime="2014-12-18T15:34:52">56</value>
                  </InfoItem>
                </Object>
                <Object>
                  <id>SmartOven</id>
                  <InfoItem name="PowerOn">
                    <value dateTime="2014-12-18T15:34:52">1</value>
                  </InfoItem>
                </Object>
              </Object>
              <Object>
                <id>SmartCar</id>
                <InfoItem name="Fuel">
                  <MetaData>
                    <InfoItem name="Units">
                      <value type="xs:String">Litre</value>
                    </InfoItem>
                  </MetaData>
                  <value dateTime="2014-12-18T15:34:52">30</value>
                </InfoItem>
              </Object>
              <Object>
                <id>SmartCottage</id>
                <Object>
                  <id>Heater</id>
                </Object>
                <Object>
                  <id>Sauna</id>
                </Object>
                <Object>
                  <id>Weather</id>
                </Object>
              </Object>
            </Objects>
          </omi:msg>
        </omi:result>
      </omi:response>
    </omi:omiEnvelope>"""

}



