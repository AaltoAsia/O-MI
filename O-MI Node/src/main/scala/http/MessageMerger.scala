package http

import akka.stream.{ Inlet, Outlet, FlowShape, Graph, Attributes, SinkShape }
import akka.http.scaladsl.model.ws
import akka.stream.stage.{ GraphStage, OutHandler, InHandler, GraphStageLogic}

class MessageMerger[A](check: A => Boolean, merger: (A, A) => A) extends GraphStage[FlowShape[A, A]] {

  val in = Inlet[A]("MessageMerger.in")
  val out = Outlet[A]("MessageMerger.out")

  val shape = FlowShape.of(in, out)
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      private var prior : Option[A] = None
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem : A = grab(in)
          val msg = prior.map{ 
            pri => merger(pri, elem) 
          }.getOrElse(elem)
          if( check(msg) ){
            prior = None
            push(out, msg)
          } else {
            prior = Some(msg)
          }
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
    }
}

class WSFormatChecker( 
  formatCheck: String => Boolean,
  merge: (String, String) => String
  )(
    implicit materializer: akka.stream.Materializer
  ) extends  GraphStage[FlowShape[ws.Message, String]]{
  val in: Inlet[ws.Message] = Inlet("WSFormatChecker.in")
  val out: Outlet[String]= Outlet("WSFormatChecker.out")
  val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    import scala.concurrent.ExecutionContext.Implicits.global
    private var prior: Option[String] = None

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val elem : ws.Message = grab(in)
          elem match {
            case textMessage: ws.TextMessage =>
              val stricted = textMessage.textStream.runFold("")(_+_)(materializer)
              stricted.foreach{
                case message : String if formatCheck(message) => 
                  println("Whole Omi message received. Prior dropped.")
                  prior = None
                  push(out, message)
                case message : String if !formatCheck(message) && prior.isEmpty =>
                  prior = Some(message)
                  println("First part of Omi message received.")
                case message : String if prior.nonEmpty=>
                  println("Part of Omi message received.")
                  val merged = prior.map{ 
                    pri => merge(pri, message) 
                  }.getOrElse(message)
                  if( formatCheck(merged) ){
                    println("Part complited request. Prior cleaned. Message forwarded.")
                    prior = None
                    push(out, merged)
                  } else {
                    println("Part merged to prior.")
                    prior = Some(merged)
                  }
              }
            case a : ws.Message => //Noop? drop?
              println("Something odd hapened, default case reached.")
          }
        }
      })
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          pull(in)
        }
      })
  }
}
class OmiChecker()(implicit materializer: akka.stream.Materializer) extends WSFormatChecker(
{ str: String => 
      val omiPrefix : String = "<omi:omiEnvelope "
      val omiPostfix : String = "</omi:omiEnvelope>"
      val start = str.indexOfSlice(omiPrefix)
      val end = str.indexOfSlice(omiPostfix)
      println( s"Msg: $str" )
      println( s"Start: $start" )
      println( s"End: $end" )
      start != -1 && end != -1 && start < end 
    }, 
  (l:String,r:String) => l + r
) 
