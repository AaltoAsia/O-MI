package authorization


import akka.http.scaladsl.HttpExt
import akka.actor.ActorSystem
import org.specs2.mock.Mockito
import java.sql.Timestamp
import org.specs2.mock.Mockito.mock
import org.specs2.mutable.Specification
import types.odf.ImmutableODF
import types.odf._
import testHelpers.{DummyHierarchyStore, Actorstest}
import akka.stream.ActorMaterializer
import scala.collection.immutable.{HashMap, Vector}
import types.Path
import scala.concurrent.duration._
import scala.concurrent.Await
import org.specs2.specification.AfterAll
import types.OmiTypes._
import org.specs2.concurrent.ExecutionEnv
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.client.RequestBuilding._
//import akka.util.{ByteString, Timeout}
import akka.util.Timeout
import org.specs2.specification.Scope
import org.specs2.matcher.MustThrownExpectations
import scala.collection.immutable
import scala.concurrent.Future
import com.typesafe.config._
import http.OmiConfigExtension
import util.Try

class AuthAPIServiceMock(
    config: Config = ConfigFactory.empty()
  , hierarchyStored: ODF=ImmutableODF()
  )(implicit system: ActorSystem)
  extends AuthAPIServiceV2(
    DummyHierarchyStore(hierarchyStored)
    , new OmiConfigExtension(config.withFallback(Actorstest.silentLoggerConfFull))
    , system
    , mock[ActorMaterializer]
    )
  with Mockito
  with MustThrownExpectations
  with Scope {

  override val httpExtension = mock[HttpExt]
}
class AuthServiceTestEnv extends Specification with AfterAll with Mockito {
  implicit val system = Actorstest.createAs()

  def afterAll = {
    system.terminate()
    Await.ready(system.whenTerminated, 5.seconds)
  }

  def beSomeEmptyReadRequest = 
    beSome(beLike[OmiRequest]{
      case read: ReadRequest if read.odf.getPaths.isEmpty => ok
    })

  def containExactlyPaths(f: Path*) = {
    beSome(beLike[OmiRequest]{
      case or: OdfRequest => 
        val odfPaths = or.odf.getLeafPaths
        odfPaths must containTheSameElementsAs(f)
    })
  }
}

class AuthServiceTest(implicit ee: ExecutionEnv) extends AuthServiceTestEnv{



  val testOdf = ODF(
      Objects(
        attributes=HashMap("prefix" -> "derp"))
    , Object(
        Path("Objects/Object1"), Some("typetest"),
        Set(Description("This is a description")),
        HashMap("customAttribute" -> "attr")
      )
    , InfoItem(
        Path("Objects/Object1/TestItem"),
        Some("typetest"), Vector(), Set(Description("This is a description")),
        Vector(), Some(MetaData(Vector(
          InfoItem(Path("Objects/Object1/TestItem/MetaData/meta"), Vector(Value(1, new Timestamp(1))))
        ))),
        HashMap("customAttribute" -> "attr")
      )
    , InfoItem.build(Path("Objects/PrivateObject/PublicItem"))
    , InfoItem.build(Path("Objects/PrivateObject/PublicObject/PublicItem"))
    , InfoItem.build(Path("Objects/PrivateObject/PublicObject/PrivateItem"))
    , InfoItem.build(Path("Objects/PublicObject/PrivateItem"))
    , InfoItem.build(Path("Objects/PublicObject/PublicItem"))
    , InfoItem.build(Path("Objects/PublicObject/PublicObject/PublicItem"))
    , InfoItem.build(Path("Objects/PublicObject/PrivateObject/PublicItem"))
    , InfoItem.build(Path("Objects/PublicObject/PrivateObject/PrivateItem"))
    , InfoItem.build(Path("Objects/SemiPublicObject/PublicItem"))
    , InfoItem.build(Path("Objects/SemiPublicObject/PrivateItem"))
    )


  case class AuthTest() extends AuthAPIServiceMock(hierarchyStored=testOdf)

  val ReadAll = ReadRequest(ODF(Objects.empty), ttl=10.seconds)

  "AuthAPIServiceV2" >> {addSections

    section("filterODF") // start

    "filterODF, given allow and deny lists of paths, it" should {

      val WritePublic = WriteRequest(ODF(Objects.empty))
      val testFiltersDeny = AuthorizationResponse(
        Set(Path("Objects")),
        Set(Path("Objects/PrivateObject")
          , Path("Objects/PublicObject/PrivateObject")
          , Path("Objects/PrivateObject/PrivateItem")
          , Path("Objects/PublicObject/PrivateObject/PrivateItem")
          , Path("Objects/PublicObject/PrivateItem")
          , Path("Objects/SemiPublicObject/PrivateItem")
          )
        )
      val testFiltersBoth = AuthorizationResponse(
        Set(Path("Objects/PublicObject")
          , Path("Objects/Object1")
          , Path("Objects/SemiPublicObject/PublicItem")),
        Set(Path("Objects/PrivateObject")
          , Path("Objects/PublicObject/PrivateObject")
          , Path("Objects/PrivateObject/PrivateItem")
          , Path("Objects/PublicObject/PrivateObject/PrivateItem")
          , Path("Objects/PublicObject/PrivateItem")
          )
        )

      val testFiltersAllow = AuthorizationResponse(
        Set(Path("Objects/PublicObject")
          , Path("Objects/Object1")
          , Path("Objects/SemiPublicObject/PublicItem")),
        Set()
      )
      
      val emptyFilters = AuthorizationResponse(Set(),Set())


      "return unauthorized, for" >> {
        "empty allowed and denied list, in read Objects request" in {
          AuthTest().filterODF(ReadAll, emptyFilters) must beNone.await
        }
        "empty allowed and denied list, in write Objects request" in {
          AuthTest().filterODF(WritePublic, emptyFilters) must beNone.await
        }

        "empty allowed and denied list, in write Objects request" in {
          AuthTest().filterODF(WritePublic, emptyFilters) must beNone.await
        }

        "empty allowed and filled denied list, in read Objects request" in {
          AuthTest().filterODF(
            ReadAll, AuthorizationResponse(Set(),testFiltersDeny.deny)
            ) must beNone.await
        }
        "direct deny path" in {
          AuthTest().filterODF(
            ReadRequest(ODF(Objects.empty, InfoItem.build(Path("Objects/SemiPublicObject/PrivateItem")))),
            AuthorizationResponse(
                Set(Path("Objects/"))
              , Set(Path("Objects/SemiPublicObject/PrivateItem"))
            )
          ) must beNone.await
        }
        "write non allowed" in {
          AuthTest().filterODF(
            WriteRequest(ODF(
                InfoItem.build(Path("Objects/SemiPublicObject/PrivateItem"))
            )),
            testFiltersBoth) must beNone.await
        }
        "write direct deny" in {
          AuthTest().filterODF(
            WriteRequest(ODF(
                InfoItem.build(Path("Objects/PublicObject/PrivateItem"))
            )),
            testFiltersBoth) must beNone.await
        }
        "write Object deny" in {
          AuthTest().filterODF(
            WriteRequest(ODF(
                InfoItem.build(Path("Objects/PrivateObject/PublicItem"))
            )),
            testFiltersBoth) must beNone.await
        }
      }

      "return correct" >> {

        "for direct allow path with read Objects" in {
          AuthTest().filterODF(
            ReadAll,
            AuthorizationResponse(Set(Path("Objects/PublicObject/PublicItem")), Set())
          ) must containExactlyPaths(
            Path("Objects/PublicObject/PublicItem")
          ).await
        }


        "for direct allow path with direct read" in {
          AuthTest().filterODF(
            ReadRequest(ODF(Objects.empty, InfoItem.build(Path("Objects/PublicObject/PublicItem")))),
            AuthorizationResponse(Set(Path("Objects/PublicObject/PublicItem")), Set())
          ) must containExactlyPaths(
            Path("Objects/PublicObject/PublicItem")
          ).await
        }

        "for overlapping allow paths with read Objects" in {
          AuthTest().filterODF( ReadAll, testFiltersAllow
          ) must containExactlyPaths(
            testFiltersAllow.allow.toSeq: _*
          ).await
        }


        "sibling for direct deny path" in {
          AuthTest().filterODF(
            ReadAll,
            AuthorizationResponse(
                Set(Path("Objects/SemiPublicObject"))
              , Set(Path("Objects/SemiPublicObject/PrivateItem"))
            )
          ) must containExactlyPaths(
            Path("Objects/SemiPublicObject/PublicItem")
          ).await
        }

        "for allow and deny filters in authorized write request" in {
          AuthTest().filterODF(
            WriteRequest(ODF(
                InfoItem.build(Path("Objects/PublicObject/PublicItem"))
              , InfoItem.build(Path("Objects/SemiPublicObject/PublicItem"))
            )),
            testFiltersBoth
            ) must containExactlyPaths(
                Path("Objects/PublicObject/PublicItem")
              , Path("Objects/SemiPublicObject/PublicItem")
            ).await
        }
      }

      "return correct for read Objects request with" >> {
        "many deny filters" in {
          val testFilters = AuthorizationResponse(
            Set(Path("Objects")),
            Set(Path("Objects/PrivateObject")
              , Path("Objects/PublicObject/PrivateObject")
              , Path("Objects/SemiPublicObject/PrivateItem")
              , Path("Objects/PublicObject/PrivateItem")
              )
            )
          AuthTest().filterODF(ReadAll, testFilters) must containExactlyPaths(
              Path("Objects/Object1")
            , Path("Objects/PublicObject/PublicItem")
            , Path("Objects/SemiPublicObject/PublicItem")
            , Path("Objects/PublicObject/PublicObject")
            ).await
        }


        "overlapping deny filters" in {
          AuthTest().filterODF(ReadAll, testFiltersDeny) must containExactlyPaths(
              Path("Objects/Object1")
            , Path("Objects/PublicObject/PublicItem")
            , Path("Objects/SemiPublicObject/PublicItem")
            , Path("Objects/PublicObject/PublicObject")
            ).await
        }

        
        "with both deny and allow filters" in {
          AuthTest().filterODF(ReadAll, testFiltersBoth) must containExactlyPaths(
              Path("Objects/Object1")
            , Path("Objects/PublicObject/PublicItem")
            , Path("Objects/SemiPublicObject/PublicItem")
            , Path("Objects/PublicObject/PublicObject")
            ).await
        }
      }
    }
    section("filterODF") // end


    val uri = Uri("http://localhost")
    implicit val t = Timeout(20.seconds)
    val httpRequest = Post(uri)

    "extractParameter" should {

      val omi = Some(RawRequestWrapper(ReadAll.asXML.toString, UserInfo()))
      val base = httpRequest

      "extract from HttpRequest" >> {
        "authorizationheader" in new AuthTest() {
          val http = base.addCredentials(headers.OAuth2BearerToken("mytoken"))

          extractParameter(
            http, omi, "authorizationheader", "Bearer") must beSome("mytoken")
        }

        "omienvelope" in new AuthTest() {
          extractParameter(base, omi, "omienvelope", "ttl") must beSome("10.0")
        }

        "headers" in new AuthTest() {
          val http = base.withHeaders(headers.RawHeader("X-Derp", "mytoken"))

          extractParameter(http, omi, "headers", "X-Derp") must beSome("mytoken")
        }

        "query" in new AuthTest() {
          val http = Post(Uri("http://localhost?Token=myToken"))

          extractParameter(http, omi, "query", "Token") must beSome("myToken")
        }
        "cookies" in new AuthTest() {
          val http = base.withHeaders(headers.Cookie("Token", "myToken"))

          extractParameter(http, omi, "cookies", "Token") must beSome("myToken")
        }
        "jsonbody" in new AuthTest() {
          val http = base.withEntity("""{"Token": "myToken"}""")

          extractParameter(http, omi, "jsonbody", "Token") must beSome("myToken")
        }
        "jsonbody list result" in todo
      }
      "extract from HttpResponse" >> {
        "cookies" in new AuthTest() {
          val http = HttpResponse(headers=immutable.Seq(
            headers.`Set-Cookie`(headers.HttpCookie("Token", "myToken")):HttpHeader
          ))

          extractParameter(http, omi, "cookies", "Token") must beSome("myToken")
        }

        "query" in new AuthTest() {
          extractParameter(HttpResponse(), omi, "query", "Token") must beNone
        }
      }
    }

    //def twoVars(context: String, nameA: String, nameB: String) =
    //  Map(context -> Map(nameA -> "a", nameB -> "b"))
    def twoVars(context: String) = Map(context -> Map("Tok" -> "a", "foo" -> "b"))
    def singleVar(context: String) = Map(context -> Map("Tok" -> "a"))

    val testVars = Map("a" -> "myToken", "b" -> "bar")

    "createRequest" should {
      "correctly include two variables of" >> {
        "query" in new AuthTest() {
          createRequest(Post, uri, twoVars("query"), testVars)
            .uri.toString === "http://localhost?Tok=myToken&foo=bar"
        }
        "headers" in new AuthTest() {
          val res = createRequest(Post, uri, twoVars("headers"), testVars)
          //extractParameter(res, None, "headers", "Tok") must beSome("myToken")
          //extractParameter(res, None, "headers", "foo") must beSome("bar")
          res.headers must contain(
            allOf(
              beLike[HttpHeader]{case h => h.name === "Tok" and h.value === "myToken"},
              beLike[HttpHeader]{case h => h.name === "foo" and h.value === "bar"}
            )
          )
        }
        "cookies" in new AuthTest() {
          val res = createRequest(Post, uri, twoVars("cookies"), testVars)
          //extractParameter(res, None, "cookies", "Tok") must beSome("myToken")
          //extractParameter(res, None, "cookies", "foo") must beSome("bar")
          res.cookies must contain(
            allOf(
              beLike[headers.HttpCookiePair]{case h => h.name === "Tok" and h.value === "myToken"},
              beLike[headers.HttpCookiePair]{case h => h.name === "foo" and h.value === "bar"}
            )
          )
        }
        "authorizationheader" in new AuthTest() {
          val res = createRequest(Post, uri, singleVar("authorizationheader"), testVars)
          //extractParameter(res, None, "headers", "Authorization") must beSome("Tok myToken")
          res.headers must contain(
            beLike[HttpHeader]{case h => h.name === "Authorization" and h.value === "Tok myToken"}
          )
        }
        "form-urlencoded" in new AuthTest() {
          val res = createRequest(Post, uri, twoVars("form-urlencoded"), testVars)
          res.entity.toString === "HttpEntity.Strict(application/x-www-form-urlencoded,Tok=myToken&foo=bar)"
        }
        "jsonbody" in new AuthTest {
          val res = createRequest(Post, uri, twoVars("jsonbody"), testVars)
          res.entity.toString === """HttpEntity.Strict(application/json,{"Tok":"myToken","foo":"bar"})"""
        }
      }
    }

    val authzResponse = HttpResponse().withEntity(ContentTypes.`application/json`, 
        """{"allowed":["Objects"],"denied":["Objects/private"]}""" 
      )
    "sendAndReceiveAsAuthorizationResponse" should {
      "parse response correctly" in new AuthTest() {
        val authzRequest = httpRequest
        //httpExtension.singleRequest(anyObject, anyObject, anyObject, anyObject
        //  ) returns Future.successful(authzResponse)
        httpExtension.singleRequest(authzRequest) returns Future.successful(authzResponse)

        // run
        val result = sendAndReceiveAsAuthorizationResponse(authzRequest)

        // test
        // XXX: bug with mocks? (two calls)
        there were two(httpExtension).singleRequest(authzRequest)
        result must equalTo(
          AuthorizationResponse(Set(Path("Objects")),Set(Path("Objects/private")))
        ).await
      }
    }
    "extractToMap" should {
      "extract many parameters according to config" in new AuthTest() {
        val request = createRequest(Post, uri, twoVars("headers"), testVars)

        extractToMap(request, None, twoVars("headers")) === testVars
      }
    }
    "isAuthorizedForRawRequest" should {

      "call isAuthorizedForOdfRequest for odf request" in {
        val authTest = spy(AuthTest())
        val result = Authorized(UserInfo(None, Some("test")))
        doReturn(result).when(authTest).isAuthorizedForOdfRequest(anyObject, anyObject)

        authTest.isAuthorizedForRawRequest(httpRequest, ReadAll.asXML.toString) === result

        there was one(authTest).isAuthorizedForOdfRequest(anyObject, anyObject)
      }
      "return Authorized for other requests" in {
        AuthTest().isAuthorizedForRawRequest(
          httpRequest, CancelRequest(Vector(1)).asXML.toString
        ) === Authorized(UserInfo(None))
      }
    }

    "isAuthorizedForOdfRequest" should {

      val baseConf = ConfigFactory.parseString("""
        omi-service.authAPI.v2 {
          authentication.url = "http://localhost"
          authentication.method = GET
          authorization.url = "http://localhost"
          authorization.method = POST
        }""")
      val config1 = baseConf withFallback ConfigFactory.parseString("""
        omi-service.authAPI.v2 {
          parameters.fromRequest.query.Tok = token
          parameters.toAuthentication.query.Tok = token
          parameters.fromAuthentication.jsonbody.username = username
          parameters.toAuthorization.jsonbody {
            username = "username"
            request = "requestTypeChar"
          }
        } """)

      val authnResponse = HttpResponse().withEntity(ContentTypes.`application/json`, """{"username":"test"}""")
      val tokenUri = uri.withQuery(Uri.Query("Tok" -> "myToken"))

      "work correctly for auth and authz in success case" in new AuthAPIServiceMock(config1) {
        implicit val order = inOrder(httpExtension)

        //val authnRequest = beLike[HttpRequest]{
        //  case r => r.method === HttpMethods.GET and r.uri.toString === uri.toString
        //}
        //val authzRequest = beLike[HttpRequest]{
        //  case r => r.toStrict(1.seconds) ===
        //    HttpEntity.Strict(ContentTypes.`application/json`, ByteString("""{"username":"test","request":"r"}"""))
        //}


        //httpExtension.singleRequest(authnRequest,anyObject,anyObject,anyObject) returns Future.successful(authnResponse)
        //httpExtension.singleRequest(authzRequest,anyObject,anyObject,anyObject) returns Future.successful(authzResponse)
        httpExtension.singleRequest(anyObject,anyObject,anyObject,anyObject) 
          .returns(Future.successful(authnResponse))
          .thenReturns(Future.successful(authzResponse))
        
        isAuthorizedForRawRequest(Post(tokenUri), ReadAll.asXML.toString) === Changed(ReadAll, UserInfo(None, Some("test")))
      }
      "work correctly for auth and authz in failure case (allowed=[])" in new AuthAPIServiceMock(config1) {
        val authzResponse2 = HttpResponse().withEntity(ContentTypes.`application/json`, 
          """{"allowed":[],"denied":["Objects/private"]}""" 
          )
        httpExtension.singleRequest(anyObject,anyObject,anyObject,anyObject) 
          .returns(Future.successful(authnResponse))
          .thenReturns(Future.successful(authzResponse2))

        Try{
          isAuthorizedForRawRequest(Post(tokenUri), ReadAll.asXML.toString)
        } must beFailedTry
      }
      "work correctly for auth and authz in failure case (denied=[\"Objects\"])" in new AuthAPIServiceMock(config1) {
        val authzResponse2 = HttpResponse().withEntity(ContentTypes.`application/json`, 
          """{"allowed":["Objects"],"denied":["Objects"]}""" 
          )
        httpExtension.singleRequest(anyObject,anyObject,anyObject,anyObject) 
          .returns(Future.successful(authnResponse))
          .thenReturns(Future.successful(authzResponse2))

        Try{
          isAuthorizedForRawRequest(Post(tokenUri), ReadAll.asXML.toString)
        } must beFailedTry
      }
      "work correctly for auth skipping" in todo
    }

  } // AuthAPIService
}
