package parsing

import scala.xml._
import scala.util.Try
import scala.collection.mutable.Map

import java.io.File
import java.io.StringReader
import java.io.StringBufferInputStream
import java.io.IOException

//Schema validation
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator

import org.xml.sax.SAXException
/** Parsing object for parsing messages with O-MI protocol*/
object OmiParser {
  private val implementedRequest = Seq("read", "write", "cancel", "response")

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
    val schema_err = validateOmiSchema(xml_msg)
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

  /**
   * Private method that is called inside parse method. This method checks which O-MI message
   *  type the message contains and handles them.
   *
   *  @param node scala.xml.Node that should contain the message to be parsed e.g. read or write messages
   *  @param ttl of the omiEnvalope as string. ttl is in seconds.
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

        // EDIT: Checking msgformat
        if(parameters("msgformat").right.get != "odf")
          return Seq(new ParseError("Unknown message format."))
          
        val odf = parseODF(subnodes("Objects").right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        if (left.isEmpty && !right.isEmpty) {
          Seq(Write(ttl,
            right.map(_.right.get),
            parameters("callback").right.get,
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
          "requestId" -> getChild(node, "requestId", true, true))

        if (subnodes("msg").isRight)
          subnodes += "Objects" -> getChild(subnodes("msg").right.get.head, "Objects")

        val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
        
        if (errors.nonEmpty)
          return errors.toSeq

        // EDIT: Checking msgformat
        if(parameters("msgformat").right.get != "odf")
          return Seq(new ParseError("Unknown message format."))
          
        val odf = parseODF(subnodes("Objects").right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        if (left.isEmpty && !right.isEmpty) {
          if (parameters("interval").right.get.isEmpty) {
            Seq(OneTimeRead(ttl,
              right.map(_.right.get),
              parameters("begin").right.get,
              parameters("end").right.get,
              parameters("newest").right.get,
              parameters("oldest").right.get,
              parameters("callback").right.get,
              subnodes("requestId").right.get.map {
                id => id.text
              }))
          } else {
            Seq(Subscription(ttl,
              parameters("interval").right.get,
              right.map(_.right.get),
              parameters("begin").right.get,
              parameters("end").right.get,
              parameters("newest").right.get,
              parameters("oldest").right.get,
              parameters("callback").right.get,
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
        val results = getChild(node, "result", false, true)
        if (results.isLeft)
          return Seq(results.left.get)

        var parseMsgs: Seq[ParseMsg] = Seq.empty
        for (result <- results.right.get)
          parseMsgs ++= parseNode(result, ttl)

        parseMsgs
      }

      case "result" => {
        val parameters = Map(
          "msgformat" -> getParameter(node, "msgformat"),
          "callback" -> getParameter(node, "callback", true))
        val subnodes = Map(
          "return" -> getChild(node, "return"),
          "msg" -> getChild(node, "msg"),
          "requestId" -> getChild(node, "requestId", true, true))

        if (subnodes("msg").isRight)
          subnodes += "Objects" -> getChild(subnodes("msg").right.get.head, "Objects", true)

        if (subnodes("return").isRight)
          parameters += "returnCode" -> getParameter(subnodes("return").right.get.head, "returnCode", true)

        val errors = parameters.filter(_._2.isLeft).map(_._2.left.get) ++ subnodes.filter(_._2.isLeft).map(_._2.left.get)
        if (errors.nonEmpty)
          return errors.toSeq

        if (subnodes("msg").right.get.isEmpty)
          return Seq(Result(subnodes("return").right.get.text,
            parameters("returnCode").right.get,
            None,
            parameters("callback").right.get,
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
              parameters("callback").right.get,
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

  private def errorsAndOdf(odf: Seq[OdfParser.ParseResult]) = odf.groupBy(_.isLeft)

  /**
   * private helper function for getting parameter of an node.
   * Handles error cases.
   * @param node were parameter should be.
   * @param parameter's label
   * @param is nonexisting parameter accepted, is parameter's existent mandatory
   * @param validation function if parameter musth confor some format
   * @return Either ParseError or parameter as String
   */
  private def getParameter(node: Node,
    paramName: String,
    tolerateEmpty: Boolean = false,
    validation: String => Boolean = _ => true): Either[ParseError, String] = {
    val parameter = (node \ s"@$paramName").text
    if (parameter.isEmpty && !tolerateEmpty)
      return Left(ParseError(s"No $paramName parameter found in ${node.label}."))
    else if (validation(parameter))
      return Right(parameter)
    else
      return Left(ParseError(s"Invalid $paramName parameter in ${node.label}."))
  }

  private def getChild(node: Node,
    childName: String,
    tolerateEmpty: Boolean = false,
    tolerateNonexist: Boolean = false): Either[ParseError, Seq[Node]] = {
    val childs = (node \ s"$childName") map (stripNamespaces)
    if (!tolerateNonexist && childs.isEmpty)
      return Left(ParseError(s"No $childName child found in ${node.label}."))
    else if (!tolerateEmpty && childs.nonEmpty && childs.head.text.isEmpty )
      return Left(ParseError(s"$childName's value not found in ${node.label}."))
    else
      return Right(childs)
  }

  /**
   * private helper function for getting child of an node.
   * Handles error cases.
   * @param node were parameter should be.
   * @param child's label
   * @param is child allowed to have empty value
   * @param is nonexisting childs accepted, is child's existent mandatory
   * @param is multiple childs accepted
   * @return Either ParseError or sequence of childs found
   */
  private def getChilds(node: Node,
    childName: String,
    tolerateEmpty: Boolean = false,
    tolerateNonexist: Boolean = false,
    tolerateMultiple: Boolean = false): Either[ParseError, Seq[Node]] = {
    val childs = (node \ s"$childName")

    if (!tolerateNonexist && childs.isEmpty)
      return Left(ParseError(s"No $childName child found in ${node.label}."))
    else if (!tolerateMultiple && childs.size > 1)
      return Left(ParseError(s"Multiple $childName childs found in ${node.label}."))
    else if (!tolerateEmpty && childs.nonEmpty && childs.contains{ n: Node => n.text.isEmpty }  )
      return Left(ParseError(s"$childName's value not found in ${node.label}."))
    else
      return Right(childs)
  }

  /**
   * function for checking does given string confort O-MI schema
   * @param String to check
   * @return ParseErrors found while checking, if empty, successful
   */
  def validateOmiSchema(xml: String): Seq[ParseError] = {
    try {
      val xsdPath = "./src/main/resources/omi.xsd"
      val factory : SchemaFactory =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema: Schema = factory.newSchema(new File(xsdPath))
      val validator: Validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(xml)))
    } catch {
      case e: IOException =>
        //TODO: log these instead of println
        //        println(e.getMessage())
        return Seq(ParseError("Invalid XML, IO failure"))
      case e: SAXException =>
        //        println(e.getMessage())
        return Seq(ParseError("Invalid XML, schema failure: " + e.getMessage))
      case e: SAXParseException =>
        //        println(e.getMessage())
        return Seq(ParseError("Invalid XML, schema failure"))
      case e: Exception =>
        return Seq(ParseError("Unknown exception: " + e.getMessage))
    }
    return Seq.empty;
  }
  
  /**
   * Temp function for fixing tests
   */
  def stripNamespaces(node : Node) : Node = {
     node match {
         case e : Elem => 
             e.copy(scope = TopScope, child = e.child map (stripNamespaces))
         case _ => node;
     }
 }
}


