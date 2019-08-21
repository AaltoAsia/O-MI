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
package omi
package parsing

import java.sql.Timestamp
import scala.util.Try
import scala.concurrent.Future
import scala.collection.JavaConverters._
import scala.collection.immutable.HashMap

import akka.NotUsed
import akka.util._
import akka.stream._
import akka.stream.stage._
import akka.stream.scaladsl._
import akka.stream.alpakka.xml._
import akka.stream.alpakka.xml.scaladsl._

import types._
import omi._
import utils._

/** Parser for data in O-DF format */
object OMIStreamParser {
  def parse(filePath: java.nio.file.Path)(implicit mat: Materializer): Future[OmiRequest] = stringParser(FileIO.fromPath(filePath).map(_.utf8String))
  def parse(str: String)(implicit mat: Materializer): Future[OmiRequest] = stringParser(Source.single(str))
  def stringParser(source: Source[String, _])(implicit mat: Materializer): Future[OmiRequest] =
    source.via(parserFlow).runWith(Sink.head[OmiRequest])
  def byteStringParser(source: Source[ByteString, _])(implicit mat: Materializer): Future[OmiRequest] =
    source.via(parserFlowByteString).runWith(Sink.head[OmiRequest])
  def parserFlowByteString: Flow[ByteString,OmiRequest,NotUsed] = Flow[ByteString]
    .via(XmlParsing.parser)
    .via(new OMIParserFlow)
  def xmlParserFlow: Flow[String,ParseEvent,NotUsed] = Flow[String]
    .map(ByteString(_))
    .via(XmlParsing.parser)
  def omiParserFlow: OMIParserFlow = new OMIParserFlow
  def parserFlow: Flow[String,OmiRequest,NotUsed] = xmlParserFlow.via(omiParserFlow)

  class OMIParserFlow extends GraphStage[FlowShape[ParseEvent, OmiRequest]] {
    val in = Inlet[ParseEvent]("OMIParserFlowF.in")
    val out = Outlet[OmiRequest]("OMIParserFlow.out")
    override val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var state: EventBuilder[_] = new EnvelopeEventBuilder(None,currentTimestamp)
      private var done: Boolean = false

      //override def preStart(): Unit = pull(in)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val event: ParseEvent = grab(in)

          state = state.parse(event)

          Try{
            state match {
              case builder: EnvelopeEventBuilder if builder.isComplete  =>
                if( !done ){
                  done = true
                  push(out,builder.build )
                  pull(in)
                }
              case other: EventBuilder[_] =>
                if( other.isComplete )
                  failStage(OMIParserError("Non EnvelopeBuilder is complete"))
                else
                  pull(in)
            }
          }.recover{
            case error: OMIParserError => 
              failStage( error)
            case t: Throwable => 
              failStage( t)
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
}
