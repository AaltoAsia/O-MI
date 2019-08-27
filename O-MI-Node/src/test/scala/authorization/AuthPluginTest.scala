package authorization

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import akka.http.scaladsl.model.HttpRequest
import java.lang.{Iterable => JavaIterable}
import scala.concurrent.duration._
import akka.actor.ActorSystem

import types.omi._
//import types.odf.ImmutableODF
import types.odf._
import types.Path
import testHelpers._


class AuthPluginTestEnv extends Specification {} //with AfterAll with Mockito {

case class TestPlugin() extends AuthApi with Scope

class AuthApiProviderMock(
      odf: ImmutableODF = ImmutableODF(Objects.empty)
    )(implicit val system: ActorSystem)
  extends Authorization.ExtensibleAuthorization with AuthApiProvider with Scope with Mockito {

  val singleStores = new DummySingleStores(
    hierarchyStore = DummyHierarchyStore(odf)
  )
}

class AuthPluginTest extends AuthPluginTestEnv {

  val readAll = ReadRequest(ODF(Objects.empty), ttl=10.seconds)
  val writeR = WriteRequest(ODF(Objects.empty))
  val authorized = Authorized(UserInfo(None,None))

  "AuthApi" >> {
    "isAuthorizedForRequest" should {
      "use isAuthorizedForType implementation correctly with read request" in new TestPlugin() {
        override def isAuthorizedForType(httpRequest: HttpRequest,
                                isWrite: Boolean,
                                paths: JavaIterable[Path]): AuthorizationResult = {
          isWrite must beFalse
          authorized
        }

        isAuthorizedForRequest(null, readAll) === authorized
      }
      "use isAuthorizedForType implementation correctly with write request" in new TestPlugin() {
        override def isAuthorizedForType(httpRequest: HttpRequest,
                                isWrite: Boolean,
                                paths: JavaIterable[Path]): AuthorizationResult = {
            isWrite must beTrue
            authorized
        }
        isAuthorizedForRequest(null, writeR) === authorized
      }
      "return Unauthorized for non-O-DF request" in {
        TestPlugin().isAuthorizedForRequest(null, CancelRequest()) === Unauthorized()
      }
    }
      //override def isAuthorizedForRawRequest(
      //  httpRequest: HttpRequest, omiRequestXml: String): AuthorizationResult = {
    "return Unauthorized if no authorization method is implemented" in {
      TestPlugin().isAuthorizedForRequest(null, readAll) === Unauthorized()
      TestPlugin().isAuthorizedForType(null, false, null) === Unauthorized()
      TestPlugin().isAuthorizedForRawRequest(null, null) === Unauthorized() 
      //  httpRequest: HttpRequest, omiRequestXml: String): AuthorizationResult = {
    }
  }

  "AuthApiProvider" should {
    todo
  }
}
