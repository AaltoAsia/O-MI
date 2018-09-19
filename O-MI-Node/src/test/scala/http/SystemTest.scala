package http

import java.text.SimpleDateFormat
import java.util.TimeZone

import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.testkit.TestProbe
import akka.util.Timeout
import database._
import journal.Models.ErasePathCommand
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable._
import org.specs2.specification.BeforeAfterAll
import testHelpers._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.Try
import scala.xml._

class SystemTest(implicit ee: ExecutionEnv) extends Specification with BeforeAfterAll {


  val omiServer = new TestOmiServer()

  omiServer.bindTCP()
  val serverBinding = omiServer.bindHTTP()

  import omiServer.{dbConnection, materializer, singleStores, system}

  // TODO: better cleaning after tests
  def beforeAll() = {
    Await
      .ready((singleStores.hierarchyStore ? ErasePathCommand(types.Path("/Objects"))) (new Timeout(20 seconds)),
        20 seconds)
  }

  sequential


  val probe = TestProbe()
  val testServer = new SystemTestCallbackServer(probe.ref, "localhost", 20002)
  val http = Http(system)


  val printer = new scala.xml.PrettyPrinter(80, 2)
  val parser = new HTML5Parser
  val sourceFile = if (java.nio.file.Files
    .exists(java.nio.file.Paths.get("O-MI-Node/html/ImplementationDetails.html"))) {
    Source.fromFile("O-MI-Node/html/ImplementationDetails.html")
  } else Source.fromFile("html/ImplementationDetails.html")
  val sourceXML: Node = parser.loadXML(sourceFile)
  val testArticles = sourceXML \\ ("article")
  val tests = testArticles.groupBy(x => x.\@("class"))

  //tests with request response pairs, each containing description and forming single test(req, resp), (req, resp)...
  lazy val readTests = tests("request-response single test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length == 2,
      s"Each request must have exactly 1 response in request-response tests, could not find for: $node")
    val request: Try[Elem] = getSingleRequest(textAreas)
    val correctResponse: Try[Elem] = getSingleResponse(textAreas)
    val testDescription = node \ ("div") \ ("p") text

    (request, correctResponse, testDescription)
  }

  //tests that have multiple request-response pairs that all have common description (req, resp, req, resp...)
  lazy val subsNoCallback = tests("request-response test").map { node =>
    val textAreas = node \\ ("textarea")
    require(textAreas.length % 2 == 0,
      "There must be even amount of response and request messages(1 response for each request)\n" + textAreas)

    val testDescription: String = node \ ("div") \ ("p") text

    val groupedRequests = textAreas.grouped(2).map { reqresp =>
      val request: Try[Elem] = getSingleRequest(reqresp)
      val correctresponse: Try[Elem] = getSingleResponseNoTime(reqresp)
      val responseWait: Option[Int] = Try(reqresp.last.\@("wait").toInt).toOption
      (request, correctresponse, responseWait)
    }
    (groupedRequests, testDescription)

  }
  /*
   * tests that have callback server responses mixed:
   * for example if the test in html is (req, resp, callbackresp, callbackresp, req, resp)
   * this groups them like: (req, resp), (callbackresp), (callbackresp), (req,resp)
   * so that each req resp pair forms 1 test and callbackresp alone forms 1 test
   */
  lazy val sequentialTest = tests("sequential-test").map { node =>
    val textAreas = node \\ ("textarea")
    val testDescription: String = node \ ("div") \ ("p") text
    val reqrespCombined: Seq[NodeSeq] = textAreas.foldLeft[Seq[NodeSeq]](NodeSeq.Empty) { (res, i) =>
      if (res.isEmpty) i
      else {
        if (res.last.length == 1 && (res.last.head.\@("class") == "request")) {
          val indx: Int = res.lastIndexWhere { x => x.head.\@("class") == "request" }
          res.updated(indx, res.last.head :+ i)

        } else res.:+(i)
      }
    }

    (reqrespCombined, testDescription)
  }

  def afterAll = {
    val future = serverBinding
      .flatMap(sb => sb.unbind())
      .flatMap(_ => testServer.unbind())
      .flatMap(_ => system.terminate())

    future.failed.foreach {
      case t: Throwable =>
        system.log.error(t, "AfterAll encountered:")
    }
    Await.ready(future, 2 seconds)
    dbConnection.destroy()
    Await
      .ready((singleStores.hierarchyStore ? ErasePathCommand(types.Path("/Objects"))) (new Timeout(20 seconds)),
        20 seconds)
  }

  def getPostRequest(in: String): HttpRequest = {
    val tmp = RequestBuilding.Post("http://localhost:8080/", in)
    tmp
  }

  def getPostRequest(in: NodeSeq): HttpRequest = {
    val tmp = RequestBuilding.Post("http://localhost:8080/", in)
    tmp
  }

  def getSingleRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(setTimezoneToSystemLocale(reqresp.head.text)))
  }

  def removeTimes(text: String): String = removeUnixTime(removeDateTime(text))

  def removeDateTime(text: String): String = text.replaceAll(
    """dateTime\s*=\s*"\S*?"""",
    ""
  )

  def removeUnixTime(text: String): String = text.replaceAll(
    """unixTime\s*=\s*"\d*"""",
    ""
  )

  def fixSubId(id: Option[Long], message: String): String =
    if (id.isEmpty) return message
    else
      message.replaceAll(
        """requestID>\d*<\/requestID""",
        s"""requestID>${ id.get }</requestID"""
      )

  def getSingleResponseNoTime(reqresp: NodeSeq): Try[Elem] = {
    Try(
      XML.loadString(
        setTimezoneToSystemLocale(
          removeTimes(reqresp.last.text)
        )
      )
    )
  }

  def getSingleResponse(reqresp: NodeSeq): Try[Elem] = {
    Try(XML.loadString(setTimezoneToSystemLocale(removeDateTime(reqresp.last.text))))
  }

  def getCallbackRequest(reqresp: NodeSeq): Try[Elem] = {
    require(reqresp.length >= 1)
    Try(XML.loadString(
      setTimezoneToSystemLocale(
        reqresp.head.text.replaceAll(
          """callback\s*=\s*"(http:\/\/callbackAddress\.com:5432)"""",
          """callback="http://localhost:20002/"""")
      )
    )
    )
  }

  def setTimezoneToSystemLocale(in: String): String = {
    val date = """(end|begin)\s*=\s*"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{3})?)"""".r

    val replaced = date replaceAllIn(in, _ match {

      case date(pref, timestamp) => {

        val form = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
        form.setTimeZone(TimeZone.getTimeZone("UTC"))

        val parsedTimestamp = form.parse(timestamp)

        form.setTimeZone(TimeZone.getDefault)

        val newTimestamp = form.format(parsedTimestamp)

        (pref + "=\"" + newTimestamp + "\"")
      }
    })
    replaced
  }

  "Automatic System Tests" should {
    "Write Test" >> {
      dbConnection.clearDB()
      //Only 1 write test
      val writearticle = tests("write test").head
      val testCase = writearticle \\ ("textarea")
      val request: Try[Elem] = getSingleRequest(testCase)
      val correctResponse: Try[Elem] = getSingleResponse(testCase)
      val testDescription = writearticle \ ("div") \ ("p") text

      ("test case:\n " + testDescription.trim + "\n") >> {
        request aka "Write request message" must beSuccessfulTry
        correctResponse aka "Correct write response message" must beSuccessfulTry

        val responseFuture = http
          .singleRequest(getPostRequest(request.get)) //pipeline(Post("http://localhost:8080/", request.get))

        val response = Try(Await.result(responseFuture.flatMap(n =>
          Unmarshal(n).to[NodeSeq]), Duration(10, "second")))

        response must beSuccessfulTry

        response.get showAs (n =>
          "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)
          ) must new BeEqualFormatted(correctResponse.get)
      }
    }

    step({
      Thread.sleep(2000);
    })

    "Read Test" >> {
      readTests.foldLeft(org.specs2.specification.core.Fragments.empty)((res, i) => {
        val (request, correctResponse, testDescription) = i
        (testDescription.trim + "\n") in {

          val t1 = request aka "Read request message" must beSuccessfulTry
          val t2 = correctResponse aka "Correct response message" must beSuccessfulTry

          val responseFuture = http.singleRequest(getPostRequest(request.get)).flatMap(n =>
            Unmarshal(n).to[NodeSeq])
          responseFuture.failed.foreach {
            case t: Throwable =>
              system.log.error(t, "Ummarshalling failure loq: ")

          }
          val responseXML = Try(Await.result(responseFuture, Duration(2, "second")))

          val t3 = responseXML must beSuccessfulTry
          val response = XML.loadString(removeDateTime(responseXML.get.toString))
          t1 and t2 and t3 and (response showAs (n =>
            "Request Message:\n" + printer.format(request.get) + "\n\n" + "Actual response:\n" + printer.format(n.head)
            ) must new BeEqualFormatted(correctResponse.get))
        }
      })
    }

    "Subscription Test" >> {
      subsNoCallback.foldLeft(org.specs2.specification.core.Fragments.empty)((res, i) => {
        val (reqrespwait, testDescription) = i
        (testDescription.trim() + "\n") >> {
          var requestId: Option[Long] = None
          reqrespwait.foldLeft(org.specs2.specification.core.Fragments.empty)((res, j) => {
            "step: " >> {
              val (request, correctResponse, responseWait) = j
              request aka "Subscription request message" must beSuccessfulTry
              correctResponse aka "Correct response message" must beSuccessfulTry

              responseWait.foreach { x => Thread.sleep(x * 1000) }
              val responseFuture = http
                .singleRequest(getPostRequest(XML.loadString(fixSubId(requestId, request.get.toString))))
              val responseXml = Try(Await
                .result(responseFuture.flatMap(Unmarshal(_).to[NodeSeq]), Duration(2, "second")))
              val tryId = Try(responseXml.get.\\("requestID").head.text.toLong).toOption
              if (requestId
                .forall(id => tryId
                  .exists(_ != id))) // if the request ID is different and not empty than previous requestId
                requestId = tryId

              responseXml must beSuccessfulTry

              val response = XML.loadString(fixSubId(requestId, removeTimes(responseXml.get.toString)))

              response showAs (n =>
                "Request Message:\n" +
                  printer.format(request.get) +
                  "\n\n" +
                  "Actual response:\n" +
                  printer.format(n.head)
                ) must new BeEqualFormatted(XML.loadString(fixSubId(requestId, correctResponse.get.toString)))
            }
          })
        }

      })
    }
    "Callback Test" >> {
      sequentialTest.foldLeft(org.specs2.specification.core.Fragments.empty)((res, i) => {

        val (singleTest, testDescription) = i

        (testDescription.trim()) >> {
          var requestId: Option[Long] = None
          singleTest.foldLeft(org.specs2.specification.core.Fragments.empty)((res, j) => {
            "Step: " >> {
              require(j.length == 2 || j.length == 1)
              val responseWait: Option[Int] = Try(j.last.\@("wait").toInt).toOption


              if (j.length == 2) {
                val request = getCallbackRequest(j)

                request aka "Subscription request message" must beSuccessfulTry

                responseWait.foreach { x => Thread.sleep(x * 1000) }

                val responseFuture = http
                  .singleRequest(getPostRequest(XML.loadString(fixSubId(requestId, request.get.toString()))))
                val responseXml = Try(Await
                  .result(responseFuture.flatMap(Unmarshal(_).to[NodeSeq]), Duration(2, "second")))
                val tryId = Try(responseXml.get.\\("requestID").head.text.toLong).toOption
                if (requestId
                  .forall(id => tryId
                    .exists(_ != id))) // if the request ID is different and not empty than previous requestId
                  requestId = tryId


                responseXml must beSuccessfulTry

                val response = XML.loadString(fixSubId(requestId, removeTimes(responseXml.get.toString)))
                //remove blocking waits if possible
                if (request.get.\\("write").nonEmpty) {
                  Thread.sleep(2000)
                }
                val correctResponse = getSingleResponseNoTime(j)
                  .map(m => XML.loadString(fixSubId(requestId, m.toString())))
                correctResponse aka "Correct response message" must beSuccessfulTry
                response showAs (n =>
                  "Request Message:\n" +
                    printer.format(request.get) +
                    "\n\n" +
                    "Actual response:\n" +
                    printer.format(n.head)
                  ) must new BeEqualFormatted(correctResponse.get)

              } else {
                val correctResponse = getSingleResponseNoTime(j)
                  .map(m => XML.loadString(fixSubId(requestId, m.toString())))
                correctResponse aka "Correct response message" must beSuccessfulTry
                val messageOption = probe.expectMsgType[Option[NodeSeq]](Duration(responseWait.getOrElse(2), "second"))

                messageOption must beSome
                val response = XML.loadString(removeTimes(messageOption.get.toString()))

                response showAs (n =>
                  "Response at callback server:\n" + printer.format(n.head)
                  ) must new BeEqualFormatted(correctResponse.get)

              }
            }
          })
        }
      })
    }
    "Web Socket test" >> {
      system.log.info(
        """
============================================
           Start Web Socket test
============================================        
        """
      )

      def writeMessage(value: String) = {
        s"""<?xml version="1.0" encoding="UTF-8"?>
            <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="0">
              <write msgformat="odf" >
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1">
                        <value>$value</value>
                      </InfoItem>
                      <InfoItem name="WSInfoItem2">
                        <value>staticValue</value>
                      </InfoItem>
                    </Object>
                  </Objects>
                </msg>
              </write>
            </omiEnvelope>"""
      }

      "Current Connection Subscription should " >> {
        "return correct number of responses for event subscription" >> {

          val wsProbe = TestProbe()
          val wsServer = new WsTestCallbackClient(wsProbe.ref, "ws://localhost", 8080)
          wsServer.offer(writeMessage("1"))
          val res1 = wsProbe.receiveN(1, 5 seconds) //write confirmation
          wsServer.offer(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="20">
              <read msgformat="odf" interval="-1" callback="0">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>
            """)
          val res2 = wsProbe.receiveN(1, 5 seconds) //write confirmation
          for {
            _ <- wsServer.offer(writeMessage("2"))
            _ <- wsServer.offer(writeMessage("3"))
            _ <- wsServer.offer(writeMessage("4"))
            f <- wsServer.offer(writeMessage("5"))
          } yield f
          val res3 = wsProbe.receiveN(8, 15 seconds)
          res1.length === 1
          res2.length === 1
          res3.length === 8 // 4 write confirmations and 4 subscription updates
        }

        "return correct number of responses for interval subscription: ttl=7 interval=2" >> {
          val wsProbe = TestProbe()
          val wsServer = new WsTestCallbackClient(wsProbe.ref, "ws://localhost", 8080)
          wsServer.offer(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="7">
              <read msgformat="odf" interval="2" callback="0">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>""")
          //val result = wsProbe.expectNoMsg(Duration.apply(5, "seconds"))
          // 3 responses + 1 confirmation
          val result = wsProbe.receiveN(4, 10 seconds)
          wsServer.close()
          result.length === 4
        }
        
        //broken test randomly fails
        // 2018-07-19: trying to fix
        "be sent to correct connections when multiple connections exists" >> {
          val wsProbe1 = TestProbe()
          val wsProbe2 = TestProbe()
          val wsServer1 = new WsTestCallbackClient(wsProbe1.ref, "ws://localhost", 8080)
          val wsServer2 = new WsTestCallbackClient(wsProbe2.ref, "ws://localhost", 8080)
          wsServer1.offer(
            """<?xml version="1.0" encoding="UTF-8"?>
            <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="10">
            <read msgformat="odf" interval="-1" callback="0">
              <msg>
                <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                  <Object>
                    <id>WebSocketTest</id>
                    <InfoItem name="WSInfoItem1"/>
                  </Object>
                </Objects>
              </msg>
            </read>
          </omiEnvelope>""")
          wsServer2.offer(
            """<?xml version="1.0" encoding="UTF-8"?>
            <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="10">
            <read msgformat="odf" interval="-1" callback="0">
              <msg>
                <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                  <Object>
                    <id>WebSocketTest</id>
                    <InfoItem name="WSInfoItem2"/>
                  </Object>
                </Objects>
              </msg>
            </read>
          </omiEnvelope>""")
          wsProbe1.receiveN(1, 5 seconds) //responses for subscriptions
          wsProbe2.receiveN(1, 5 seconds)
          val res = for{
            _ <- wsServer2.offer(writeMessage("6")) //WS2
            _ <- wsServer2.offer(writeMessage("7")) //WS2
            _ <- wsServer1.offer(writeMessage("8")) //WS1
            r <- wsServer1.offer(writeMessage("9")) //WS1
          } yield r
          Await.ready(res, 10 seconds)

          val res1 = wsProbe1.receiveN(6, 10 seconds) //4 subscription updates and 2 write confirmations
          val res2 = wsProbe2.receiveN(2, 10 seconds) //2 write confirmations(subscribed to unchanging ii)
          wsServer1.close()
          wsServer2.close()
          res1.length === 6
          res2.length === 2
        }
      }

      "Websocket Socket Subscription " >> {
        val wsProbe = TestProbe()
        val wsServer1 = new WsTestCallbackServer(wsProbe.ref, "localhost", 8787)
        val wsServer2 = new WsTestCallbackServer(wsProbe.ref, "localhost", 8788)
        val (bind1, unbind1) = wsServer1.bind()
        Await.ready(bind1, 5 seconds)
        "return correct number of responses for event subscription" >> {
          val m1 = getPostRequest(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="15">
              <read msgformat="odf" interval="-1" callback="ws://localhost:8787">
              <msg>
                <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                  <Object>
                    <id>WebSocketTest</id>
                    <InfoItem name="WSInfoItem1"/>
                  </Object>
                </Objects>
              </msg>
            </read>
          </omiEnvelope>
          """)
          val res1 = http.singleRequest(m1)
          Await.result(res1, 5 seconds)
          for {
            _ <- http.singleRequest(getPostRequest(writeMessage("2")))
            _ <- http.singleRequest(getPostRequest(writeMessage("3")))
            _ <- http.singleRequest(getPostRequest(writeMessage("4")))
            f <- http.singleRequest(getPostRequest(writeMessage("5")))
          } yield f
          val res3 = wsProbe.receiveN(4, 10 seconds)
          res3.length === 4 // 4 write confirmations and 4 subscription updates
        }
        "return correct number of responses for two event subscriptions with same callback address" >> {
          val m1 = getPostRequest(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="15">
              <read msgformat="odf" interval="-1" callback="ws://localhost:8787">
              <msg>
                <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                  <Object>
                    <id>WebSocketTest</id>
                    <InfoItem name="WSInfoItem1"/>
                  </Object>
                </Objects>
              </msg>
            </read>
          </omiEnvelope>
          """)
          val res1 = http.singleRequest(m1)
          val res2 = http.singleRequest(m1)
          Await.result(res1, 5 seconds)
          Await.result(res2, 5 seconds)
          for {
            _ <- http.singleRequest(getPostRequest(writeMessage("2")))
            _ <- http.singleRequest(getPostRequest(writeMessage("3")))
            _ <- http.singleRequest(getPostRequest(writeMessage("4")))
            f <- http.singleRequest(getPostRequest(writeMessage("5")))
          } yield f
          val res3 = wsProbe.receiveN(8, 12 seconds)
          res3.length === 8 // 4 write confirmations and 4 subscription updates
        }
        "return correct number of responses for interval subscription: ttl=7 interval=2" >> {
          val m1 = getPostRequest(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="7">
              <read msgformat="odf" interval="2" callback="ws://localhost:8787">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>""")
          http.singleRequest(m1)
          val result = wsProbe.receiveN(3, 15 seconds)
          //val result = wsProbe.expectNoMsg(Duration.apply(5, "seconds"))
          // 3 responses
          result.length === 3
        }
        "return correct number of responses for multiple interval subscription: ttl=7 interval=2 and ttl=8 and interval=3 with same callback address" >>
          {
            val m1 = getPostRequest(
              """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="7">
              <read msgformat="odf" interval="2" callback="ws://localhost:8787">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>""")
            val m2 = getPostRequest(
              """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="8">
              <read msgformat="odf" interval="3" callback="ws://localhost:8787">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>""")
            http.singleRequest(m1)
            http.singleRequest(m2)
            val result = wsProbe.receiveN(5, 20 seconds)
            //val result = wsProbe.expectNoMsg(Duration.apply(5, "seconds"))
            // 3 responses
            result.length === 5
          }
        val (bind2, unbind2) = wsServer2.bind()
        Await.ready(bind2, 5 seconds)
        "return correct number of responses for multiple interval subscription: ttl=7 interval=2 and ttl=8 and interval=3 with different callback address" >>
          {
            val m1 = getPostRequest(
              """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="7">
              <read msgformat="odf" interval="2" callback="ws://localhost:8787">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>""")
            val m2 = getPostRequest(
              """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="8">
              <read msgformat="odf" interval="3" callback="ws://localhost:8788">
                <msg>
                  <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                    <Object>
                      <id>WebSocketTest</id>
                      <InfoItem name="WSInfoItem1"/>
                    </Object>
                  </Objects>
                </msg>
              </read>
            </omiEnvelope>""")
            http.singleRequest(m1)
            http.singleRequest(m2)
            val result = wsProbe.receiveN(5, 20 seconds)
            //val result = wsProbe.expectNoMsg(Duration.apply(5, "seconds"))
            // 3 responses
            result.length === 5
          }
        "return correct number of responses for two event subscriptions with different callback address" >> {
          val m1 = getPostRequest(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="15">
              <read msgformat="odf" interval="-1" callback="ws://localhost:8787">
              <msg>
                <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                  <Object>
                    <id>WebSocketTest</id>
                    <InfoItem name="WSInfoItem1"/>
                  </Object>
                </Objects>
              </msg>
            </read>
          </omiEnvelope>
          """)
          val m2 = getPostRequest(
            """<?xml version="1.0" encoding="UTF-8"?>
              <omiEnvelope  xmlns="http://www.opengroup.org/xsd/omi/1.0/" version="1.0" ttl="15">
              <read msgformat="odf" interval="-1" callback="ws://localhost:8788">
              <msg>
                <Objects xmlns="http://www.opengroup.org/xsd/odf/1.0/">
                  <Object>
                    <id>WebSocketTest</id>
                    <InfoItem name="WSInfoItem1"/>
                  </Object>
                </Objects>
              </msg>
            </read>
          </omiEnvelope>
          """)
          val res1 = http.singleRequest(m1)
          val res2 = http.singleRequest(m2)
          Await.result(res1, 5 seconds)
          Await.result(res2, 5 seconds)
          for {
            _ <- http.singleRequest(getPostRequest(writeMessage("2")))
            _ <- http.singleRequest(getPostRequest(writeMessage("3")))
            _ <- http.singleRequest(getPostRequest(writeMessage("4")))
            f <- http.singleRequest(getPostRequest(writeMessage("5")))
          } yield f
          val res3 = wsProbe.receiveN(8, 12 seconds)
          res3.length === 8 // 4 write confirmations and 4 subscription updates
        }
        step{
          unbind1()
          unbind2()
        }
      }
    }
  }
}
