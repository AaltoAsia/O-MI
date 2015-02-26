package parsing
import parsing.Types._

import java.sql.Timestamp
import scala.xml._
import scala.util.Try
import scala.collection.mutable.Map

import java.io.File
import java.io.StringReader
import java.io.StringBufferInputStream
import java.io.IOException
import java.sql.Timestamp
import java.text.SimpleDateFormat

//Schema validation
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator

import org.xml.sax.SAXException
/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser extends Parser[ParseMsg] {
  private val implementedRequest = Seq("read", "write", "cancel", "response")
  private val dateFormat = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss")

  override def schemaPath : String = "./src/main/resources/omi.xsd"

  /**
   * This method calls the OdfParser class to parse the data when the O-MI message has been parsed.
   *  this method converts the Node message to xml string.
   *
   *  @param msg scala.xml.Node that contains the data in O-DF format
   *  @return sequence of ParseResults, type ParseResult is defined in the OdfParser class.
   */
  private def parseODF(msg: Node) = {
    OdfParser.parse(new PrettyPrinter(80, 2).format(msg))
  }

  /**
   * Parse the given XML string into sequence of ParseMsg classes
   *
   * @param xml_msg O-MI formatted message that is to be parsed
   * @return sequence of ParseMsg classes, different message types are defined in
   *         the TypeClasses.scala file
   */
  def parse(xml_msg: String): Seq[ParseMsg] = {
    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val schema_err = schemaValitation(xml_msg)
    if (schema_err.nonEmpty)
      return schema_err

    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(new ParseError("Invalid XML")))
    
    parse(root)
  }

  /**
   * Parse the given XML string into sequence of ParseMsg classes
   *
   * @param xml_msg O-MI formatted message that is to be parsed
   * @return sequence of ParseMsg classes, different message types are defined in
   *         the TypeClasses.scala file
   */
  private def parse(xml_msg: NodeSeq): Seq[ParseMsg] = {

    /*Convert the string into scala.xml.Elem. If the message contains invalid XML, send correct ParseError*/
    val root = xml_msg.head

    val request = root.child.collect {
      case el: Elem => el
    }.head

    val ttl = (root \ "@ttl").text

    parseNode(request, ttl)
    
  }

  /**
   * Private method that is called inside parse method. This method checks which O-MI message
   *  type the message contains and handles them.
   *
   *  @param node scala.xml.Node that should contain the message to be parsed e.g. read or write messages
   *  @param ttl of the omiEnvelope as string. ttl is in seconds.
   *
   */
  private def parseNode(node: Node, ttl: String): Seq[ParseMsg] = {
    node.label match {
      /*
        Write request 
      */
      case "write" => {
        val parameters = Map(
          "msgformat" -> getParameter(node, "msgformat"),
          "callback" -> getParameter(node, "callback", true))
        val subnodes = Map(
          "msg" -> getChild(node, "msg"),
          "requestId" -> getChild(node, "requestId", true, true))

        if (subnodes("msg").isRight)
          subnodes += "Objects" -> getChild(subnodes("msg").right.get.head, "Objects")

        val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
        if (errors.nonEmpty)
          return errors.toSeq
          
        val odf = parseODF(subnodes("Objects").right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        val callback = parameters("callback").right.get match {
          case "" => None
          case str => Some(str)
        }
        if (left.isEmpty && !right.isEmpty) {
          Seq(Write(ttl,
            right.map(_.right.get),
            callback,
            subnodes("requestId").right.get.map {
              id => id.text
            }))
        } else if (!left.isEmpty) {
          left.map(_.left.get)
        } else { Seq(ParseError("No Objects to parse")) }
      }

      /*
        Read request 
      */
      case "read" => {
        val parameters = Map(
          "msgformat" -> getParameter(node, "msgformat"),
          "interval" -> getParameter(node, "interval", true),
          "begin" -> getParameter(node, "begin", true),
          "end" -> getParameter(node, "end", true),
          "newest" -> getParameter(node, "newest", true),
          "oldest" -> getParameter(node, "oldest", true),
          "callback" -> getParameter(node, "callback", true))
        val subnodes = Map(
          "msg" -> getChild(node, "msg"),
          "requestId" -> getChild(node, "requestID", true, true)
        )

        if (subnodes("msg").isRight){
          subnodes += "Objects" -> getChild(subnodes("msg").right.get.head, "Objects")
        }

        val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
        
        if (errors.nonEmpty)
          return errors.toSeq

        val odf = parseODF(subnodes("Objects").right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        val begin = parameters("begin").right.get match {
          case "" => None
          case str => Some(new Timestamp(dateFormat.parse(str).getTime))
        }
        val end = parameters("end").right.get match {
          case "" => None
          case str => Some(new Timestamp(dateFormat.parse(str).getTime))
        }
        val newest = parameters("newest").right.get match {
          case "" => None
          case str => Some(str.toInt)
        }
        val oldest = parameters("oldest").right.get match {
          case "" => None
          case str => Some(str.toInt)
        }
        val callback = parameters("callback").right.get match {
          case "" => None
          case str => Some(str)
        }

        if (left.isEmpty && !right.isEmpty) {
          if (parameters("interval").right.get.isEmpty) {
            Seq(OneTimeRead(ttl,
              right.map(_.right.get),
              begin,
              end,
              newest,
              oldest,
              callback,
              subnodes("requestId").right.get.map {
                id => id.text
              }))
          } else {
            Seq(Subscription(ttl,
              parameters("interval").right.get,
              right.map(_.right.get),
              begin,
              end,
              newest,
              oldest,
              callback,
              subnodes("requestId").right.get.map {
                id => id.text
              }))
          }
        } else if (!left.isEmpty) {
          left.map(_.left.get)
        } else { Seq(ParseError("No Objects to parse")) }

      }

      /*
        Cancel request 
      */
      case "cancel" => {
        val requestIds = getChild(node, "requestId", false, true)
        if (requestIds.isLeft)
          return Seq(requestIds.left.get)

        return Seq(Cancel(ttl,
          requestIds.right.get.map {
            id => id.text
          }))
      }

      /*
        Response 
      */
      case "response" => {
        val results = getChild(node, "result", true, true)
        if (results.isLeft)
          return Seq(results.left.get)

        var parseMsgs: Seq[ParseMsg] = Seq.empty
        for (result <- results.right.get)
          parseMsgs ++= parseNode(result, ttl)

        parseMsgs
      }

      case "result" => {
        val parameters = Map(
          "msgformat" -> getParameter(node, "msgformat", true)
        )
        
        // NOTE: Result does not have to contain msg
        val subnodes = Map(
          "return" -> getChild(node, "return",tolerateEmpty = true),
          "msg" -> getChild(node, "msg", true, true),
          "requestId" -> getChild(node, "requestId", true, true)
        )

        if (subnodes("msg").isRight && !subnodes("msg").right.get.isEmpty) {
          subnodes += "Objects" -> getChild(subnodes("msg").right.get.head, "Objects", true, true)
        }
        
        if (subnodes("return").isRight) {
          parameters += "returnCode" -> getParameter(subnodes("return").right.get.head, "returnCode", true)
        }
        
        val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
        if (errors.nonEmpty)
          return errors.toSeq

        
        if (!subnodes.contains("msg") || subnodes("msg").right.get.isEmpty)
          return Seq(Result(subnodes("return").right.get.text,
            parameters("returnCode").right.get,
            None,
            subnodes("requestId").right.get.map {
              id => id.text
            }))

        if (subnodes("Objects").right.get.nonEmpty) {
          val odf = parseODF(subnodes("Objects").right.get.head)
          val left = odf.filter(_.isLeft)
          val right = odf.filter(_.isRight)

          if (left.isEmpty && !right.isEmpty) {
            return Seq(Result(subnodes("return").right.get.text,
              parameters("returnCode").right.get,
              Some(right.map(_.right.get)),
              subnodes("requestId").right.get.map {
                id => id.text
              }))
          } else if (!left.isEmpty) {
            left.map(_.left.get)
          } else {
            Seq(ParseError("No Objects to parse"))
          }
        } else {
          Seq(ParseError("No Objects node in msg, possible but not implemented"))
        }
      }

      /*
        Unknown node 
      */
      case _ => Seq(new ParseError("Unknown node."))
    }
  }

  
}


