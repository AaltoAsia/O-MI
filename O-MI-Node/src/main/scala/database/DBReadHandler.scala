package database

import java.sql.Timestamp
import java.util.Date
import akka.util.Timeout

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

//import scala.collection.JavaConverters._ //JavaConverters provide explicit conversion methods
//import scala.collection.JavaConversions.asJavaIterator
//import akka.http.StatusCode

import types.OmiTypes._
import types.Path
import types.odf.{ImmutableODF, ODF}
import utils._

trait DBReadHandler extends DBHandlerBase {
  def currentTimestamp = new Timestamp( new Date().getTime)
  /** Method for handling ReadRequest.
    *
    * @param read request
    * @return (xml response, HTTP status code)
    */
  def handleRead(read: ReadRequest): Future[ResponseRequest] = {
    implicit val timeout: Timeout = read.handleTTL
    read match {
      case read: ReadRequest if read.newest.nonEmpty && read.oldest.nonEmpty=>
        Future.successful(
          ResponseRequest(Vector(
            Results.InvalidRequest(
              Some("Both newest and oldest at the same time not supported!")
            )
          ))
        )
      case default: ReadRequest =>
        log.debug(
          s"Read(" +
            default.begin.map { t => s"begin: $t," }.getOrElse("") +
            default.end.map { t => s"end: $t," }.getOrElse("") +
            default.newest.map { t => s"newest: $t," }.getOrElse("") +
            default.oldest.map { t => s"oldest: $t," }.getOrElse("") +
            s"ttl: ${default.ttl} )"
        )

        val requestedODF = read.odf
        val timer = LapTimer(log.info)
        val leafs = requestedODF.getLeafs
        timer.step("Got leafs")

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
        val mdtimer = LapTimer(log.info)
        val fmetadataTree: Future[ImmutableODF] = singleStores.getHierarchyTree()


        val fodfWithMetaData: Future[ODF] = fmetadataTree.map{
          mdOdf =>
          mdtimer.step("Got MD ODF")
          val tmp = mdOdf.readTo(requestedODF)
          mdtimer.step("Read MD ODF")
          val tmp1 = tmp.valuesRemoved
          mdtimer.step("MD ODF values removed")
          tmp1
        }

        val resultF = odfWithValuesO.flatMap {
          case Some(odfWithValues) =>
          timer.step("Got value ODF")
            
            //Select requested O-DF from metadataTree and remove MetaDatas and descriptions
            val fmetaCombined: Future[ODF] = fodfWithMetaData.map{
              mdOdf =>
              mdtimer.step("Got MD ODF ready")
              val t = mdOdf.union(odfWithValues)
              mdtimer.step("MD value union ODF")
              mdtimer.total()

              t
            }
            fmetaCombined.foreach{ _ => timer.step("metacompined")}
            val result = for {
              metaCombined <- fmetaCombined
              requestsPaths = leafs.map {
                _.path
              }
              foundOdfAsPaths = metaCombined.getPaths.toSet
              notFound = requestsPaths.filterNot { path => foundOdfAsPaths.contains(path) }.toSet.toSeq
              found = metaCombined match {
                case odf if odf.getPaths.exists(p => p != Path("Objects")) => 
                  Some(Results.Read(odf))
                case _ => None
              }
              nfResults = if (notFound.nonEmpty) {
                val notFoundOdf = requestedODF.selectSubTree(notFound.toSet)
                Vector(Results.NotFoundPaths(notFoundOdf))
              } else Vector.empty
              omiResults = nfResults ++ found.toVector

            } yield ResponseRequest(omiResults)
            result.foreach{
              r =>
                timer.step("result")
                timer.total()
            }
            result

          case None =>
            Future.successful(ResponseRequest(Vector(Results.NotFoundPaths(requestedODF))))
        }
        resultF
    }
  }
}
