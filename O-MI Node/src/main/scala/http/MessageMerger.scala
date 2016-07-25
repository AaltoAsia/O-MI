package http

import akka.stream.{ Inlet, Outlet, FlowShape, Graph, Attributes }
import akka.stream.stage.{ GraphStage, OutHandler, InHandler, GraphStageLogic}

class MessageMerger[A](check: A => Boolean, merger: (A, A) => A) extends GraphStage[FlowShape[A, A]] {

  val in = Inlet[A]("MessageMerger.in")
  val out = Outlet[A]("MessageMerger.out")

  val shape = FlowShape.of(in, out)
  var prior : Option[A] = None
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
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

class OmiMerger extends MessageMerger[String]( 
  { str: String =>
    val omiPrefix : String = "<omi:omiEnvelope "
    val omiPostfix : String = "</omi:omiEnvelope>"
    val start = str.indexOfSlice(omiPrefix)
    val end = str.indexOfSlice(omiPostfix)
    println( s"Start: $start" )
    println( s"End: $end" )
    println( s"Msg: $str" )
    start != -1 && end != -1 && start < end 
  }, { (prior: String, next: String) =>
    prior + next
  })
