package http

import akka.actor.{Actor, ActorSystem,  ActorLogging, Props}
import scala.collection.mutable.{Map => MMap, HashMap => MHMap}
import java.sql.Timestamp

object RequestStore{
  def props: Props = {
    Props(
      new RequestStore()
    )
  }
  type RequestIDType= Long
  trait RequestInfo{
    def name: String
    def value: Any
  }

  case class RequestLongInfo( val name: String, var value: Long) extends RequestInfo
  case class RequestTimestampInfo( val name: String, var value: Timestamp) extends RequestInfo
  case class RequestDoubleInfo( val name: String, var value: Double) extends RequestInfo
  case class RequestStringInfo( val name: String, var value: String) extends RequestInfo
  case class RequestAnyInfo( val name: String, var value: Any) extends RequestInfo

  case class AddInfos(val request: RequestIDType,  val infos: Seq[RequestInfo] )
  case class GetInfo(val request: RequestIDType,  val infoName: String ) 
  case class GetAllInfos(val request: RequestIDType)
  case class AddRequest(val request: RequestIDType)
  case class RemoveRequest(val request: RequestIDType)
}


class RequestStore () extends Actor with ActorLogging{
  import RequestStore._
  val storage: MHMap[RequestIDType,MHMap[String,RequestInfo]] = MHMap.empty
  def receive = {
    case AddRequest( request ) => 
      log.debug( s"New Request: $request")
      storage += request -> MHMap.empty

    case RemoveRequest( request ) => 
      log.debug( s"Remove Request: $request")
      storage -= request
    case GetInfo( request, infoName ) =>
      sender() ! storage.get(request).flatMap{
        requestInfos: MHMap[String, RequestInfo] => 
          requestInfos.get(infoName)
      }
    case GetAllInfos( request) =>
      sender() ! storage.get(request).map{
        requestInfos: MHMap[String, RequestInfo] => 
          requestInfos.toMap
      }
    case AddInfos( request,infos ) =>
      storage.get(request).foreach{ 
        requestInfos =>
          infos.foreach{
            info =>
            requestInfos += info.name -> info
          }
      }
      val tmp = infos.map{ info => s" ${info.name} : ${info.value.toString}"}.mkString("; ")
      log.debug( s"Info Request: $request, $tmp") 
  }

}
