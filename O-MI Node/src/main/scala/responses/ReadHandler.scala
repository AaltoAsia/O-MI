
package responses

import parsing.xmlGen.xmlTypes.RequestResultType

import scala.util.{ Try, Success, Failure }
import scala.concurrent.duration._
import scala.concurrent.{ Future, Await, ExecutionContext, TimeoutException }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.JavaConversions.asJavaIterable
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.collection.breakOut
import scala.xml.{ NodeSeq, XML }
//import spray.http.StatusCode

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.util.Timeout
import akka.pattern.ask


import types._
import OmiTypes._
import OdfTypes._
import OmiGenerator._
import parsing.xmlGen.{ xmlTypes, scalaxb, defaultScope }
import CallbackHandlers._
import database._

trait ReadHandler extends OmiRequestHandler{
  handler{
    case read: ReadRequest => handleRead(read)
  }
  /** Method for handling ReadRequest.
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): Future[NodeSeq] = {
    log.debug("Handling read.")

    val leafs = getLeafs(read.odf)
    val other = getOdfNodes(read.odf) collect {case o: OdfObject if o.hasDescription => o.copy(objects = OdfTreeCollection())}
    val objectsO: Future[Option[OdfObjects]] = dbConnection.getNBetween(leafs, read.begin, read.end, read.newest, read.oldest)

    objectsO.map(res => res match {
      case Some(objects) =>
        val found = Results.read(objects)
        val requestsPaths = leafs.map { _.path }
        val foundOdfAsPaths = getLeafs(objects).flatMap { _.path.getParentsAndSelf }.toSet
        val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
        var results = Seq(found)
        if (notFound.nonEmpty)
          results ++= Seq(Results.simple("404",
            Some("Could not find the following elements from the database:\n" + notFound.mkString("\n"))))


          xmlFromResults(
            1.0,
            results: _*)
      case None =>
        xmlFromResults(
          1.0, Results.notFound)
    })
  }
}
