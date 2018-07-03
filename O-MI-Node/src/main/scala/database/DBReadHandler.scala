package database

import java.util.Date

import analytics.{AddRead, AddUser}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
//import akka.http.StatusCode

import types.OmiTypes._
import types.Path
import types.odf.{ODF}

trait DBReadHandler extends DBHandlerBase{
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
         val metadataTree = singleStores.hierarchyStore execute GetTree()


         val odfWithMetaData = metadataTree.readTo( requestedODF).valuesRemoved 
          
         val resultF = odfWithValuesO.map {
           case Some(odfWithValues) =>
             //Select requested O-DF from metadataTree and remove MetaDatas and descriptions
             /*
             val odfWithValuesAndAttributes = metadataTree.mutable
               .metaDatasRemoved
               .descriptionsRemoved
               .intersection( odfWithValues.valuesRemoved )
               .union( odfWithValues )
               */


             val metaCombined  = odfWithMetaData.union(odfWithValues)
             val requestsPaths = leafs.map { _.path }
             val foundOdfAsPaths = metaCombined.getPaths
             //handle analytics
             analyticsStore.foreach{ store =>
               val reqTime: Long = new Date().getTime()
               foundOdfAsPaths.foreach(path => {
                 store ! AddRead(path, reqTime)
                 store ! AddUser(path, read.user.remoteAddress.map(_.hashCode()), reqTime)
               })
             }

             val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
             def notFoundOdf =requestedODF.selectSubTree(notFound)
             val found = if( metaCombined.getPaths.exists(p => p != Path("Objects") )) Some( Results.Read(metaCombined) ) else None
             val nfResults = if (notFound.nonEmpty) Vector(Results.NotFoundPaths(notFoundOdf)) 
             else Vector.empty
             val omiResults = nfResults ++ found.toVector

             ResponseRequest( omiResults )
           case None =>
             ResponseRequest( Vector(Results.NotFoundPaths(requestedODF) ) )
         }
         resultF
     }
   }
}
