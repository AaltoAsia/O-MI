/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2019 Aalto University.                                        +
 +                                                                                +
 +    Licensed under the 4-clause BSD (the "License");                            +
 +    you may not use this file except in compliance with the License.            +
 +    You may obtain a copy of the License at top most directory of project.      +
 +                                                                                +
 +    Unless required by applicable law or agreed to in writing, software         +
 +    distributed under the License is distributed on an "AS IS" BASIS,           +
 +    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    +
 +    See the License for the specific language governing permissions and         +
 +    limitations under the License.                                              +
 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
package types
package odf
package parsing

import scala.util.Try
import scala.concurrent.Future

import akka.NotUsed
import akka.util._
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import types._
import odf._
import utils._

/** Parser for data in O-DF format */
object ODFStreamParser {
  def parse(filePath: java.nio.file.Path)(implicit mat: Materializer): Future[ODF] = stringParser(FileIO.fromPath(filePath).map(_.utf8String))
  def parse(str: String)(implicit mat: Materializer): Future[ODF] = stringParser(Source.single(str))
  def stringParser(source: Source[String, _])(implicit mat: Materializer): Future[ODF] =
    source.via(parserFlow).runWith(Sink.fold[ODF,ODF](ImmutableODF())(_ union _))
  def byteStringParser(source: Source[ByteString, _])(implicit mat: Materializer): Future[ODF] =
    source.via(parserFlowByteString).runWith(Sink.fold[ODF,ODF](ImmutableODF())(_ union _))

  def parserFlowByteString: Flow[ByteString,ODF,NotUsed] = Flow[ByteString]
    .via(XmlParsing.parser)
    .via(new ODFParserFlow)
  def parserFlow: Flow[String,ODF,NotUsed] = Flow[String]
    .map(ByteString(_))
    .via(XmlParsing.parser)
    .via(new ODFParserFlow)

  class ODFParserFlow extends GraphStage[FlowShape[ParseEvent,ODF]] {
    val in = Inlet[ParseEvent]("ODFParserFlowF.in")
    val out = Outlet[ODF]("ODFParserFlow.out")
    override val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var state: EventBuilder[_] = new ODFEventBuilder(None,currentTimestamp)
      private var done: Boolean = false

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val event: ParseEvent = grab(in)

          state = state.parse(event)

          Try{
            state match {
              case builder: ODFEventBuilder if builder.isComplete  =>
                if( !done ){
                  done = true
                  push(out,builder.build )
                  pull(in)
                }
              case other: EventBuilder[_] =>
                if( other.isComplete )
                  failStage(ODFParserError("Non EnvelopeBuilder is complete"))
                else
                  pull(in)
            }
          }.recover{
            case error: ODFParserError => 
              failStage( error)
            case t: Throwable => 
              failStage( t)
          }

        }
      })

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          if( !done )
            pull(in)
        }
      })

    }
  }
}
