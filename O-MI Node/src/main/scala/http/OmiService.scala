package http

import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.event.LoggingAdapter
import spray.routing._
import spray.http._
import spray.http.HttpHeaders.RawHeader
import MediaTypes._
import java.net.InetSocketAddress
import java.net.InetAddress

import responses._
import parsing._
import parsing.Types._
import parsing.Types.OmiTypes._
import database._

import xml._
import scala.collection.JavaConverters._
import scala.collection.JavaConversions.iterableAsScalaIterable

/**
 * Actor that handles incoming http messages
 * @param subHandler ActorRef that is used in subscription handling
 */
class OmiServiceActor(subHandler: ActorRef) extends Actor with ActorLogging with OmiService {

  /**
   * the HttpService trait defines only one abstract member, which
   * connects the services environment to the enclosing actor or test
   */
  def actorRefFactory = context

  //Used for O-MI subscriptions
  val subscriptionHandler = subHandler

  /**
   * this actor only runs our route, but you could add
   * other things here, like request stream processing
   * or timeout handling
   */
  def receive = runRoute(myRoute)

  implicit val dbobject = new SQLiteConnection

 // def settings = Settings(context.system)
}

/**
 * this trait defines our service behavior independently from the service actor
 */
trait OmiService extends HttpService {
  import scala.concurrent.ExecutionContext.Implicits.global
  def log: LoggingAdapter
  val subscriptionHandler: ActorRef

  //def settings : OmiConfigExtension 
  implicit val dbobject: DB
  /*
  private val ips = settings.externalAgentIps.asScala.map{
    case s: String => inetAddrToInt(InetAddress.getByName(s))
  }.toArray 
  private val subnets = settings.externalAgentSubnets.unwrapped().asScala.map{ 
    case (s: String, bits: Object ) => 
    (inetAddrToInt(InetAddress.getByName(s)), bits.toString.toInt )
  }.toMap 
  */
  //Handles CORS allow-origin seems to be enough
  private def corsHeaders =
    respondWithHeader(RawHeader("Access-Control-Allow-Origin", "*"))

  //Get the files from the html directory; http://localhost:8080/html/form.html
  val staticHtml =
    pathPrefix("html") {
      getFromDirectory("html")
    }

  // should be removed?
  val helloWorld =
    get {
      path("") { // Root
        corsHeaders {
          respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default
            complete {
              <html>
                <body>
                  <h1>Say hello to <i>O-MI Node service</i>!</h1>
                  <a href="/Objects">Url Data Discovery /Objects: Root of the hierarchy</a>
                  <p>
                    With url data discovery you can discover or request Objects,
                     InfoItems and values with HTTP Get request by giving some existing
                     path to the O-DF xml hierarchy.
                  </p>
                  <a href="/html/form.html">O-MI Test Client WebApp</a>
                </body>
              </html>
            }
          }
        }
      }
    }

  val getDataDiscovery =
    get {
      path(Rest) { pathStr =>
        corsHeaders {
          val path = Path(pathStr)
          Read.generateODFREST(path) match {
            case Some(Left(value)) =>
              respondWithMediaType(`text/plain`) {
                complete(value)
              }
            case Some(Right(xmlData)) =>
              respondWithMediaType(`text/xml`) {
                complete(xmlData)
              }
            case None =>
              log.debug(s"Url Discovery fail: org: [$pathStr] parsed: [$path]")
              respondWithMediaType(`text/xml`) {
                complete(404, <error>No object found</error>)
              }
          }
        }
      }
    }

  import OMISubscription._
  // XXX: lazy maybe fixes bug
  lazy val cancelResponseGen = new OMICancelGen(subscriptionHandler)
  lazy val readResponseGen = new ReadResponseGen
  lazy val pollResponseGen = new PollResponseGen

  /* Receives HTTP-POST directed to root (localhost:8080) */
  val getXMLResponse = post { // Handle POST requests from the client
    path("") {
      corsHeaders {
        entity(as[NodeSeq]) { xml =>
          val omi = OmiParser.parse(xml.toString)

          if (omi.isRight) {
            val requests = omi.right.get
            respondWithMediaType(`text/xml`) {

              var returnStatus = 200

              //FIXME: Currently sending multiple omi:omiEnvelope
              val result = requests.map {

                case read: ReadRequest =>
                  log.debug(read.toString)
                  readResponseGen.runRequest(read)

                  case poll: PollRequest =>
                    // Should give out poll result
                    pollResponseGen.runRequest(poll)

                case write: WriteRequest =>
                  //ClientIp not cathcing localhost?
                  //Should be re implemented after responseGen refactor
                  /*clientIP { ip =>
                  val remote = ip.toOption.get
                    log.warning(s"$remote tryed to use write request.")
                    if(!ips.contains( inetAddrToInt(remote) ) ||
                      !subnets.exists{ case (subnet : Int, bits : Int) =>
                      isInSubnet(subnet, bits, inetAddrToInt(remote))
                    }
                    ){
                      log.warning(s"Unauthorized $remote tryed to use write request.")
                      returnStatus = 401
                      ErrorResponse.notImplemented
                    }else {*/
                    //  log.warning(s"$remote tryed to use write request, not iplemented.")
                      log.debug(write.toString)
                      returnStatus = 501 
                      ErrorResponse.notImplemented
                   // }
                   // complete(returnStatus, ErrorResponse.notImplemented.mkString("\n"))
                  //}
                case subscription: SubscriptionRequest =>
                  log.debug(subscription.toString)

                  val (id, response) = OMISubscription.setSubscription(subscription) //setSubscription return -1 if subscription failed

                  if (subscription.callback.isDefined && subscription.callback.get.toString.length > 3 && id >= 0) // XXX: hack check for valid url :D
                    subscriptionHandler ! NewSubscription(id)

                  response

                case cancel: CancelRequest =>
                  log.debug(cancel.toString)

                  cancelResponseGen.runRequest(cancel)

                case _ =>
                  log.warning("Unknown request")
                  returnStatus = 400

              }.mkString("\n")

              complete(returnStatus, result)

            }
          } else {
            val errors = omi.left.get
            //Errors found
            log.warning("Parse Errors: {}", errors.mkString(", "))
            complete(400,
              ErrorResponse.parseErrorResponse(errors))
          }
        }
      }
    }
  }

  // Combine all handlers
  val myRoute = helloWorld ~ staticHtml ~ getDataDiscovery ~ getXMLResponse

  def inetAddrToInt(addr: InetAddress) : Int = {
    val b : Array[Byte] = addr.getAddress()
    val ip : Int = ((b(0) & 0xFF) << 24) |
      ((b(1) & 0xFF) << 16) |
      ((b(2) & 0xFF) << 8)  |
      ((b(3) & 0xFF) << 0);
    ip
  }
  def isInSubnet(subnet: Int, bits: Int, ip: Int) : Boolean = {
    val mask = -1 << (32 - bits)  
    (subnet & mask) == (ip & mask) 
  }
}
