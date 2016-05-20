package agents

import agentSystem._ 
import types._
import types.OdfTypes._
import types.OmiTypes._
import akka.util.Timeout
import akka.actor.Cancellable
import akka.pattern.ask
import scala.util.{Success, Failure}
import scala.collection.JavaConversions.{iterableAsScalaIterable, asJavaIterable }
import scala.concurrent._
import scala.concurrent.duration._
import java.sql.Timestamp;
import java.util.Random;
import java.util.Date;

class ResponsibleAgent  extends BasicAgent with ResponsibleInternalAgent{
  protected def handleWrite(promise:Promise[ResponsibleAgentResponse], write: WriteRequest) = {
    val result = PromiseResult()
    parent ! PromiseWrite( result, write)
    promise.completeWith( result.isSuccessful ) 
  }
}
