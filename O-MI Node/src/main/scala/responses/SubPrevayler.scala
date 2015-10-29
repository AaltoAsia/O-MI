package responses

import akka.actor.{ActorLogging, Actor}
import database._
import org.prevayler.PrevaylerFactory

/**
 * Created by satsuma on 29.10.2015.
 */
class SubPrevayler(implicit dbConnection: DB) extends Actor with ActorLogging{
  case class MyStore(var data: MyData)
  case class MyData(data: Int)

  val prevayler = PrevaylerFactory.createPrevayler(MyStore(MyData(0)))

  def receive = {
    case _ =>
  }

}
