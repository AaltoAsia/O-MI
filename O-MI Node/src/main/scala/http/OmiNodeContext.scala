package http

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, Materializer}
import akka.http.scaladsl.HttpExt

import responses.{CallbackHandler, RequestHandler}
import database.{DBReadWrite, SingleStores, DBReadOnly}

sealed trait Storages {
  implicit val singleStores: SingleStores
  implicit val dbConnection: DBReadWrite
}

sealed trait Actors {
   val subscriptionManager: ActorRef
   val agentSystem: ActorRef
   val cliListener: ActorRef
}

sealed trait Settings {
  implicit val settings: OmiConfigExtension
}

sealed trait ActorSystemContext{
  implicit val system: ActorSystem
  implicit def ec: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer 
}
sealed trait RequestHandling {
  val requestHandler: RequestHandler
}
sealed trait Callbacking{
  implicit val callbackHandler : CallbackHandler
}

trait OmiNodeContext
  extends ActorSystemContext
  with    Actors
  with    Settings
  with    RequestHandling
  with    Storages
  with    Callbacking
{}
object ContextConversion {
  implicit def toExecutionContext(sys: ActorSystemContext): ExecutionContext = sys.system.dispatcher
  implicit def toActorSystem(sys: ActorSystemContext) : ActorSystem = sys.system
  implicit def toMaterializer(sys: ActorSystemContext) : Materializer = sys.materializer
  implicit def toConfigExtension(se: Settings): OmiConfigExtension = se.settings 
  implicit def toDBReadWrite(storage: Storages): DBReadWrite = storage.dbConnection
  implicit def toDBReadOnly(storage: Storages): DBReadOnly = storage.dbConnection
  implicit def toSingleStores(storage: Storages): SingleStores = storage.singleStores
}
