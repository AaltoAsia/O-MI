package parsing

import scala.xml._
import scala.util.Try

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
    val schema_err = validateOmiSchema(xml_msg)
    if( schema_err.nonEmpty )
      return schema_err

    val root = Try(XML.loadString(xml_msg)).getOrElse(return Seq(new ParseError("Invalid XML")))
    parse(root)
  }

  /** Parse the given XML string into sequence of ParseMsg classes
   * 
   * @param xml_msg O-MI formatted message that is to be parsed
   * @return sequence of ParseMsg classes, different message types are defined in
   *         the TypeClasses.scala file
   */
  def parse(xml_msg: NodeSeq): Seq[ParseMsg] = {


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
        val msgformat = getParameter(node,"msgformat")
        if( msgformat.isLeft )
          return Seq( msgformat.left.get)

        val msg = getChild(node, "msg")
        if( msg.isLeft )
          return Seq( msg.left.get)

        val objs = getChild(msg.right.get.head, "Objects")
        if( objs.isLeft )
          return Seq( objs.left.get)
        
        val requestIds = getChild( node, "requestId", true, true)
        if( requestIds.isLeft )
          return Seq( requestIds.left.get)

        val callback = getParameter( node, "callback", true)
        if( callback.isLeft )
          return Seq( callback.left.get)

        val odf = parseODF(objs.right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        if (left.isEmpty && !right.isEmpty) {
          Seq( Write( ttl,
                      right.map(_.right.get),
                      callback.right.get, 
                      requestIds.right.get.map{
                        id => id.text
                      }
                    ) )
        } else if (!left.isEmpty) {
          left.map(_.left.get)
        } else { Seq(ParseError("No Objects to parse")) }
      }

      /*
        Read request 
      */
      case "read" => {
        val msgformat = getParameter(node,"msgformat")
        if( msgformat.isLeft )
          return Seq( msgformat.left.get)

        val msg = getChild(node, "msg")
        if( msg.isLeft )
          return Seq( msg.left.get)

        val objs = getChild(msg.right.get.head, "Objects")
        if( objs.isLeft )
          return Seq( objs.left.get)

        val interval = getParameter( node, "interval", true)
        if( interval.isLeft )
          return Seq( interval.left.get)

        val begin = getParameter( node, "begin", true)
        if( begin.isLeft )
          return Seq( begin.left.get)

        val end = getParameter( node, "end", true)
        if( end.isLeft )
          return Seq( end.left.get)

        val newest = getParameter( node, "newest", true)
        if( newest.isLeft )
          return Seq( newest.left.get)

        val oldest = getParameter( node, "oldest", true)
        if( oldest.isLeft )
          return Seq( oldest.left.get)

        val requestIds = getChild( node, "requestId", true, true)
        if( requestIds.isLeft )
          return Seq( requestIds.left.get)

        val callback = getParameter( node, "callback", true)
        if( callback.isLeft )
          return Seq( callback.left.get)

        val odf = parseODF(objs.right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        if (left.isEmpty && !right.isEmpty) {
          if (interval.right.get.isEmpty) {
            Seq( OneTimeRead( ttl,
                              right.map( _.right.get),
                              begin.right.get,
                              end.right.get,
                              newest.right.get,
                              oldest.right.get,
                              callback.right.get, 
                              requestIds.right.get.map{
                                id => id.text
                              }
                            ) )
          } else {
            Seq( Subscription(  ttl,
                                interval.right.get,
                                right.map( _.right.get),
                                begin.right.get,
                                end.right.get,
                                newest.right.get,
                                oldest.right.get,
                                callback.right.get, 
                                requestIds.right.get.map{
                                  id => id.text
                                }
                              ) )
          }
        } else if (!left.isEmpty) {
          left.map(_.left.get)
        } else { Seq( ParseError( "No Objects to parse" ) ) }
          
      }

      /*
        Cancel request 
      */
      case "cancel" => {
        val requestIds = getChild( node, "requestId", false, true)
        if( requestIds.isLeft )
          return Seq( requestIds.left.get)
        
        
        return Seq( Cancel( ttl, 
          requestIds.right.get.map{
            id => id.text
          }
        ) )
      }

      /*
        Response 
      */
      case "response" => {
        val results = getChild(node, "result", false, true)
        if( results.isLeft )
          return Seq( results.left.get)
        var parseMsgs : Seq[ ParseMsg] = Seq.empty
        for( result <- results.right.get )
          parseMsgs ++= parseNode(result, ttl)

        parseMsgs
      }

      case "result" => {
        val msgformat = getParameter(node,"msgformat", true)
        if( msgformat.isLeft )
          return Seq( msgformat.left.get)

        val msg = getChild(node, "msg", true)
        if( msg.isLeft )
          return Seq( msg.left.get)
        
        val returnValue = getChild(node, "return")
        if( returnValue.isLeft )
          return Seq( returnValue.left.get)

        val returnCode = getParameter(returnValue.right.get.head, "returnCode", true)
        if( returnCode.isLeft )
          return Seq( returnCode.left.get)

        val requestIds = getChild( node, "requestId", true, true)
        if( requestIds.isLeft )
          return Seq( requestIds.left.get)

        val callback = getParameter( node, "callback", true)
        if( callback.isLeft )
          return Seq( callback.left.get)

        if( msg.right.get.isEmpty )
          return Seq( Result( returnValue.right.get.text,
                              returnCode.right.get,
                              None,
                              callback.right.get,
                              requestIds.right.get.map{
                                id => id.text
                              }
                            ) )
          
        val objs = getChild(msg.right.get.head, "Objects")
        if( objs.isLeft )
          return Seq( objs.left.get)

        val odf = parseODF(objs.right.get.head)
        val left = odf.filter(_.isLeft)
        val right = odf.filter(_.isRight)

        if (left.isEmpty && !right.isEmpty) {
          return Seq( Result( returnValue.right.get.text,
                              returnCode.right.get,
                              Some( right.map( _.right.get) ),
                              callback.right.get,
                              requestIds.right.get.map{
                                id => id.text
                              }
                            ) )
        } else if (!left.isEmpty) {
          left.map(_.left.get)
        } else { Seq(ParseError("No Objects to parse")) }
      }

      /*
        Unknown node 
      */
      case _ => Seq(new ParseError("Unknown node."))
    }
  }

  private def errorsAndOdf(odf: Seq[OdfParser.ParseResult]) = odf.groupBy(_.isLeft)

  private def getParameter( node: Node, 
                    paramName: String,
                    tolerateEmpty: Boolean = false, 
                    validation: String => Boolean = _ => true) 
                  : Either[ ParseError, String ] = {
    val parameter = ( node \ "@$paramName" ).text
    if( parameter.isEmpty && !tolerateEmpty )
      return Left( ParseError( "No $paramName parameter found in " + node.label ) )
    else if( validation( parameter ) )
      return Right( parameter )
    else
      return Left( ParseError( "Invalid $paramName parameter" ) )
  }

  private def getChild( node: Node, 
                    childName: String,
                    tolerateEmpty: Boolean = false, 
                    tolerateMultiple: Boolean = false) 
                  : Either[ ParseError, Seq[ Node] ] = {
    val childs = ( node \ "$childName" )
    if(!tolerateEmpty && childs.isEmpty )
      return Left( ParseError( "No $childName child found in " + node.label ) )
    else if( !tolerateMultiple && childs.size > 1  )
      return Left( ParseError( "Multiple $childName childs found in " + node.label ) )
    else
      return Right( childs )
  }

  def validateOmiSchema( xml: String) : Seq[ ParseError] = {
    try {
      val xsdPath = "./src/main/resources/omischema.xsd"
      val factory : SchemaFactory =
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val schema: Schema = factory.newSchema(new File(xsdPath))
      val validator: Validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(xml)))
    } catch { 
      case e: IOException => 
        //TODO: log these instead of println
        println(e.getMessage()) 
        Seq( ParseError("Invalid XML, schema failure") )
      case e: SAXException =>
        println(e.getMessage()) 
        Seq( ParseError("Invalid XML, schema failure") )
    }
    return Seq.empty;
   }
}


