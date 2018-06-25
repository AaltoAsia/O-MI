package database

import java.util.Date

import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
//import akka.http.StatusCode

import types.OmiTypes._
import types.Path
import types.odf.{ImmutableODF, ODF, _}
import journal.Models.GetTree
import akka.pattern.ask
import scala.concurrent.duration._

trait DBReadHandler extends DBHandlerBase{
  implicit val timeout: Timeout = 2 minutes
  /** Method for handling ReadRequest.
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): Future[ResponseRequest] = {
     read match{
       case ReadRequest(_,_,begin,end,Some(newest),Some(oldest),_,_,_,_) =>
         Future.successful(
           ResponseRequest( Vector(
             Results.InvalidRequest(
               Some("Both newest and oldest at the same time not supported!")
             )
           ))
       )
       case default: ReadRequest =>
         log.debug(
           s"Read(" + 
           default.begin.map{ t => s"begin: $t," }.getOrElse("") + 
           default.end.map{ t => s"end: $t," }.getOrElse("") + 
           default.newest.map{ t => s"newest: $t," }.getOrElse("") + 
           default.oldest.map{ t => s"oldest: $t," }.getOrElse("") + 
           s"ttl: ${default.ttl} )"
          )

         val requestedODF = read.odf
         val leafs = requestedODF.getLeafs

         //Get values from database
         val odfWithValuesO: Future[Option[ODF]] = dbConnection.getNBetween(
           leafs,
           read.begin,
           read.end,
           read.newest,
           read.oldest
         )

         // NOTE: Might go off sync with tree or values if the request is large,
         // but it shouldn't be a big problem
         val fmetadataTree: Future[ImmutableODF] = (singleStores.hierarchyStore ? GetTree).mapTo[ImmutableODF]

         //Find nodes from the request that HAVE METADATA OR DESCRIPTION REQUEST
         def odfWithMetaDataRequest: ODF = ImmutableODF(requestedODF.getNodes.collect {
           case ii: InfoItem
           if ii.hasStaticData => 
             log.debug(ii.toString)
             ii.copy(values = OdfCollection())
           case obj: Object 
             if obj.hasStaticData =>
             log.debug(obj.toString)
             obj
         })

         val fodfWithMetaData: Future[ODF] = fmetadataTree.map(_.readTo( requestedODF).valuesRemoved)
          
         val resultF = odfWithValuesO.flatMap {
           case Some(odfWithValues) =>
             //Select requested O-DF from metadataTree and remove MetaDatas and descriptions
             val fmetaCombined: Future[ODF] = fodfWithMetaData.map(_.union(odfWithValues))
             val requestsPaths = leafs.map { _.path }
             val foundOdfAsPaths = odfWithValues.getPaths

             val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
             def notFoundOdf =requestedODF.selectSubTree(notFound)
             val ffound = fmetaCombined.map{
               case odf if odf.getPaths.exists(p => p != Path("Objects") ) => Some(Results.Read(odf))
               case _ => None
             }
             val nfResults = if (notFound.nonEmpty) Vector(Results.NotFoundPaths(notFoundOdf))
             else Vector.empty
             val fomiResults = ffound.map(found => nfResults ++ found.toVector)

             fomiResults.map(omiResults => ResponseRequest( omiResults ))
           case None =>
             Future.successful(ResponseRequest( Vector(Results.NotFoundPaths(requestedODF) ) ))
         }
         resultF
     }
   }
}
