
package responses

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import spray.http.StatusCode

import responses.OmiGenerator._
import types.OdfTypes._
import types.OmiTypes._
import types._

trait ReadHandler extends OmiRequestHandler{
  /** Method for handling ReadRequest.
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): Future[NodeSeq] = {
    log.debug("Handling read.")

    val leafs = getLeafs(read.odf)
    val other = getOdfNodes(read.odf) collect {case o: OdfObject if o.hasDescription => o.copy(objects = OdfTreeCollection())}
    val objectsO: Future[Option[OdfObjects]] = dbConnection.getNBetween(leafs, read.begin, read.end, read.newest, read.oldest)

    objectsO.map{
      case Some(objects) =>
        val found = Results.read(objects)
        val requestsPaths = leafs.map { _.path }
        val foundOdfAsPaths = getLeafs(objects).flatMap { _.path.getParentsAndSelf }.toSet
        val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
        val results = Seq(found) ++ {
          if (notFound.nonEmpty)
            Seq(Results.simple("404", Some("Could not find the following elements from the database:\n" + notFound.mkString("\n"))))
          else Seq.empty
        }

          xmlFromResults(
            1.0,
            results: _*)
      case None =>
        xmlFromResults(
          1.0, Results.notFound)
    }
  }
}
