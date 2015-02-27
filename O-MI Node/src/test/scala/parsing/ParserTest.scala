package parsing

import org.specs2._
import scala.io.Source
import parsing._
import parsing.Types._
import parsing.Types.Path._
import java.sql.Timestamp

/*
 * Test class for testing parsing parsing package
 * tests e1   - e99 are for testing OmiParser general methods
 * tests e100 - e199 are for testing write request
 * tests e200 - e299 are for testing response messages
 * tests e300 - e399 are for testing read requests
 * tests e400 - e499 are for testing OdfParser class
 */
class ParserTest extends Specification {
  lazy val omi_subscription_test_file = Source.fromFile("src/test/resources/parsing/omi_subscription_test.xml").getLines.mkString("\n")
  lazy val omi_read_test_file = Source.fromFile("src/test/resources/parsing/omi_read_test.xml").getLines.mkString("\n")
  lazy val omi_write_test_file = Source.fromFile("src/test/resources/parsing/omi_write_test.xml").getLines.mkString("\n")
  lazy val omi_response_test_file = Source.fromFile("src/test/resources/parsing/omi_response_test.xml").getLines.mkString("\n")
  lazy val omi_cancel_test_file = Source.fromFile("src/test/resources/parsing/omi_cancel_test.xml").getLines.mkString("\n")
  lazy val odf_test_file = Source.fromFile("src/test/resources/parsing/odf_test.xml").getLines.mkString("\n")
  val write_response_odf = Seq(
    OdfObject(Path("Objects/SmartHouse"),
      Seq(
        OdfObject(Path("Objects/SmartHouse/SmartFridge"),
          Seq(),
          Seq(
            OdfInfoItem(Path("Objects/SmartHouse/SmartFridge/PowerConsumption"),
              Seq(
                TimedValue(
                  None,
                  "56"
                )
              ),
              ""
            )
          )
        ),
        OdfObject(Path("Objects/SmartHouse/SmartOven"),
          Seq(),
          Seq(
            OdfInfoItem(Path("Objects/SmartHouse/SmartOven/PowerOn"),
              Seq(
                TimedValue(
                  Some(Timestamp.valueOf("2014-12-18 15:34:52")),
                  "1"
                )
              ),
              ""
            )
          )
        )
      ),
      Seq(    
        OdfInfoItem(Path("Objects/SmartHouse/PowerConsumption"),
          Seq(
            TimedValue(
              Some(Timestamp.valueOf("2014-12-18 15:34:52")),
              "180"
            )
          ),
          ""
        ),
        OdfInfoItem(Path("Objects/SmartHouse/Moisture"),
          Seq(
            TimedValue(
              Some(new Timestamp(1418916892.toLong*1000)),
              "0.20"
            )
          ),
          ""
        )
      )
    ),
    OdfObject(Path("Objects/SmartCar"),
      Seq(),
      Seq(
        OdfInfoItem(Path("Objects/SmartCar/Fuel"),
          Seq(
            TimedValue(
              Some(Timestamp.valueOf("2014-12-18 15:34:52")),
              "30"
            )
          ),
          ""
        )
      )
    ),
    OdfObject(Path("Objects/SmartCottage"),
      Seq(
        OdfObject(Path("Objects/SmartCottage/Heater"), Seq(), Seq()),
        OdfObject(Path("Objects/SmartCottage/Sauna"), Seq(), Seq()),
        OdfObject(Path("Objects/SmartCottage/Weather"), Seq(), Seq())
      ),
      Seq()
    )
  )
  
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
      missing msgformat   $e201
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
      missing Object id   $e403
      nameless infoitem   $e404

      
    """

  def e1 = {
    val temp = OmiParser.parse("incorrect xml")
    temp.head should be equalTo (ParseError("Invalid XML, schema failure: Content is not allowed in prolog."))

  }

  /*
   * case ParseError("Incorrect prefix :: _ ) matches to list that has that parse error in the head position    
   */
  def e2 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("omi:omiEnvelope", "pmi:omiEnvelope"))
    temp.head.asInstanceOf[ParseError].msg must startWith("Invalid XML, schema failure: The prefix \"pmi\" for")

  }

  def e3 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("omi:omiEnvelope", "omi:Envelope"))
    temp.head.asInstanceOf[ParseError].msg must endWith(
      "Cannot find the declaration of element 'omi:Envelope'.")

  }

  def e4 = {
    val temp = OmiParser.parse(
      """<omi:omiEnvelope ttl="10" version="1.0" xsi:schemaLocation="omi.xsd omi.xsd" xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
         </omi:omiEnvelope>
      """)
    temp.head.asInstanceOf[ParseError].msg must endWith("One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")

  }

  def e5 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("""ttl="10"""", """ttl="""""))
    temp.head.asInstanceOf[ParseError].msg must endWith("'' is not a valid value for 'double'.")

  }

  def e6 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("omi:response", "omi:respnse"))
    temp.head.asInstanceOf[ParseError].msg must endWith("One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")

  }

  def e100 = {
    OmiParser.parse(omi_write_test_file) should be equalTo (List(
      Write("10", write_response_odf, Some("http://testing.test"))))
    //      List(
    //      Write("10", List(
    //        OdfObject(Seq("Objects","SmartHouse","SmartFridge","PowerConsumption"), InfoItem, Some("56"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartHouse/SmartOven/PowerOn", InfoItem, Some("1"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartHouse/PowerConsumption", InfoItem, Some("180"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartHouse/Moisture", InfoItem, Some("0.20"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCar/Fuel", InfoItem, Some("30"), Some("dateTime=\"2014-12-186T15:34:52\""), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCottage/Heater", NodeObject, Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCottage/Sauna", NodeObject, Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0"))),
    //        ODFNode("/Objects/SmartCottage/Weather", NodeObject, Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")), Some( Timestamp.valueOf("2014-12-18 15:34:52.0")))),
    //        "test",
    //        Seq()))
  }
  def e101 = {
    val temp = OmiParser.parse(omi_write_test_file.replace("""omi:write msgformat="odf"""", "omi:write"))
    temp.head should be equalTo (ParseError("No msgformat parameter found in write."))

  }

//  def e102 = {
//    val temp = OmiParser.parse(omi_write_test_file.replace("""msgformat="odf"""", """msgformat="pdf""""))
//    temp.head should be equalTo (ParseError("Unknown message format."))
//  }

  def e103 = {
    val temp = OmiParser.parse(omi_write_test_file.replace("omi:msg", "omi:msn"))
    temp.head should be equalTo (ParseError("Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msn'. One of '{\"omi.xsd\":nodeList, \"omi.xsd\":requestID, \"omi.xsd\":msg}' is expected."))
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
    temp.head should be equalTo (ParseError("No Objects child found in msg."))

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
    temp.head should be equalTo (ParseError("No Objects to parse"))

  }

  def e106 = {
    OmiParser.parse(omi_write_test_file.replace("callback=\"http://testing.test\" ", "")) should be equalTo (List(
      Write("10", write_response_odf)))
  }

  def e200 = {
    OmiParser.parse(omi_response_test_file) should be equalTo (List(
      Result("", "200", Some( write_response_odf))))
  }

  def e201 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("msgformat=\"odf\"", " "))
    temp.head should be equalTo (ParseError("No msgformat parameter found in result."))

  }

//  def e202 = {
//    val temp = OmiParser.parse(omi_response_test_file.replace("""msgformat="odf"""", """msgformat="pdf""""))
//    temp.head should be equalTo (ParseError("Unknown message format."))
//
//  }

  //  def e203 = {
  //    OmiParser.parse(omi_response_test_file.replace("omi:msg", "omi:msn")) match {
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
    temp.head should be equalTo (ParseError("No Objects node in msg, possible but not implemented"))

  }

  def e205 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("<omi:return returnCode=\"200\" />", ""))
    temp.head should be equalTo (ParseError("Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msg'. One of '{\"omi.xsd\":return}' is expected."))

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
    temp.head should be equalTo (ParseError("No Objects to parse"))

  }

  def e207 = {
    val temp = OmiParser.parse(omi_response_test_file.replace("returnCode=\"200\"", ""))
    temp.head should be equalTo (ParseError("Invalid XML, schema failure: cvc-complex-type.4: Attribute 'returnCode' must appear on element 'omi:return'."))
  }

  def e300 = {
    OmiParser.parse(omi_read_test_file) should be equalTo (List(
      OneTimeRead("10", List(
        OdfObject(
          List("Objects", "SmartHouse"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartFridge"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartFridge",
                    "PowerConsumption"),
                  List(),
                  ""))),
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartOven"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartOven",
                    "PowerConsumption"),
                  List(),
                  "")))),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "PowerConsumption"),
              List(),
              ""),
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "Moisture"),
              List(),
              ""))),
        OdfObject(
          List(
            "Objects",
            "SmartCar"),
          List(),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartCar",
                "Fuel"),
              List(),
              ""))),
        OdfObject(
          List(
            "Objects",
            "SmartCottage"),
          List(),
          List()))
      )))
 }

  def e301 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("""omi:read msgformat="odf"""", "omi:read"))
    temp.head should be equalTo (ParseError("No msgformat parameter found in read."))

  }

//  def e302 = {
//    val temp = OmiParser.parse(omi_read_test_file.replace("""msgformat="odf"""", """msgformat="pdf""""))
//    temp.head should be equalTo (ParseError("Unknown message format."))
//
//  }

  def e303 = {
    val temp = OmiParser.parse(omi_read_test_file.replace("omi:msg", "omi:msn"))
    temp.head should be equalTo (ParseError("Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msn'. One of '{\"omi.xsd\":nodeList, \"omi.xsd\":requestID, \"omi.xsd\":msg}' is expected."))

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
    temp.head should be equalTo (ParseError("No Objects child found in msg."))

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
    temp.head should be equalTo (ParseError("No Objects to parse"))

  }

  def e306 = {
    OmiParser.parse(omi_subscription_test_file) should be equalTo (List(
      Subscription("10", 40, List(
        OdfObject(
          List("Objects", "SmartHouse"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartFridge"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartFridge",
                    "PowerConsumption"),
                  List(),
                  ""))),
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartOven"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartOven",
                    "PowerConsumption"),
                  List(),
                  "")))),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "PowerConsumption"),
              List(),
              ""),
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "Moisture"),
              List(),
              ""))),
        OdfObject(
          List(
            "Objects",
            "SmartCar"),
          List(),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartCar",
                "Fuel"),
              List(),
              ""))),
        OdfObject(
          List(
            "Objects",
            "SmartCottage"),
          List(),
          List())),
      Some(Timestamp.valueOf("2014-12-18 15:34:52")),
      Some(Timestamp.valueOf("2014-12-18 15:34:52")),
      Some(5),
      Some(3),
      Some("http://testing.test"),
      Seq("123456","456789")
      )))
  }
  def e401 = {
    val temp = OdfParser.parse("incorrect xml")
    temp.head should be equalTo (Left(ParseError("Invalid XML, schema failure: Content is not allowed in prolog.")))

  }
  def e402 = {
    val temp = OdfParser.parse("""<Object>
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
    temp.head should be equalTo (Left(ParseError("Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'Object'. One of '{id}' is expected.")))

  }
  def e403 = {
    val temp = OdfParser.parse("""
    <Objects>
        <Object>
        <id></id>
        </Object>
    </Objects>
""")
    temp.head should be equalTo (Left(ParseError("id's value not found in Object.")))

  }
  def e404 = {
    val temp = OdfParser.parse("""
    <Objects>
        <Object>
        <id>SmartHouse</id>
        <InfoItem name="">
        </InfoItem>
        </Object>
    </Objects>
""")
    temp.head should be equalTo (Left(ParseError("No name parameter found in InfoItem.")))

  }
  def e400 = {
    OdfParser.parse(odf_test_file)  should be equalTo( write_response_odf.map( o => Right(o) ))
  }

  def e500 = {
    OmiParser.parse(omi_cancel_test_file)  should be equalTo(Seq(Cancel("10", Seq("123","456"))))
  }


}



