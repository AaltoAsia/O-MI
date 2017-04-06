package http

import types.Path
import types.OmiTypes._
import Authorization._
import akka.http.scaladsl.server.Directives._
import scala.util.{Success,Failure}

trait AuthChargingPole extends AuthorizationExtension {
  abstract override def makePermissionTestFunction: CombinedTest = combineWithPrevious(
    super.makePermissionTestFunction,
    provide{(wrap: RequestWrapper) =>
      wrap.unwrapped flatMap {
        case r: WriteRequest =>
          val targetOdf = r.odf.get(Path("Objects/ChargingPole/Users/Register"))
          targetOdf match {
            case Some(i) =>
              Success(r.copy(odf=i.createAncestors)) // disable any other simultaneus write
            case None =>
              Failure(UnauthorizedEx())
          }
        case _ => Failure(UnauthorizedEx())
      }
    }
  )
}



