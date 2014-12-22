package parsing

import sensorDataStructure._
import scala.xml._
object OmiParser extends Parser {
  private val implementedRequest = Seq("read", "write", "cancel", "response")
  private def parseODF(msg: Node) = {
    OdfParser.parse(new PrettyPrinter(80, 2).format(msg))
  }
  def parse(xml_msg: String): Seq[ParseMsg] = {
    //TODO: try-catch for invalid xml
    val root = XML.loadString(xml_msg)

    if (root.prefix != "omi")
      return Seq(new ParseError("Incorrect prefix"))
    if (root.label != "Envelope")
      return Seq(new ParseError("XML's root isn't omi:Envelope"))

    val request = root.child.collect {
      case el: Elem => el
    }.headOption.getOrElse(
      return Seq(new ParseError("omi:Envelope doesn't contain request")))

    val ttl = (root \ "@ttl").text
    if (ttl.isEmpty())
      return Seq(new ParseError("No ttl present in O-MI Envelope"))

    parseNode(request, ttl)

  }

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
            } else { Seq(ParseError("No odf or errors found ln 46")) }
          }
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
            } else { Seq(ParseError("No odf or errors found ln 78")) }
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
              } else { Seq(ParseError("No odf or errors found ln 123")) }

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

abstract sealed trait ParseMsg
case class ParseError(msg: String) extends ParseMsg
case class OneTimeRead(ttl: String, sensors: Seq[OdfParser.ODFNode]) extends ParseMsg
case class Write(ttl: String, sensors: Seq[OdfParser.ODFNode]) extends ParseMsg
case class Subscription(ttl: String, interval: String, sensors: Seq[OdfParser.ODFNode]) extends ParseMsg
case class Result(value: String, parseMsgOp: Option[Seq[OdfParser.ODFNode]]) extends ParseMsg
