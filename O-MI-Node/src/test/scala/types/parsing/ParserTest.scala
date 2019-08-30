package parsing

import java.sql.Timestamp

import scala.concurrent.Future
import scala.collection.SeqView
import scala.xml.Elem
import scala.xml.XML
import akka.stream.scaladsl.Sink
import akka.stream.alpakka.xml._
import akka.stream.ActorMaterializer
import org.specs2._
import types.omi._
import types._
import types.odf._
import types.omi.parsing.OMIStreamParser
import types.odf.parsing.ODFStreamParser
import utils._
import testHelpers._

import scala.xml.NodeSeq
//import java.lang.Iterable
import org.specs2.matcher.XmlMatchers._
import org.specs2.matcher._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.execute.Result

import scala.concurrent.duration._

/*
 * Test class for testing parsing parsing package
 * tests e1   - e99 are for testing OMIStreamParser general methods
 * tests e100 - e199 are for testing write request
 * tests e200 - e299 are for testing response messages
 * tests e300 - e399 are for testing read requests
 * tests e400 - e499 are for testing OdfParser class
 */

class ParserTest( implicit ee: ExecutionEnv ) extends Specification with MatcherMacros with SilentActorSystem{
  implicit val materializer = ActorMaterializer()

  def is =
    s2"""
  This is Specification to check the parsing functionality.

  OMIStreamParser should give certain result for
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
      missing msg     $e103
      missing Objects     $e104
      no objects to parse $e105
    response message with
      correct message     $e200
      missing Objects     $e204
      missing result node $e205
      missing return code $e207
    read request with
      correct message     $e300
      missing msgformat   $e301
      missing msg     $e303
      missing Objects     $e304
      no objects to parse $e305
    correct subscription $e306
    cancel request with
      correct request     $e500
    OdfParser should give certain result for message with
      correct format      $e400
      incorrect XML       $e401
      incorrect label     $e402
    """

  def e1 = {
    invalidOmiTestThrowable("incorrect xml", "Unexpected character 'i' (code 105) in prolog")
  }

  /*
   * case ParseError("Incorrect prefix :: _ ) matches to list that has that parse error in the head position
   */
  def e2 = {
    invalidOmiTestThrowable(
      omiReadTest.replace("omiEnvelope", "pmi:omiEnvelope"),  "Unbound namespace prefix 'pmi' (for element name 'pmi:omiEnvelope')"
    )
  }

  def e3 = {
    invalidOmiTest(
      omiReadTest.replace("omiEnvelope", "Envelope"), "OMIParserError"
    )

  }

  def e4 = {
    invalidOmiTestThrowable(
      """<omiEnvelope ttl="10" version="1.0" xsi:schemaLocation="omi.xsd omi.xsd" xmlns="http://www.opengroup.org/xsd/omi/1.0/" >
      </omiEnvelope>""",
      "Unbound namespace prefix 'xsi' (for attribute name 'xsi:schemaLocation')" 
    )
  }

  def e5 = {
    invalidOmiTest(
      omiReadTest.replace(""" ttl="10"""", """ ttl="""""), "OMIParserError" )
  }

  def e6 = {
    val temp = "daer"
    invalidOmiTest(
      omiReadTest.replace("read", s"$temp"), "OMIParserError" 
    )
  }

  def e100 = {
    validOmiTest(writeRequestTest)
  }

  def e101 = {
    invalidOmiTest(
      omiWriteTest.toString.replace("write msgformat=\"odf\"", "write"), "OMIParserError" 
    )
  }


  def e103 = {
    invalidOmiTest(
      omiWriteTest.toString.replace("msg", "msn"), "OMIParserError"
    )
  }

  def e104 = {
    val temp = <omiEnvelope ttl="10.0" version="1.0" xmlns="http://www.opengroup.org/xsd/omi/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
      <write msgformat="odf">
        <msg xmlns="http://www.opengroup.org/xsd/odf/1.0/" xmlns:odf="http://www.opengroup.org/xsd/odf/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        </msg>
      </write>
    </omiEnvelope>
    invalidOmiTest(
      temp, "OMIParserError"
    )


  }

  def e105 = {
    val temp = <omiEnvelope ttl="10.0" version="1.0" xmlns="http://www.opengroup.org/xsd/omi/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <write msgformat="odf">
        <msg>
          <Objects/>
        </msg>
      </write>
    </omiEnvelope>
    invalidOmiTest(temp, "ODFParserError")

  }


  def e200 = {
    validOmiTest(responseRequestTest)
  }


  def e204 = {
    val temp = <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="10">
      <response>
        <result msgformat="odf" > 
          <return returnCode="200" /> 
          <msg xmlns="http://www.opengroup.org/xsd/odf/1.0/"  xmlns:odf="http://www.opengroup.org/xsd/odf/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
        </msg>
      </result> 
    </response>
  </omiEnvelope>

    invalidOmiTest(temp, "ODFParserError")

  }

  def e205 = {
    val temp = omiResponseTest.replace("<return returnCode=\"200\"/>", "")

    invalidOmiTest(temp, "OMIParserError")
  }

  def e207 = {
    val temp = omiResponseTest.replace("returnCode=\"200\"", "") //, None)
    invalidOmiTest(temp, "OMIParserError")
  }

  def e300 = {
    validOmiTest(readRequestTest)
  }

  def e301 = {
    val temp =omiReadTest.replace("""read msgformat="odf"""", "read")
    invalidOmiTest(temp, "OMIParserError")
  }

  def e303 = {
    val temp = omiReadTest.replace("msg", "msn") 
    invalidOmiTest(temp, "OMIParserError")
  }

  def e304 = {
    val temp ="""
      <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="10">
      <read msgformat="odf" >
        <msg xmlns="http://www.opengroup.org/xsd/odf/1.0/"  xmlns:odf="http://www.opengroup.org/xsd/odf/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
      </msg>
    </read>
  </omiEnvelope>
  """ 
  invalidOmiTest(temp, "OMIParserError")

  }

  def e305 = {
    val request=  ReadRequest(ImmutableODF(Vector(Objects()))) 
    val temp = validOmiTest(request)
    temp 

  }

  def e306 = {
    validOmiTest(subscriptionRequestTest)
  }

  def e400 = {
    validOdfTest(writeOdf)
  }

  def e401 = {
    invalidOdfTestThrowable("incorrect xml", "Unexpected character 'i' (code 105) in prolog")
  }

  def e402 = {
    val temp = """
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
            """ 
    invalidOdfTest(temp, "ODFParserError")
  }

  def e500 = {
    val request = CancelRequest(Vector(123, 456))
    validOmiTest(request) and validOmiTest(request)
  }

  lazy val omiReadTest =
    """<?xml version="1.0" encoding="UTF-8"?>
      <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="10">
      <read msgformat="odf">
        <msg xmlns="http://www.opengroup.org/xsd/odf/1.0/" xmlns:odf="http://www.opengroup.org/xsd/odf/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
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
                </msg>
              </read>
            </omiEnvelope>"""
  lazy val readOdf2: ODF = ODF(
    InfoItem.build(
      Path("Objects", "SmartHouse", "PowerConsumption")
    ),

    InfoItem.build(
      Path("Objects", "SmartHouse", "Moisture")
    ),

    InfoItem.build(
      Path("Objects", "SmartHouse", "SmartFridge", "PowerConsumption")
    ),

    Object(
      Vector(new OdfID("Heater")),
      Path("Objects", "SmartCottage", "Heater")
    )
  )

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
    <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="10.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
      <write msgformat="odf" callback="http://testing.test">
        <msg>
          <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/" xmlns:odf="http://www.opengroup.org/xsd/odf/1.0/" xmlns:xs="http://www.w3.org/2001/XMLSchema">
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
                <value unixTime="1418909692" dateTime="2014-12-18T15:34:52.000+02:00">30</value>
              </InfoItem>
            </Object>
            <Object>
              <id>SmartCottage</id>
              <Object>
                <id>Heater</id>
              </Object>
            </Object>
          </Objects>
        </msg>
      </write>
    </omiEnvelope>
  lazy val testTimestamp1 = new Timestamp(1418909692)
  lazy val testTimestamp2 = new Timestamp(1418919692)
  lazy val writeOdf: ODF = ODF(
    InfoItem(
      Path("Objects", "SmartHouse", "PowerConsumption"),
      Vector(
        Value("1.1", "xs:double", testTimestamp1),
        Value("193.1", "xs:double", testTimestamp2)
      )
    ),

    InfoItem.build((
      Path("Objects", "SmartHouse", "Moisture"),
      Vector[Value[Any]](
        Value("1.1", "xs:double", timestamp = testTimestamp1),
        Value("193.1", "xs:double", timestamp = testTimestamp2)
      ),
      Description(" test"), 
      MetaData(Vector(InfoItem(
          Path("Objects", "SmartHouse", "Moisture", "MetaData", "Units"),
          Vector(Value(
            "Litre",
            "xs:string",
            testTimestamp1
          ))
      )))
    )),

    InfoItem(
      Path("Objects", "SmartHouse", "SmartFridge", "PowerConsumption"),
      Vector(
        Value("193.1", "xs:double", testTimestamp1),
        Value("1.1", "xs:double", testTimestamp2)
      )
    ),

    Object(
      Path("Objects", "SmartCottage", "Heater")
    )
  )

  lazy val writeRequestTest = WriteRequest(
    writeOdf,
    Some(HTTPCallback("http://testing.test"))
  )
  lazy val responseRequestTest = ResponseRequest(
    OdfCollection(OmiResult(
      omi.Returns.Success(),
      odf = Some(writeOdf)
    ))
  )

  lazy val omiResponseTest =
    """<?xml version="1.0" encoding="UTF-8"?>
    <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="-1">
      <response>
        <result msgformat="odf">
          <return returnCode="200"/>
          <msg>
            <Objects xmlns:xs="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://www.opengroup.org/xsd/odf/1.0/" xmlns:odf="http://www.opengroup.org/xsd/odf/1.0/">
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
          </msg>
        </result>
      </response>
    </omiEnvelope>"""

  def reparseRequest(request: OmiRequest): Future[OmiRequest] = request.asXMLSource.via(OMIStreamParser.parserFlow).runWith(Sink.head[OmiRequest]) 
  def validOmiTest(request: OmiRequest): Result = {

    def strConcatSink= Sink.fold[String, String]("")(_+_)
    val reparsed: Future[OmiRequest] = reparseRequest(request)
    reparsed.flatMap{ 
      reReq => 
        reReq.asXMLSource.runWith(strConcatSink).map{ str => XML.loadString(str)}.flatMap{ 
          strReq => 
            request.asXMLSource.runWith(strConcatSink).map{ str => XML.loadString(str)}.map{
              correctStr => 
                strReq should beEqualToIgnoringSpace(correctStr)
            }
        }
    }.await
  }

  def validOmiTest(text: String): Result = {
    val result = OMIStreamParser.parse(text)

    result.flatMap{
      request: OmiRequest =>
        request.asXMLSource.runWith(Sink.fold("")(_ + _))
    }.map{ 
      strReq: String => 
      strReq should beEqualTo(text)
    }.await
  }

  def validOmiTest(xml: NodeSeq): Result= validOmiTest(xml.toString) 

  def invalidOmiTest(xml: NodeSeq, error: ParseError): Result= invalidOmiTest(xml.toString,error)

  def invalidOmiTest(text: String, error: ParseError): Result= {

    val result = OMIStreamParser.parse(text)
    result.failed.map{
      t =>
        t should be equalTo error
    }.await
  }

  def invalidOmiTest(xml: NodeSeq, errorType: String): Result= invalidOmiTest(xml.toString,errorType)

  def invalidOmiTest(text: String, errorType: String): Result = {
    val result = OMIStreamParser.parse(text)
    result.recover{
      case pe: ParseError =>
        parseErrorTypeToString(pe) 
    }.map{
      case str: String => 
        str must contain(errorType)
    }.await
  }
  def invalidOmiTestThrowable(text: String, errorMsg: String): Result = {
    val result = OMIStreamParser.parse(text)
    result.recover{
      case t: Throwable =>
        t.getMessage()
    }.map{
      case str: String => 
        str must contain(errorMsg)
    }.await
  }

  def reparseODF(odf: ODF): Future[ODF] = odf.asXMLDocumentSource().via(ODFStreamParser.parserFlow).runWith(Sink.fold[ImmutableODF,ODF](ImmutableODF())(_ union _)) 
  def validOdfTest(odf: ODF): Result= {

    val result = reparseODF(odf)

    result.map{r_odf => r_odf should beEqualTo(odf)}.await
  }

  def invalidOdfTest(text: String, errorType: String): Result= {
    val result = ODFStreamParser.parse(text)

    result.recover{
      case pe: ParseError => parseErrorTypeToString(pe) 
    }.map{
      case str: String =>
      str must contain(errorType)
    }.await
  }
  def invalidOdfTest(text: String, error: ParseError): Result= {

    val result = ODFStreamParser.parse(text)
    result.failed.map{
      t =>
        t should be equalTo error
    }.await
  }
  def invalidOdfTestThrowable(text: String, error: String): Result= {

    val result = ODFStreamParser.parse(text)
    result.failed.map{
      t: Throwable =>
        t.getMessage must contain(error)
    }.await
  }

  def parseErrorTypeToString(pe: ParseError): String = {
    pe match {
      case _: OMIParserError => "OMIParserError"
      case _: ScalaXMLError => "ScalaXMLError"
      case _: ScalaxbError => "ScalaxbError"
      case _: ODFParserError => "ODFParserError"
      case _: OMIParserError => "OMIParserError"
      case _: ParseErrorList => "ParserErrorList"
      case _ => throw pe
    }
  }
}


