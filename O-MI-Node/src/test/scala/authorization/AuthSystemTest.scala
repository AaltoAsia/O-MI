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

class AuthAPIServiceMock(hierarchyStored: ODF=ImmutableODF())(implicit system: ActorSystem)
  extends AuthAPIServiceV2(
    DummyHierarchyStore(hierarchyStored)
    , new OmiConfigExtension(Actorstest.silentLoggerConfFull)
    , system
    , mock[ActorMaterializer]
    )
  with Mockito {

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
      , Path("Objects/SemiPublicObject/PrivateItem")
      , Path("Objects/PublicObject/PrivateItem")
      )
    )

  val testFiltersAllow = AuthorizationResponse(
    Set(Path("Objects/PublicObject")
      , Path("Objects/Object1")
      , Path("Objects/SemiPublicObject/PublicItem")),
    Set()
  )

  case class AuthTest() extends AuthAPIServiceMock(testOdf)

  val ReadAll = ReadRequest(ODF(Objects.empty))
  val WritePublic = WriteRequest(ODF(Objects.empty))

  "AuthAPIServiceV2" should {
    "filter O-DF correctly, given allow and deny lists of paths, it" should {
      
      val emptyFilters = AuthorizationResponse(Set(),Set())

      section("simple") // start

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
      }

      section("simple") // end
      section("complex") // start

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
      section("complex") // end
    }
    
  }

}
