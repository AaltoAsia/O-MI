
/*+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 +    Copyright (c) 2015 Aalto University.                                        +
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

package responses

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import database.{GetTree, SingleStores}

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode

import responses.OmiGenerator._
import types.OdfTypes._
import types.OmiTypes._

trait ReadHandler extends OmiRequestHandlerBase {
  /** Method for handling ReadRequest.
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): Future[NodeSeq] = {
    log.debug("Handling read.")

    val leafs = getLeafs(read.odf)
    // NOTE: Might go off sync with tree or values if the request is large,
    // but it shouldn't be a big problem
    val metadataTree = SingleStores.hierarchyStore execute GetTree()

    //Find nodes from the request that HAVE METADATA OR DESCRIPTION REQUEST
    def nodesWithoutMetadata: Option[OdfObjects] = getOdfNodes(read.odf).collect {
      case oii@OdfInfoItem(_, _, desc, mData)
        if desc.isDefined || mData.isDefined => createAncestors(oii.copy(values = OdfTreeCollection()))
      case obj@OdfObject(pat, _, _, _, des, tv)
        if des.isDefined || tv.isDefined => createAncestors(obj.copy(infoItems = OdfTreeCollection(), objects = OdfTreeCollection()))
    }.reduceOption(_.union(_))

    def objectsWithMetadata = nodesWithoutMetadata.map(objs => metadataTree.intersect(objs))

    //val other = getOdfNodes(read.odf) collect {
    //  case o: OdfObject if o.hasDescription => o.copy(objects = OdfTreeCollection())
    //}

    val objectsO: Future[Option[OdfObjects]] = dbConnection.getNBetween(leafs, read.begin, read.end, read.newest, read.oldest)

    objectsO.map {
      case Some(objects) =>
        val metaCombined = objectsWithMetadata.fold(objects)(metas => objects.union(metas))
        val found = Results.read(metaCombined)
        val requestsPaths = leafs.map {
          _.path
        }
        val foundOdfAsPaths = getLeafs(metaCombined).flatMap {
          _.path.getParentsAndSelf
        }.toSet
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
