/**
  Copyright (c) 2015 Aalto University.

  Licensed under the 4-clause BSD (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at top most directory of project.

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
**/
package responses

import http.Boot
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

import akka.event.{ LoggingAdapter, Logging, LogSource}
import akka.actor.{ Actor,  ActorLogging, ActorRef }
import akka.util.Timeout
import akka.pattern.ask

import java.util.Date
import java.net.{ URL, InetAddress, UnknownHostException }

import types._
import OmiTypes._
import OdfTypes._
import OmiGenerator._
import parsing.xmlGen.{ xmlTypes, scalaxb, defaultScope }
import agentSystem.InputPusher
import CallbackHandlers._
import database._

trait OmiRequestHandler { 

  type Handle = PartialFunction[OmiRequest, Future[NodeSeq]]
  var handles: Handle = PartialFunction.empty 
  def handler(next: Handle) { handles = handles orElse next }
  final def handle = handles
  protected final def handleTTL( ttl: Duration) : FiniteDuration = if( ttl.isFinite ) {
        if(ttl.toSeconds != 0)
          FiniteDuration(ttl.toSeconds, SECONDS)
        else
          FiniteDuration(2,MINUTES)
      } else {
        FiniteDuration(Int.MaxValue,MILLISECONDS)
      }
  implicit def  dbConnection: DB
  def log: LoggingAdapter
  protected[this] def date = new Date()
}
class RequestHandler(val subscriptionManager: ActorRef, val agentSystem: ActorRef)(implicit val dbConnection: DB) extends ReadHandler with WriteHandler with ResponseHandler with SubscriptionHandler with PollHandler with CancelHandler{
  import http.Boot.system.log

  implicit val logSource: LogSource[RequestHandler]= new LogSource[RequestHandler] {
      def genString(requestHandler: RequestHandler ) = requestHandler.toString
    }
  def log = Logging( http.Boot.system, this)

  def handleRequest(request: OmiRequest)(implicit ec: ExecutionContext): Future[NodeSeq] = {

    def checkCallback(address: String) = Try {
      val url = new URL(address)
      val addr = InetAddress.getByName(url.getHost)
      val protocol = url.getProtocol()
      if (protocol != "http" && protocol != "https")
        throw new java.net.ProtocolException(s"Unsupported protocol: $protocol")

    }

    request.callback match {
      
      case Some(address) => {
        checkCallback(address).map { x =>
          request match {
            case sub: SubscriptionRequest => runGeneration(sub)
            case _ => {
              // TODO: Can't cancel this callback
              runGeneration(request)  map { xml =>
                  sendCallback(
                    address,
                    xml,
                    request.ttl
                  )
                 xmlFromResults(
                  1.0,
                  Results.simple("200", Some("OK, callback job started")))



              }

           }
          }
        } match {
          case Success(res)                               => res
          case Failure(e: java.net.MalformedURLException) => Future.successful(invalidCallback(e.getMessage))
          case Failure(e: UnknownHostException)           => Future.successful(invalidCallback("Unknown host: " + e.getMessage))
          case Failure(e: SecurityException)              => Future.successful(invalidCallback("Unauthorized " + e.getMessage))
          case Failure(e: java.net.ProtocolException)     => Future.successful(invalidCallback(e.getMessage))
          case Failure(t)                                 => throw t
        }

      }
      case None => {
        request match {
          case _ => runGeneration(request)
        }
      }
    }
  }


  /**
   * Method for running response generation. Handles tiemout etc. upper level failures.
   *
   * @param request request is O-MI request to be handled
   */
  def runGeneration(request: OmiRequest)(implicit ec: ExecutionContext): Future[NodeSeq] = {
    handles(request).recoverWith{
      case e: TimeoutException => Future.successful(OmiGenerator.timeOutError(e.getMessage))
      case e: IllegalArgumentException => Future.successful(OmiGenerator.invalidRequest(e.getMessage))
      case e =>
        log.error(e, "Internal Server Error: ")
        Future.successful(OmiGenerator.internalError(e))
    }

    /*Try {
      Await.result(responseFuture, request.ttl)
    } match {
      case Success((xml: NodeSeq, code: Int)) => (xml, code)
      case Success(a)                         => a //TODO does this fix default case not specified problem?

      case Failure(e: TimeoutException) =>
        (OmiGenerator.timeOutError(e.getMessage), 503)
      case Failure(e: IllegalArgumentException) =>
        (OmiGenerator.invalidRequest(e.getMessage), 400)

      case Failure(e) => // all exception should be re-thrown here to log it consistently
        actionOnInternalError(e)
        (OmiGenerator.internalError(e), 500)
    }*/
  }

  /**
   * Method to be called for handling internal server error, logging and stacktrace.
   *
   * @param request request is O-MI request to be handled
   */
  def actionOnInternalError: Throwable => Unit = { error =>
    //println("[ERROR] Internal Server error:")
    //error.printStackTrace()
    log.error(error, "Internal server error: ")
  }

  private sealed trait ODFRequest {def path: Path} // path is OdfNode path
  private case class Value(path: Path)      extends ODFRequest
  private case class MetaData(path: Path)   extends ODFRequest
  private case class Description(path: Path)extends ODFRequest
  private case class ObjId(path: Path)      extends ODFRequest
  private case class InfoName(path: Path)   extends ODFRequest
  private case class NodeReq(path: Path)    extends ODFRequest

  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def generateODFREST(orgPath: Path): Option[Either[String, xml.Node]] = {

    def getODFRequest(path: Path): ODFRequest = path.lastOption match {
      case attr @ Some("value")      => Value(path.init)
      case attr @ Some("MetaData")   => MetaData(path.init)
      case attr @ Some("description")=> Description(path.init)
      case attr @ Some("id")         => ObjId(path.init)
      case attr @ Some("name")       => InfoName(path.init)
      case _                         => NodeReq(path)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val request = getODFRequest(orgPath)

    request match {
      case Value(path) =>
        SingleStores.latestStore execute LookupSensorData(path) map { Left apply _.value }

      case MetaData(path) =>
        SingleStores.getMetaData(path) map { metaData =>
          Right(XML.loadString(metaData.data))
        }
      case ObjId(path) =>{  //should this query return the id as plain text or inside Object node?
        val xmlReturn = SingleStores.getSingle(path).map{
          case odfObj: OdfObject =>
            scalaxb.toXML[xmlTypes.ObjectType](
              odfObj.copy(infoItems = OdfTreeCollection(),objects = OdfTreeCollection(), description = None)
              .asObjectType, Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObject </error>
            )
          case odfObjs: OdfObjects => <error>Id query not supported for root Object</error>
          case odfInfoItem: OdfInfoItem => <error>Id query not supported for InfoItem</error>
        }

        xmlReturn map Right.apply
        //Some(Right(<Object xmlns="odf.xsd"><id>{path.last}</id></Object>)) // TODO: support for multiple id
      }

      case InfoName(path) =>
        Some(Right(<InfoItem xmlns="odf.xsd" name={path.last}><name>{path.last}</name></InfoItem>))
        // TODO: support for multiple name

      case Description(path) =>
        SingleStores.hierarchyStore execute GetTree() get path flatMap (
          _.description map (_.value)
        ) map Left.apply _
        
      case NodeReq(path) =>
        val xmlReturn = SingleStores.getSingle(path) map {

          case odfObj: OdfObject =>
            scalaxb.toXML[xmlTypes.ObjectType](
              odfObj.asObjectType, Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObject </error>
            )

          case odfObj: OdfObjects =>
            scalaxb.toXML[xmlTypes.ObjectsType](
              odfObj.asObjectsType, Some("odf"), Some("Objects"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObjects </error>
            )

          case infoitem: OdfInfoItem =>
            scalaxb.toXML[xmlTypes.InfoItemType](
              infoitem.asInfoItemType, Some("odf"), Some("InfoItem"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfInfoItem</error>
            )
        }

        xmlReturn map Right.apply
    }
  }

  def handlePathRemove(parentPath: Path): Boolean = {
    val objects = SingleStores.hierarchyStore execute GetTree()
    val node = objects.get(parentPath)
    node match {
      case Some(node) => {

        val leafs = getInfoItems(node).map(_.path)

        SingleStores.hierarchyStore execute TreeRemovePath(parentPath)

        leafs.foreach{path =>
          log.info(s"removing $path")
          SingleStores.latestStore execute EraseSensorData(path)
        }

        dbConnection.remove(parentPath)
        true

      }
      case None => false
    }
  }

}

/**
 * Actor for handling all request.
 *
class RequestHandler(val subscriptionHandler: ActorRef)(implicit val dbConnection: DB) {

  import http.Boot.system.log
  private[this] def date = new Date()

  /**
   * Main interface for hanling O-MI request
   *
   * @param request request is O-MI request to be handled
   */
  def handleRequest(request: OmiRequest)(implicit ec: ExecutionContext): Future[NodeSeq] = {

    def checkCallback(address: String) = Try {
      val url = new URL(address)
      val addr = InetAddress.getByName(url.getHost)
      val protocol = url.getProtocol()
      if (protocol != "http" && protocol != "https")
        throw new java.net.ProtocolException(s"Unsupported protocol: $protocol")

    }

    request.callback match {
      
      case Some(address) => {
        checkCallback(address).map { x =>
          request match {
            case sub: SubscriptionRequest => runGeneration(sub)
            case _ => {
              // TODO: Can't cancel this callback
              runGeneration(request)  map { xml =>
                  sendCallback(
                    address,
                    xml,
                    request.ttl
                  )
                 xmlFromResults(
                  1.0,
                  Results.simple("200", Some("OK, callback job started")))



              }

           }
          }
        } match {
          case Success(res)                               => res
          case Failure(e: java.net.MalformedURLException) => Future.successful(invalidCallback(e.getMessage))
          case Failure(e: UnknownHostException)           => Future.successful(invalidCallback("Unknown host: " + e.getMessage))
          case Failure(e: SecurityException)              => Future.successful(invalidCallback("Unauthorized " + e.getMessage))
          case Failure(e: java.net.ProtocolException)     => Future.successful(invalidCallback(e.getMessage))
          case Failure(t)                                 => throw t
        }

      }
      case None => {
        request match {
          case _ => runGeneration(request)
        }
      }
    }
  }

  /**
   * Method for running response generation. Handles tiemout etc. upper level failures.
   *
   * @param request request is O-MI request to be handled
   */
  def runGeneration(request: OmiRequest)(implicit ec: ExecutionContext): Future[NodeSeq] = {
    xmlFromRequest(request).recoverWith{
      case e: TimeoutException => Future.successful(OmiGenerator.timeOutError(e.getMessage))
      case e: IllegalArgumentException => Future.successful(OmiGenerator.invalidRequest(e.getMessage))
      case e =>
        log.error(e, "Internal Server Error: ")
        Future.successful(OmiGenerator.internalError(e))
    }

    /*Try {
      Await.result(responseFuture, request.ttl)
    } match {
      case Success((xml: NodeSeq, code: Int)) => (xml, code)
      case Success(a)                         => a //TODO does this fix default case not specified problem?

      case Failure(e: TimeoutException) =>
        (OmiGenerator.timeOutError(e.getMessage), 503)
      case Failure(e: IllegalArgumentException) =>
        (OmiGenerator.invalidRequest(e.getMessage), 400)

      case Failure(e) => // all exception should be re-thrown here to log it consistently
        actionOnInternalError(e)
        (OmiGenerator.internalError(e), 500)
    }*/
  }

  /**
   * Method to be called for handling internal server error, logging and stacktrace.
   *
   * @param request request is O-MI request to be handled
   */
  def actionOnInternalError: Throwable => Unit = { error =>
    //println("[ERROR] Internal Server error:")
    //error.printStackTrace()
    log.error(error, "Internal server error: ")
  }

  /**
   * Generates xml from request, match request and call specific method for generation.
   *
   * @param request request is O-MI request to be handled
   * @return Tuple containing xml message and HTTP status code
   */
  def xmlFromRequest(request: OmiRequest): Future[NodeSeq] = request match {
    case read: ReadRequest => {
      handleRead(read)
    }
    case poll: PollRequest => {
      //When sender wants to poll data of some subscription
      handlePoll(poll)
    }
    case subscription: SubscriptionRequest => {
      //When subscription is created
      handleSubscription(subscription)
    }
    case write: WriteRequest => {
      handleWrite(write)
    }
    case response: ResponseRequest => {
      handleResponse(response)
    }
    case cancel: CancelRequest => {
      handleCancel(cancel)
    }
    case _ => {
      Future.successful(xmlFromResults(1.0, Results.simple("500", Some("Unknown request."))))
    }
  }

  private def handleTTL( ttl: Duration) : FiniteDuration = if( ttl.isFinite ) {
        if(ttl.toSeconds != 0)
          FiniteDuration(ttl.toSeconds, SECONDS)
        else
          FiniteDuration(2,MINUTES)
      } else {
        FiniteDuration(Int.MaxValue,MILLISECONDS)
      }

  /** Method for handling WriteRequest.
    * @param write request
    * @return (xml response, HTTP status code)
    */
  def handleWrite( write: WriteRequest ) : Future[NodeSeq] ={
      val ttl = handleTTL(write.ttl)
      val future : Future[Try[Boolean]] = InputPusher.handleObjects(write.odf.objects, new Timeout(ttl.toSeconds, SECONDS)).mapTo[Try[Boolean]]
      future.recoverWith{case e =>{
        log.error(e, "Failure when writing")
        Future.failed(e)
      }}

      //val result = Await.result(future, ttl)
      future.flatMap(result => result match {
        case Success(b: Boolean ) =>
          if(b)
            Future.successful(success)
          else{
            log.warning("Write failed without exception")
            Future.failed(new RuntimeException("Write failed without exception."))
            }
        case Failure(thro: Throwable) => {
          log.error(thro, "Failure when writing")
          Future.failed(thro)
        }
      })
  }
  /** Method for handling ResponseRequest.
    * @param response request
    * @return (xml response, HTTP status code)
    */
  def handleResponse( response: ResponseRequest ) : Future[NodeSeq] ={
      val ttl = handleTTL(response.ttl)
      val resultFuture = Future.sequence(response.results.map{ result =>
           result.odf match {
            case Some(odf) =>
            val future =  InputPusher.handleObjects(odf.objects, new Timeout(ttl)).mapTo[Try[Boolean]]
              future.map{res => res match {
                case Success(true) => Results.success
                case Success(false) => Results.invalidRequest("Failed without exception.")
                case Failure(thro: Throwable) => throw thro
              }}
            case None => //noop?
              Future.successful(Results.success)
          }
        }.toSeq
      )

    resultFuture.map(results =>
      xmlFromResults(
        1.0,
        results:_*
      )
      )
  
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

  /** Method for handling PollRequest.
    * @param poll request
    * @return (xml response, HTTP status code)
    */
  def handlePoll(poll: PollRequest): Future[NodeSeq] = {
    val ttl = handleTTL(poll.ttl)
    implicit val timeout = Timeout(ttl) 
    val time = date.getTime
    val resultsFut =
      Future.sequence(poll.requestIDs.map { id =>

      val objectsF: Future[ Any /* Option[OdfObjects] */ ] = (subscriptionHandler ? PollSubscription(id)).mapTo[Future[Option[OdfObjects]]].flatMap(n=>n)
      objectsF.recoverWith{case e => Future.failed(new RuntimeException(
        s"Error when trying to poll subscription: ${e.getMessage}"))}

      objectsF.map(res => res match {
        case Some(objects: OdfObjects) =>
          Results.poll(id.toString, objects)
        case None =>
          Results.notFoundSub(id.toString)
        //case Failure(e) =>
        //  throw new RuntimeException(
        //    s"Error when trying to poll subscription: ${e.getMessage}")
      })
    })
    val returnTuple = resultsFut.map(results =>
      xmlFromResults(
        1.0,
        results.toSeq: _*)
    )

    returnTuple
  }

  /** Method for handling SubscriptionRequest.
    * @param _subscription request
    * @return (xml response, HTTP status code)
    */
  def handleSubscription(_subscription: SubscriptionRequest): Future[NodeSeq] = {
    //if interval is below allowed values, set it to minimum allowed value
    val subscription: SubscriptionRequest = _subscription match {
      case SubscriptionRequest( _, interval, _, _, _, _) if interval.toSeconds < Boot.settings.minSubscriptionInterval && interval.toSeconds >= 0 =>
        _subscription.copy(interval=Boot.settings.minSubscriptionInterval.seconds)
      case s => s
    }
    val ttl = handleTTL(subscription.ttl)
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    val subFuture: Future[RequestResultType] = (subscriptionHandler ? NewSubscription(subscription)).mapTo[Try[Long]].map( res=>res  match {
        case Success(id: Long) if _subscription.interval != subscription.interval =>
          Results.subscription(id.toString,subscription.interval.toSeconds)
        case Success(id: Long) =>
          Results.subscription(id.toString)
        case Failure(ex) => throw ex
      }).recoverWith{
      case e: IllegalArgumentException => Future.successful(Results.invalidRequest(e.getMessage()))
      case e => Future.failed(new RuntimeException(s"Error when trying to create subscription: ${e.getMessage}", e))
    }
    subFuture.map{ response =>{

        xmlFromResults(
          1.0,
          response)

    }}
  }

  /** Method for handling CancelRequest.
    * @param cancel request
    * @return (xml response, HTTP status code) wrapped in a Future
    */
  def handleCancel(cancel: CancelRequest): Future[NodeSeq] = {
    log.debug("Handling cancel.")
    implicit val timeout = Timeout(10.seconds) // NOTE: ttl will timeout from elsewhere
    var returnCode = 200
    val jobs = Future.sequence(cancel.requestID.map { id =>
      (subscriptionHandler ? RemoveSubscription(id)).mapTo[Boolean].map( res =>
        if(res){
          Results.success
        }else{
          Results.notFoundSub
        }
      ).recoverWith{
        case e => {
          val error = "Error when trying to cancel subcription: "
          log.error(e, error)
          Future.successful(Results.internalError(error + e.toString))
        }
      }
    })

    jobs.map( res =>
    (
      xmlFromResults(
        1.0,
      res.toSeq: _*
        )))
  }

  private sealed trait ODFRequest {def path: Path} // path is OdfNode path
  private case class Value(path: Path)      extends ODFRequest
  private case class MetaData(path: Path)   extends ODFRequest
  private case class Description(path: Path)extends ODFRequest
  private case class ObjId(path: Path)      extends ODFRequest
  private case class InfoName(path: Path)   extends ODFRequest
  private case class NodeReq(path: Path)    extends ODFRequest

  /**
   * Generates ODF containing only children of the specified path's (with path as root)
   * or if path ends with "value" it returns only that value.
   *
   * @param orgPath The path as String, elements split by a slash "/"
   * @return Some if found, Left(string) if it was a value and Right(xml.Node) if it was other found object.
   */
  def generateODFREST(orgPath: Path): Option[Either[String, xml.Node]] = {

    def getODFRequest(path: Path): ODFRequest = path.lastOption match {
      case attr @ Some("value")      => Value(path.init)
      case attr @ Some("MetaData")   => MetaData(path.init)
      case attr @ Some("description")=> Description(path.init)
      case attr @ Some("id")         => ObjId(path.init)
      case attr @ Some("name")       => InfoName(path.init)
      case _                         => NodeReq(path)
    }

    // safeguard
    assert(!orgPath.isEmpty, "Undefined url data discovery: empty path")

    val request = getODFRequest(orgPath)

    request match {
      case Value(path) =>
        SingleStores.latestStore execute LookupSensorData(path) map { Left apply _.value }

      case MetaData(path) =>
        SingleStores.getMetaData(path) map { metaData =>
          Right(XML.loadString(metaData.data))
        }
      case ObjId(path) =>{  //should this query return the id as plain text or inside Object node?
        val xmlReturn = SingleStores.getSingle(path).map{
          case odfObj: OdfObject =>
            scalaxb.toXML[xmlTypes.ObjectType](
              odfObj.copy(infoItems = OdfTreeCollection(),objects = OdfTreeCollection(), description = None)
              .asObjectType, Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObject </error>
            )
          case odfObjs: OdfObjects => <error>Id query not supported for root Object</error>
          case odfInfoItem: OdfInfoItem => <error>Id query not supported for InfoItem</error>
        }

        xmlReturn map Right.apply
        //Some(Right(<Object xmlns="odf.xsd"><id>{path.last}</id></Object>)) // TODO: support for multiple id
      }

      case InfoName(path) =>
        Some(Right(<InfoItem xmlns="odf.xsd" name={path.last}><name>{path.last}</name></InfoItem>))
        // TODO: support for multiple name

      case Description(path) =>
        SingleStores.hierarchyStore execute GetTree() get path flatMap (
          _.description map (_.value)
        ) map Left.apply _
        
      case NodeReq(path) =>
        val xmlReturn = SingleStores.getSingle(path) map {

          case odfObj: OdfObject =>
            scalaxb.toXML[xmlTypes.ObjectType](
              odfObj.asObjectType, Some("odf"), Some("Object"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObject </error>
            )

          case odfObj: OdfObjects =>
            scalaxb.toXML[xmlTypes.ObjectsType](
              odfObj.asObjectsType, Some("odf"), Some("Objects"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfObjects </error>
            )

          case infoitem: OdfInfoItem =>
            scalaxb.toXML[xmlTypes.InfoItemType](
              infoitem.asInfoItemType, Some("odf"), Some("InfoItem"), defaultScope
            ).headOption.getOrElse(
              <error>Could not create from OdfInfoItem</error>
            )
        }

        xmlReturn map Right.apply
    }
  }

  def handlePathRemove(parentPath: Path): Boolean = {
    val objects = SingleStores.hierarchyStore execute GetTree()
    val node = objects.get(parentPath)
    node match {
      case Some(node) => {

        val leafs = getInfoItems(node).map(_.path)

        SingleStores.hierarchyStore execute TreeRemovePath(parentPath)

        leafs.foreach{path =>
          log.info(s"removing $path")
          SingleStores.latestStore execute EraseSensorData(path)
        }

        dbConnection.remove(parentPath)
        true

      }
      case None => false
    }
  }

}
 */
