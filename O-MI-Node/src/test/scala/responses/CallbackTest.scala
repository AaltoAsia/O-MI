package responses

import java.net.{InetAddress, URI}

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.RemoteAddress
import akka.stream.ActorMaterializer
import database.SingleStores
import http.{OmiConfig, OmiConfigExtension, OmiService}
import org.specs2.Specification
import org.specs2.mock.Mockito
import types.OdfTypes.OdfObjects
import types.OmiTypes.{RawCallback, ReadRequest, UserInfo}
import types.odf.OldTypeConverter
import scala.util.Failure
import testHelpers.OmiServiceDummy

/**
  * Created by satsuma on 24.5.2017.
  */
class CallbackTest extends Specification with Mockito {
  def is =
    s2"""
Request with callback
  will fail if callback address is different from sender address $ct1
  will succeed if callback address matches the sender address $ct2
  """

  val googleAddress = "http://google.com"
  val googleIP = InetAddress.getByName(new URI(googleAddress).getHost)
  val cb = RawCallback(googleAddress)


  def ct1 = {
    val dummy = new OmiServiceDummy()
    val differentCb = RawCallback(InetAddress.getLoopbackAddress.getHostAddress)
    dummy
      .defineCallbackForRequest(ReadRequest(OldTypeConverter.convertOdfObjects(OdfObjects()),
        callback = Some(differentCb),
        user0 = UserInfo(Some(RemoteAddress(googleIP)))), None)
    there was no(dummy.callbackHandler).createCallbackAddress(googleAddress)
  }

  def ct2 = {
    val dummy = new OmiServiceDummy()
    dummy.callbackHandler.createCallbackAddress(googleAddress) returns Failure(new Exception("dummy"))
    dummy
      .defineCallbackForRequest(ReadRequest(OldTypeConverter.convertOdfObjects(OdfObjects()),
        callback = Some(cb),
        user0 = UserInfo(Some(RemoteAddress(googleIP)))), None)
    there was two(dummy.callbackHandler).createCallbackAddress(googleAddress) // two because other is the stub
  }


}
