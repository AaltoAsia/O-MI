
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

import java.util.Date

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.ActorRef
import analytics.{AddRead, AnalyticsStore}
import database.{DB, GetTree, DBReadWrite, SingleStores}

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.NodeSeq
//import akka.http.StatusCode

import types.OdfTypes._
import types.OmiTypes._
import http.{ActorSystemContext, Storages}

trait ReadHandler extends OmiRequestHandlerBase {
  protected implicit def dbConnection: DB
  protected implicit def singleStores: SingleStores
  protected implicit def analyticsStore: Option[ActorRef]
  /** Method for handling ReadRequest.
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): Future[ResponseRequest] = {
     log.debug("Handling read.")
     read match{
       case ReadRequest(_,_,begin,end,Some(newest),Some(oldest),_) =>
         Future.successful(
           ResponseRequest( Vector(
             Results.InvalidRequest(
               "Both newest and oldest at the same time not supported!"
             )
           ))
       )
         
         /*
       case ReadRequest(_,_,begin,end,newest,Some(oldest),_) =>
         Future.successful(
           xmlFromResults(
             1.0,
             Results.simple(
               "400",
               Some("Oldest not supported with Warp10 integration!")
             )
           )
         )*/
       case default: ReadRequest =>
         val leafs = getLeafs(read.odf)
         // NOTE: Might go off sync with tree or values if the request is large,
         // but it shouldn't be a big problem
         val metadataTree = (singleStores.hierarchyStore execute GetTree())

         //Find nodes from the request that HAVE METADATA OR DESCRIPTION REQUEST
         def nodesWithoutMetadata: Option[OdfObjects] = getOdfNodes(read.odf).collect {
           case oii@OdfInfoItem(_, _, desc, mData)
            if desc.isDefined || mData.isDefined => 
              createAncestors(oii.copy(values = OdfTreeCollection()))
           case obj@OdfObject(pat, _, _, _, des, _)
             if des.isDefined  => 
               createAncestors(obj.copy(infoItems = OdfTreeCollection(), objects = OdfTreeCollection()))
         }.reduceOption(_.union(_))

         def objectsWithMetadata = 
           nodesWithoutMetadata.map( objs => metadataTree.intersect( objs ) )
          
         //Get values from database
         val objectsWithValuesO: Future[Option[OdfObjects]] = dbConnection.getNBetween(leafs, read.begin, read.end, read.newest, read.oldest)

         objectsWithValuesO.map {
           case Some(objectsWithValues) =>
             //Select requested O-DF from metadataTree and remove MetaDatas and descriptions
             val objectsWithValuesAndAttributes = 
              metadataTree.allMetaDatasRemoved.intersect( objectsWithValues.valuesRemoved )
                .union( objectsWithValues )


             val metaCombined = objectsWithMetadata.fold(objectsWithValuesAndAttributes)(metas => objectsWithValuesAndAttributes.union(metas) )
             val found = Results.Read(metaCombined)
             val requestsPaths = leafs.map { _.path }
             val foundOdfAsPaths = getLeafs(objectsWithValuesAndAttributes).flatMap { _.path.getParentsAndSelf }.toSet
             //handle analytics
             analyticsStore.foreach{ store =>
               val reqTime: Long = new Date().getTime()
               foundOdfAsPaths.foreach(store ! AddRead(_, reqTime))
             }

             val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
             val omiResults = Vector(found) ++ {
               if (notFound.nonEmpty)
                 Vector(Results.NotFoundPaths(notFound.toVector))
               else Vector.empty
             }

             ResponseRequest( omiResults )
           case None =>
             ResponseRequest( Vector(Results.NotFoundPaths(leafs.map{ p => p.path}.toVector)))
         }
     }
   }
}
