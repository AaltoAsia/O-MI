package database

import java.util.Date

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import akka.actor.{Actor, ActorRef, ActorSystem, ActorLogging}
import analytics.{AddUser, AddRead, AnalyticsStore}

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
import scala.xml.{NodeSeq, PrettyPrinter}
//import akka.http.StatusCode

import types.odf.{ NewTypeConverter, ImmutableODF, ODF, OldTypeConverter }
import types.OmiTypes._
import types.odf._
import types.Path
import types.Path._
import http.{ActorSystemContext, Storages}

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
        //Read all
         /*
       case ReadRequest(objs,_,None,None,None,None,_,_,_) if objs.objects.isEmpty=>
         log.info( "Received read all.")
         Future{
           // NOTE: Might go off sync with tree or values if the request is large,
           // but it shouldn't be a big problem
           val metadataTree = (singleStores.hierarchyStore execute GetTree())

           val pathToValue = singleStores.latestStore execute LookupSensorDatas( metadataTree.intersect( objects ).infoItems.map(_.path)) 
           val objectsWithValues = pathToValue.map{
             case ( path: Path, value: OdfValue[Any]) => OdfInfoItem( path, values = Vector( value)).createAncestors
             }.fold(OdfObjects()){
               case ( odf: OdfObjects, iiObjs: OdfObjects) => odf.union( iiObjs)
             }

             //Find nodes from the request that HAVE METADATA OR DESCRIPTION REQUEST
             val nodesWithoutMetadata: Option[OdfObjects] = getOdfNodes(objects).collect {
               case oii@OdfInfoItem(_, _, desc, mData, typeValue,attr)
               if desc.isDefined || mData.isDefined || typeValue.nonEmpty ||attr.nonEmpty=> 
                 createAncestors(oii.copy(values = OdfTreeCollection()))
               case obj@OdfObject(pat, _, _, _, des, _,attr)
               if des.isDefined  || attr.nonEmpty => 
                 createAncestors(obj.copy(infoItems = OdfTreeCollection(), objects = OdfTreeCollection()))
             }.reduceOption(_.union(_))

             def objectsWithMetadata = nodesWithoutMetadata.map( objs => metadataTree.intersect( objs ) )

             //Select requested O-DF from metadataTree and remove MetaDatas and descriptions
             val objectsWithValuesAndAttributes = metadataTree
               .allMetaDatasRemoved
               .intersect( objectsWithValues.valuesRemoved )
               .union( objectsWithValues )

               val metaCombined = objectsWithMetadata.fold(objectsWithValuesAndAttributes){
                 metas => objectsWithValuesAndAttributes.union(metas) 
               }

               ResponseRequest( Vector(
                 Results.Read( metaCombined) )
               ) 
         }
         
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

         //Find nodes from the request that HAVE METADATA OR DESCRIPTION REQUEST
         def odfWithMetaDataRequest: ODF = ImmutableODF(requestedODF.getNodes.collect {
           case ii: InfoItem
           if ii.hasStaticData => 
             log.debug(ii.toString)
             ii.copy(values = OdfTreeCollection())
           case obj: Object 
             if obj.hasStaticData =>
             log.debug(obj.toString)
             obj
         })

         val odfWithMetaData = metadataTree.readTo( requestedODF).valuesRemoved 
         log.debug("Nodes with metadata:\n" + odfWithMetaData.getNodes.mkString("\n"))
          
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

              log.debug("Nodes with values:\n" + odfWithValues.getNodes.mkString("\n"))

             val metaCombined  = odfWithMetaData.union(odfWithValues)
             val requestsPaths = leafs.map { _.path }
             val foundOdfAsPaths = odfWithValues.getPaths
             //handle analytics
             analyticsStore.foreach{ store =>
               val reqTime: Long = new Date().getTime()
               foundOdfAsPaths.foreach(path => {
                 store ! AddRead(path, reqTime)
                 store ! AddUser(path, read.user.remoteAddress.map(_.hashCode()), reqTime)
               })
             }

             val notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
             def notFoundOdf =requestedODF.getSubTreeAsODF(notFound)
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
