package parsing

import sensorDataStructure._
import scala.xml._
import scala.util.Try

/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser {
  private val implementedRequest = Seq("read", "write", "cancel", "response")
  
  /** This method calls the OdfParser class to parse the data when the O-MI message has been parsed.
   *  this method converts the Node message to xml string.
   *  
   *  @param msg scala.xml.Node that contains the data in O-DF format
   *  @return sequence of ParseResults, type ParseResult is defined in the OdfParser class.
   */
  private def parseODF(msg: Node) = {
    OdfParser.parse(new PrettyPrinter(80, 2).format(msg))
  }
  
  /** Parse the given XML string into sequence of ParseMsg classes
   * 
   * @param xml_msg O-MI formatted message that is to be parsed
   * @return sequence of ParseMsg classes, different message types are defined in
   *         the TypeClasses.scala file
   */
  def parse(xml_msg: String): Seq[ParseMsg] = {


    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(new ParseError("Invalid XML")))

    if (root.prefix != "omi")
      return Seq(new ParseError("Incorrect prefix"))
    if (root.label != "omiEnvelope")
      return Seq(new ParseError("XML's root isn't omi:omiEnvelope"))

    val request = root.child.collect {
      case el: Elem => el
    }.headOption.getOrElse(
      return Seq(new ParseError("omi:omiEnvelope doesn't contain request")))

    val ttl = (root \ "@ttl").text
    if (ttl.isEmpty())
      return Seq(new ParseError("No ttl present in O-MI Envelope"))

    parseNode(request, ttl)

  }

  /** Private method that is called inside parse method. This method checks which O-MI message
   *  type the message contains and handles them.
   *  
   *  @param node scala.xml.Node that should contain the message to be parsed e.g. read or write messages
   *  @param ttl ttl of the omiEnvalope as string. ttl is in seconds.
   * 
   */
  private def parseNode(node: Node, ttl: String): Seq[ParseMsg] = {
    if (node.prefix != "omi")
      return Seq(new ParseError("Incorrect prefix"))
    node.label match {
      /*
        Write request 
      */
      case "write" => {
        val msgformat = (node \ "@msgformat").headOption.getOrElse(
          return Seq(new ParseError("No msgformat in write request"))).text
        msgformat match {
          case "odf" => {
            val msg = (node \ "msg").headOption.getOrElse {
              return Seq(new ParseError("No message node found in write node."))
            }
            val odf = parseODF((msg \ "Objects").headOption.getOrElse(
              return Seq(new ParseError("No Objects node found in msg node."))))
            val left = odf.filter(_.isLeft)
            val right = odf.filter(_.isRight)

            if (left.isEmpty && !right.isEmpty) {
              Seq(Write(ttl, right.map(_.right.get)))
            } else if (!left.isEmpty) {
              left.map(_.left.get)
            } else { Seq(ParseError("No Objects to parse")) }
          }
          
          /* default case */
          case _ => Seq(new ParseError("Unknown message format."))
        }
      }

      /*
        Read request 
      */
      case "read" => {
        val msgformat = (node \ "@msgformat").headOption.getOrElse(
          return Seq(new ParseError("No msgformat in read request"))).text

        msgformat match {
          case "odf" => {
            val msg = (node \ "msg").headOption.getOrElse {
              return Seq(new ParseError("No message node found in read node."))
            }
            val interval = (node \ "@interval").headOption
            val odf = parseODF((msg \ "Objects").headOption.getOrElse(
              return Seq(new ParseError("No Objects node found in msg node."))))
            val left = odf.filter(_.isLeft)
            val right = odf.filter(_.isRight)

            if (left.isEmpty && !right.isEmpty) {
              if (interval.isEmpty) {
                Seq(OneTimeRead(ttl, right.map(_.right.get)))
              } else {
                Seq(Subscription(ttl, interval.get.text, right.map(_.right.get)))
              }
            } else if (!left.isEmpty) {
              left.map(_.left.get)
            } else { Seq(ParseError("No Objects to parse")) }
          }

          case _ => Seq(new ParseError("Unknown message format."))
        }
      }

      /*
        Cancel request 
      */
      case "cancel" => Seq(new ParseError("Unimplemented O-MI node."))

      /*
        Response 
      */
      case "response" => {
        parseNode((node \ "result").headOption.getOrElse(
          return Seq(new ParseError("No result node in response node"))), ttl)
      }

      /*
        Response's Result 
      */
      case "result" => {
        val msgformat = (node \ "@msgformat").headOption.getOrElse(
          return Seq(new ParseError("No msgformat in result message"))).text
        val returnValue = (node \ "return").headOption.getOrElse(
          return Seq(new ParseError("No return node in result node"))).text
        val msgOp = (node \ "msg").headOption
        if (msgOp.isEmpty)
          return Seq(Result(returnValue, None))
        else {
          msgformat match {
            case "odf" => {
              val odf = parseODF((msgOp.get \ "Objects").headOption.getOrElse(
                return Seq(new ParseError("No Objects node found in msg node."))))
              val left = odf.filter(_.isLeft)
              val right = odf.filter(_.isRight)

              if (left.isEmpty && !right.isEmpty) {
                return Seq(Result(returnValue, Some(right.map(_.right.get))))
              } else if (!left.isEmpty) {
                left.map(_.left.get)
              } else { Seq(ParseError("No Objects to parse")) }

            }
            case _ => return Seq(new ParseError("Unknown message format."))
          }
        }
      }

      /*
        Unknown node 
      */
      case _ => Seq(new ParseError("Unknown node."))
    }
  }

  private def errorsAndOdf(odf: Seq[OdfParser.ParseResult]) = odf.groupBy(_.isLeft)

}


