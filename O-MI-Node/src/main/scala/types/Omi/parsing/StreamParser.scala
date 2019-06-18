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
package OmiTypes
package parser

import java.sql.Timestamp
import scala.util.Try
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
  def parserFlow: Flow[String,OmiRequest,NotUsed] = Flow[String]
    .map(ByteString(_))
    .via(XmlParsing.parser)
    .via(new OMIParserFlow)
  private class OMIParserFlow extends GraphStage[FlowShape[ParseEvent, OmiRequest]] {
    val in = Inlet[ParseEvent]("OMIParserFlowF.in")
    val out = Outlet[OmiRequest]("OMIParserFlow.out")
    override val shape = FlowShape(in, out)

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var state: EventBuilder[_] = new EnvelopeEventBuilder(None,currentTimestamp)


      setHandler(out, new OutHandler {
        override def onPull(): Unit = pull(in)
      })

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val event: ParseEvent = grab(in)
          Try{
            state = state.parse(event) 
            state match {
              case builder: EnvelopeEventBuilder if builder.isComplete  =>
                push(out,builder.build )
              case other: EventBuilder[_] =>
                if( other.isComplete )
                  failStage(OMIParserError("Non EnvelopeBuilder is complete"))
            }
          }.recover{
            case error: OMIParserError => 
              failStage( error)
            case t: Throwable => 
              failStage( t)
          }
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          completeStage()
        }
      })
    }
  }
  
}
