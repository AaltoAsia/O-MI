package authorization


import akka.http.scaladsl.HttpExt
import akka.actor.ActorSystem
import org.specs2.mock.Mockito
import java.sql.Timestamp
import org.specs2.mock.Mockito.mock
import org.specs2.mutable.Specification
import types.odf.ImmutableODF
import types.odf._
import http.OmiConfigExtension
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
import akka.util.Timeout
import org.specs2.specification.Scope
import org.specs2.matcher.MustThrownExpectations
import scala.collection.immutable

class AuthAPIServiceMock(hierarchyStored: ODF=ImmutableODF())(implicit system: ActorSystem)
  extends AuthAPIServiceV2(
    DummyHierarchyStore(hierarchyStored)
    , new OmiConfigExtension(Actorstest.silentLoggerConfFull)
    , system
    , mock[ActorMaterializer]
    )
  with Mockito
  with MustThrownExpectations
  with Scope {

  override val httpExtension = mock[HttpExt]
}
class AuthServiceTestEnv extends Specification with AfterAll{
  implicit val system = Actorstest.createSilentAs()

  def afterAll = {
    system.terminate()
    Await.ready(system.whenTerminated, 5.seconds)
  }

  def beSomeEmptyReadRequest = 
    beSome(beLike[OmiRequest]{
      case read: ReadRequest if read.odf.getPaths.size == 0 => ok
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


  case class AuthTest() extends AuthAPIServiceMock(testOdf)

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

    "extractParameter" should {

      val omi = Some(RawRequestWrapper(ReadAll.asXML.toString, UserInfo()))
      val base = Post(uri)

      "extract from HttpRequest" >> {
        "authorizationheader" in new AuthTest() {
          val http = base.addCredentials(headers.OAuth2BearerToken("mytoken"))

          extractParameter(
            http, omi, "authorizationheader", "Bearer") must beSome("mytoken")
        }

        "omienvelope" in new AuthTest() {
          extractParameter(base, omi, "omienvelope", "ttl") must beSome("10")
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
    "createRequest" should {
      "correctly include two variables of" >> {

        //def twoVars(context: String, nameA: String, nameB: String) =
        //  Map(context -> Map(nameA -> "a", nameB -> "b"))
        def twoVars(context: String) = Map(context -> Map("Tok" -> "a", "foo" -> "b"))
        def singleVar(context: String) = Map(context -> Map("Tok" -> "a"))

        val testVars = Map("a" -> "myToken", "b" -> "bar")

        "query" in new AuthTest() {
          createRequest(Post, uri, twoVars("query"), testVars)
            .uri.toString === "http://localhost?Tok=myToken&foo=bar"
        }
        "headers" in new AuthTest() {
          val res = createRequest(Post, uri, twoVars("headers"), testVars)
          extractParameter(res, None, "headers", "Tok") must beSome("myToken")
          extractParameter(res, None, "headers", "foo") must beSome("bar")
        }
        "cookies" in new AuthTest() {
          val res = createRequest(Post, uri, twoVars("cookies"), testVars)
          extractParameter(res, None, "cookies", "Tok") must beSome("myToken")
          extractParameter(res, None, "cookies", "foo") must beSome("bar")
        }
        "authorizationheader" in new AuthTest() {
          val res = createRequest(Post, uri, singleVar("authorizationheader"), testVars)
          // Reusing "headers" extraction to check the Authorization header,
          // because it is RawHeader when created with createRequest
          extractParameter(res, None, "headers", "Authorization") must beSome("Tok myToken")
        }
        "form-urlencoded" in new AuthTest() {
          val res = createRequest(Post, uri, twoVars("form-urlencoded"), testVars)
          res.entity.toString === "HttpEntity.Strict(application/x-www-form-urlencoded; charset=UTF-8,Tok=myToken&foo=bar)"
        }
        "jsonbody" in new AuthTest {
          val res = createRequest(Post, uri, twoVars("jsonbody"), testVars)
          res.entity.toString === """HttpEntity.Strict(application/json,{"Tok":"myToken","foo":"bar"})"""
        }
      }
    }
  } // AuthAPIService
}
