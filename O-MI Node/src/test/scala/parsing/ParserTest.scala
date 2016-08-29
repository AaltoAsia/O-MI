package parsing

import org.specs2._
import scala.io.Source
import parsing._
import parsing.xmlGen.xmlTypes.{ValueType, InfoItemType, MetaData, QlmID}
import types._
import types.OmiTypes._
import types.OdfTypes.OdfTreeCollection._
import types.OdfTypes._
import types.Path._
import java.sql.Timestamp
import scala.xml.Utility.trim
import scala.xml.NodeSeq
//import java.lang.Iterable
import scala.collection.JavaConversions.asJavaIterable
import scala.collection.JavaConversions.seqAsJavaList
import scala.collection.JavaConversions.iterableAsScalaIterable

import scala.concurrent.duration._

import org.specs2.matcher._
import org.specs2.matcher.XmlMatchers._
/*
 * Test class for testing parsing parsing package
 * tests e1   - e99 are for testing OmiParser general methods
 * tests e100 - e199 are for testing write request
 * tests e200 - e299 are for testing response messages
 * tests e300 - e399 are for testing read requests
 * tests e400 - e499 are for testing OdfParser class
 */

class ParserTest extends Specification {
  val write_response_odf: OdfObjects = {
    /*Right(
      Iterable(
        WriteRequest(
          10.0, */ OdfObjects(
      Iterable(
        OdfObject(
        Seq(),
          Path("Objects/SmartHouse"), Iterable(
            OdfInfoItem(
              Path("Objects/SmartHouse/PowerConsumption"), Iterable(
                OdfValue(
                  "180", "xs:string",
                    Timestamp.valueOf("2014-12-18 15:34:52"))), None, None), OdfInfoItem(
              Path("Objects/SmartHouse/Moisture"), Iterable(
                OdfValue(
                  "0.20", "xs:string",
                    new Timestamp(1418916892L * 1000))), None, None)), Iterable(
            OdfObject(
            Seq(),
              Path("Objects/SmartHouse/SmartFridge"), Iterable(
                OdfInfoItem(
                  Path("Objects/SmartHouse/SmartFridge/PowerConsumption"), Iterable(
                    OdfValue(
                      "56", "xs:string",
                        Timestamp.valueOf("2014-12-18 15:34:52"))), None, None)), Iterable(), None, None), OdfObject(
            Seq(),
              Path("Objects/SmartHouse/SmartOven"), Iterable(
                OdfInfoItem(
                  Path("Objects/SmartHouse/SmartOven/PowerOn"), Iterable(
                    OdfValue(
                      "1", "xs:string",
                        Timestamp.valueOf("2014-12-18 15:34:52"))), None, None)), Iterable(), None, None)), None, None), OdfObject(
        Seq(),
          Path("Objects/SmartCar"), Iterable(
            OdfInfoItem(
              Path("Objects/SmartCar/Fuel"),
              Vector(OdfValue(
                  "30",
                  "xs:string",
                  Timestamp.valueOf("2014-12-18 15:34:52")
              )), 
              None, 
              Some(OdfMetaData(
                Vector(OdfInfoItem(
                  Path("Objects/SmartCar/Fuel/MetaData/Units"),
                  Vector(OdfValue(
                    "Litre",
                    "xs:string",
                    Timestamp.valueOf("2014-12-18 15:34:52")
                  ))
                ))
              ))
            )),
          Iterable(), None, None), OdfObject(
        Seq(),
          Path("Objects/SmartCottage"), Iterable(), Iterable(
            OdfObject(
            Seq(),
              Path("Objects/SmartCottage/Heater"), Iterable(), Iterable(), None, None), OdfObject(
            Seq(),
              Path("Objects/SmartCottage/Sauna"), Iterable(), Iterable(), None, None), OdfObject(
            Seq(),
              Path("Objects/SmartCottage/Weather"), Iterable(), Iterable(), None, None)), None, None)), None)
  }
  val readOdf: OdfObjects = {
    OdfObjects(
      Iterable(
        OdfObject(
        Seq(),
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
            Seq(),
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
            Seq(),
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
        Seq(),
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
          Nil,
          Path("Objects/SmartCottage"),
          Iterable(),
          Iterable(),
          None,
          None)),
      None)
  }

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
    invalidOmiTest(
      "incorrect xml",
      Set(
        ParseError("OmiParser: Invalid XML: Content is not allowed in prolog.")
      )
    )
  }

  /*
   * case ParseError("Incorrect prefix :: _ ) matches to list that has that parse error in the head position    
   */
  def e2 = {
    invalidOmiTest(
      omiReadTest.replace("omi:omiEnvelope", "pmi:omiEnvelope"),
      Set(
        ParseError("OmiParser: Invalid XML, schema failure: The prefix \"pmi\" for element \"pmi:omiEnvelope\" is not bound.")
      )
    )

  }

  def e3 = {
    invalidOmiTest(
      omiReadTest.replace("omi:omiEnvelope", "omi:Envelope"),
      Set(
        ParseError("OmiParser: Invalid XML, schema failure: cvc-elt.1: Cannot find the declaration of element \'omi:Envelope\'.")
      )
    )

  }

  def e4 = {
    invalidOmiTest(
      "<omi:omiEnvelope ttl=\"10\" version=\"1.0\" xsi:schemaLocation=\"omi.xsd omi.xsd\" xmlns:omi=\"omi.xsd\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"></omi:omiEnvelope>",
      Set(
        ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.2.4.b: The content of element 'omi:omiEnvelope' is not complete. One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")
      )
    )
  }

  def e5 = {
    invalidOmiTest(
      omiReadTest.replace("ttl=\"10\"", "ttl=\"\""),
      Set(
        ParseError("OmiParser: Invalid XML, schema failure: cvc-datatype-valid.1.2.1: '' is not a valid value for 'double'.")
      )
    )
  }

  def e6 = {
    val temp = "daer" 
    invalidOmiTest(
      omiReadTest.replace("omi:read", s"omi:$temp"),
      Set(
        ParseError(s"OmiParser: Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:$temp'." + " One of '{\"omi.xsd\":read, \"omi.xsd\":write, \"omi.xsd\":response, \"omi.xsd\":cancel}' is expected.")
      )
    )
  }

  def e100 = {
    validOmiTest(writeRequestTest) 
  }

  def e101 = {
    invalidOmiTest(
      omiWriteTest.toString.replace("omi:write msgformat=\"odf\"", "omi:write"),
      Set(ParseError("OmiParser: Missing msgformat attribute."))
    ) 
  }

  //  def e102 = {
  //    val temp = OmiParser.parse(omiWriteTest.toString.replace("""msgformat="odf"""", """msgformat="pdf""""))
  //    temp.head should be equalTo (ParseError("Unknown message format."))
  //  }

  def e103 = {
    invalidOmiTest(
      omiWriteTest.toString.replace("omi:msg", "omi:msn"),
      Set(ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.2.4.a: Invalid content was found starting with element 'omi:msn'. One of '{\"omi.xsd\":nodeList, \"omi.xsd\":requestID, \"omi.xsd\":msg}' is expected."))
    ) 
  }

  def e104 = {
    val temp = <omi:omiEnvelope ttl="10.0" version="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><omi:write msgformat="odf">
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
    invalidOmiTest(
      temp, 
      Set(ParseError("No Objects child found in msg."))
    )


  }

  def e105 = {
    val temp = <omi:omiEnvelope ttl="10.0" version="1.0" xmlns="odf.xsd" xmlns:omi="omi.xsd" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><omi:write msgformat="odf">
          <omi:msg>
        <Objects/>
          </omi:msg>
      </omi:write>
    </omi:omiEnvelope>
    validOmiTest( temp )

  }


  def e200 = {
    validOmiTest(responseRequestTest)
  }


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

    temp.right.get.head should be equalTo ResponseRequest(Iterable(OmiResult(OmiReturn("200"), Iterable.empty[Long], Some(OdfObjects(OdfTreeCollection())))), 10 seconds)

  }

  def e207 = {
    val temp = OmiParser.parse(omiResponseTest.replace("returnCode=\"200\"", ""))
    temp.isLeft === true
    temp.left.get.head should be equalTo ParseError("OmiParser: Invalid XML, schema failure: cvc-complex-type.4: Attribute 'returnCode' must appear on element 'omi:return'.")
  }

  def e300 = {
    validOmiTest(readRequestTest)
  }

  def e301 = {
    val temp = OmiParser.parse(omiReadTest.replace("""omi:read msgformat="odf"""", "omi:read"))
    temp should be equalTo Left(Iterable(ParseError("OmiParser: Missing msgformat attribute.")))

  }

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
    temp should be equalTo Right(Iterable(ReadRequest(OdfObjects())))

  }

  def e306 = {
    validOmiTest(subscriptionRequestTest)
  }
  def e400 = {
    validOdfTest(writeOdf)
  }

  def e401 = {
    val temp = OdfParser.parse("incorrect xml")
    temp should be equalTo Left(Iterable(ParseError("Invalid XML: Content is not allowed in prolog.")))

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
    temp should be equalTo Left(Iterable( ParseError("OdfParser: Invalid XML, schema failure: cvc-elt.1: Cannot find the declaration of element 'Object'.")))

  }

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
    temp2 should be equalTo CancelRequest(Vector(123, 456))
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
  lazy val readOdf2 : OdfObjects = {
    val item1 = createAncestors(OdfInfoItem( 
      Path( "Objects/SmartHouse/PowerConsumption")
    ))

    val item2 = createAncestors(OdfInfoItem( 
      Path( "Objects/SmartHouse/Moisture")
    ))
  
    val item3 = createAncestors(OdfInfoItem( 
      Path( "Objects/SmartHouse/SmartFridge/PowerConsumption")
    ))
      
    val object1 = createAncestors(OdfObject(
      Vector( new QlmID("Heater")),
      Path("Objects/SmartCottage/Heater")
    ))
    item1.union( item2 ).union( item3 ).union( object1 ) 

  }
  lazy val readRequestTest = ReadRequest(
    readOdf2,
    callback = Some(HTTPCallback("http://testing.test"))
  )
  lazy val subscriptionRequestTest = SubscriptionRequest(
    10 seconds,
    readOdf2,
    callback = Some(HTTPCallback("http://testing.test"))
  )

  lazy val omiWriteTest =
   // <?xml version="1.0" encoding="UTF-8"?>
    <omi:omiEnvelope xmlns="odf.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" version="1.0" ttl="10.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
      <omi:write msgformat="odf" callback="http://testing.test">
        <omi:msg>
          <Objects>
            <Object>
              <id>SmartHouse</id>
              <InfoItem name="PowerConsumption">
                <value unixTime="1418909692" dateTime="2014-12-18T15:34:52.000+02:00" type="xs:int">180</value>
              </InfoItem>
              <InfoItem name="Moisture">
                <value unixTime="1418909692" dateTime="2014-12-18T15:34:52.000+02:00" type="xs:int">0.20</value>
              </InfoItem>
              <Object>
                <id>SmartFridge</id>
                <InfoItem name="PowerConsumption">
                  <value unixTime="1418909692" dateTime="2014-12-18T15:34:52.000+02:00" type="xs:int">56</value>
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
                <value unixTime="1418909692" dateTime="2014-12-18T15:34:52.000+02:00" >30</value>
              </InfoItem>
            </Object>
            <Object>
              <id>SmartCottage</id>
              <Object>
                <id>Heater</id>
              </Object>
            </Object>
          </Objects>
        </omi:msg>
      </omi:write>
    </omi:omiEnvelope>
  lazy val testTimestamp = new Timestamp( 1418909692 )
  lazy val writeOdf : OdfObjects = {
    val item1 = createAncestors(OdfInfoItem( 
      Path( "Objects/SmartHouse/PowerConsumption"),
      Vector( 
        OdfValue( "193.1", "xs:double", testTimestamp ),
        OdfValue( "1.1", "xs:double", testTimestamp )
      ), 
      None,
      None
    ))

    val item2 = createAncestors(OdfInfoItem( 
      Path( "Objects/SmartHouse/Moisture"),
      Vector( 
        OdfValue( "193.1", "xs:double", timestamp = testTimestamp ),
        OdfValue( "1.1", "xs:double", timestamp = testTimestamp )
      ), 
      Some( OdfDescription( " test" )), Some(
        OdfMetaData(Vector(OdfInfoItem(
          Path( "Objects/SmartHouse/Moisture/MetaData/Units"),
          Vector(OdfValue(
            "Litre",
            "xs:string",
            testTimestamp
          ))))))
      //Some( OdfMetaData(
      //  "<MetaData xmlns=\"odf.xsd\" xmlns:omi=\"omi.xsd\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"" +
      //  " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><InfoItem name=\"Units\"><value type=\"xs:String\">" +
      //  "Litre</value></InfoItem></MetaData>"
      //        )
      //    )
    ))
  
    val item3 = createAncestors(OdfInfoItem( 
      Path( "Objects/SmartHouse/SmartFridge/PowerConsumption"),
      Vector( 
        OdfValue( "193.1", "xs:double", testTimestamp ),
        OdfValue( "1.1", "xs:double", testTimestamp )
      ), 
      None,
      None
    ))
      
    val object1 = createAncestors(OdfObject(
      Vector( new QlmID("Heater")),
      Path("Objects/SmartCottage/Heater")
    ))
    item1.union( item2 ).union( item3 ).union( object1 ) 

  }
  lazy val writeRequestTest = WriteRequest(
    writeOdf,
    Some(HTTPCallback("http://testing.test"))
  )
  lazy val responseRequestTest = ResponseRequest(
    Seq(OmiResult(
      OmiReturn("200"),
      odf = Some(writeOdf)
      ))
  )

  println( responseRequestTest.asXML.toString )
  lazy val omiResponseTest =
    """<?xml version="1.0" encoding="UTF-8"?>
    <omi:omiEnvelope xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="-1">
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
              </Object>
            </Objects>
          </omi:msg>
        </omi:result>
      </omi:response>
    </omi:omiEnvelope>"""

    def validOmiTest( request: OmiRequest ) : MatchResult[OmiParseResult] = {
      val xml = request.asXML
      val text = xml.toString
      val result = OmiParser.parse( text )
  
      result should beRight{ 
        requests: Iterable[OmiRequest] =>
        {
          requests should have size(1) 
        }and{
          requests.headOption should beSome(request)
        }
      }
    }

    def validOmiTest( text: String ) : MatchResult[OmiParseResult] = {
      val result = OmiParser.parse( text )
  
      result should beRight{ 
        requests: Iterable[OmiRequest] =>
          requests should have size(1)
          requests.headOption should beSome{
            request : OmiRequest  => 
            request.asXML.toString should be equalTo text
          }
      }
    }
    def validOmiTest( xml: NodeSeq ) : MatchResult[OmiParseResult] = {
      val text = xml.toString
      val result = OmiParser.parse( text )
  
      result should beRight{ 
        requests: Iterable[OmiRequest] =>
          requests should have size(1)
          requests.headOption should beSome{
            request : OmiRequest  => 
            request.asXML should beEqualToIgnoringSpace(xml)
          }
      }
    }
    def invalidOmiTest( xml: NodeSeq, errors : Set[ParseError] ) : MatchResult[OmiParseResult] = {
      val text = xml.toString
      val result = OmiParser.parse( text )
      result should beLeft{
        parseErrors : Iterable[ParseError] => 
          parseErrors.toSet should be equalTo errors
      }
    }

    def invalidOmiTest( text: String, errors : Set[ParseError] ) : MatchResult[OmiParseResult]= {
      val result = OmiParser.parse( text )
  
      result should beLeft{
        parseErrors : Iterable[ParseError] => 
          parseErrors.toSet should be equalTo errors
      }
    }
    def validOdfTest( node: OdfNode ) : MatchResult[OdfParseResult] = {
      val correct = createAncestors(node)
      val xml = correct.asXML
      val text = xml.toString
      val result = OdfParser.parse( text )
  
      result should beRight( correct )
    }

    def invalidOdfTest( text: String, errors : Set[ParseError] ) : MatchResult[OdfParseResult] = {
      val result = OdfParser.parse( text )
  
      result should beLeft{
        parseErrors : Iterable[ParseError] => 
          parseErrors.toSet should be equalTo errors
      }
    }
}


