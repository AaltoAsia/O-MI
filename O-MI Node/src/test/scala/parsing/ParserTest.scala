package parsing

import org.specs2._
import scala.io.Source
import parsing._
import parsing.OdfParser._

/*
 * Test class for testing parsing parsing package
 * tests e1   - e99 are for testing OmiParser general methods
 * tests e100 - e199 are for testing write request
 * tests e200 - e299 are for testing response messages
 * tests e300 - e399 are for testing read requests
 * tests e400 - e499 are for testing OdfParser class
 */
class ParserTest extends Specification {
  lazy val omi_read_test_file = Source.fromFile("src/test/scala/parsing/omi_read_test.xml").getLines.mkString("\n")
  lazy val omi_write_test_file = Source.fromFile("src/test/scala/parsing/omi_write_test.xml").getLines.mkString("\n")
  lazy val omi_response_test_file = Source.fromFile("src/test/scala/parsing/omi_response_test.xml").getLines.mkString("\n")

  def is = s2"""
  This is Specification to check the parsing functionality.

  OmiParser should give certain result for
    message with
      incorrect XML       $e1
      incorrect prefix    $e2
      incorrect label     $e3
      missing request     $e4
      missing ttl         $e5
      unknown omi message $e6
    write request with
      correct message     $e100
      missing msgformat   $e101
      wrong msgformat     $e102
      missing omi:msg     $e103
      missing Objects     $e104 
      no objects to parse $e105
    response message with
      correct message     e200
      missing msgformat   $e201
      wrong msgformat     $e202
      missing Objects     $e204
      missing result node $e205
      no objects to parse $e206
    read request with
      correct message     e300
      missing msgformat   $e301
      wrong msgformat     $e302
      missing omi:msg     $e303
      missing Objects     $e304
      no objects to parse $e305
  OdfParser should give certain result for
    message with
      incorrect XML       $e401
      incorrect label     $e402
      missing Object id   $e403
      nameless infoitem   $e404

      
    """

  def e1 = {
    OmiParser.parse("incorrect xml") match {
      case ParseError("Invalid XML") :: _ => true
      case _ => false
    }
  }

  /*
   * case ParseError("Incorrect prefix :: _ ) matches to list that has that parse error in the head position    
   */
  def e2 = {
    OmiParser.parse(omi_read_test_file.replace("omi:omiEnvelope", "pmi:omiEnvelope")) match {
      case ParseError("Incorrect prefix") :: _ => true
      case _ => false
    }
  }

  def e3 = {
    OmiParser.parse(omi_read_test_file.replace("omi:omiEnvelope", "omi:Envelope")) match {
      case ParseError("XML's root isn't omi:omiEnvelope") :: _ => true
      case _ => false
    }
  }

  def e4 = {
    OmiParser.parse(
      """<omi:omiEnvelope ttl="10" version="1.0" xsi:schemaLocation="omi.xsd omi.xsd" xmlns:omi="omi.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
         </omi:omiEnvelope>
      """) match {
        case ParseError("omi:omiEnvelope doesn't contain request") :: _ => true
        case _ => false
      }
  }

  def e5 = {
    OmiParser.parse(omi_read_test_file.replace("""ttl="10"""", """ttl=""""")) match {
      case ParseError("No ttl present in O-MI Envelope") :: _ => true
      case _ => false
    }
  }

  def e6 = {
    OmiParser.parse(omi_response_test_file.replace("omi:response", "omi:respnse")) match {
      case ParseError("Unknown node.") :: _ => true
      case _ => false
    }
  }

  def e100 = {
    println("_________________________________________")
    println(OmiParser.parse(omi_write_test_file))
    OmiParser.parse(omi_write_test_file) == List(
      Write("10", List(
        OdfObject(
          List("Objects", "SmartHouse"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartFridge"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartFridge",
                    "PowerConsumption"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "56")),
                  "")),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartHouse",
                "SmartOven"),
              List(),
              List(
                OdfInfoItem(
                  List(
                    "Objects",
                    "SmartHouse",
                    "SmartOven",
                    "PowerOn"),
                  List(
                    TimedValue("2014-12-186T15:34:52", "1")),
                  "")),
              "")),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "PowerConsumption"),
              List(
                TimedValue("2014-12-186T15:34:52", "180")),
              ""),
            OdfInfoItem(
              List(
                "Objects",
                "SmartHouse",
                "Moisture"),
              List(
                TimedValue("2014-12-186T15:34:52", "0.20")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCar"),
          List(),
          List(
            OdfInfoItem(
              List(
                "Objects",
                "SmartCar",
                "Fuel"),
              List(
                TimedValue("2014-12-186T15:34:52", "30")),
              "")),
          ""),
        OdfObject(
          List(
            "Objects",
            "SmartCottage"),
          List(
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Heater"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Sauna"),
              List(),
              List(),
              ""),
            OdfObject(
              List(
                "Objects",
                "SmartCottage",
                "Weather"),
              List(),
              List(),
              "")),
          List(),
          "")),
        "",
        List()))
    //      List(
    //      Write("10", List(
    //        OdfObject(Seq("Objects","SmartHouse","SmartFridge","PowerConsumption"), InfoItem, Some("56"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartHouse/SmartOven/PowerOn", InfoItem, Some("1"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartHouse/PowerConsumption", InfoItem, Some("180"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartHouse/Moisture", InfoItem, Some("0.20"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartCar/Fuel", InfoItem, Some("30"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
    //        ODFNode("/Objects/SmartCottage/Heater", NodeObject, None, None, None),
    //        ODFNode("/Objects/SmartCottage/Sauna", NodeObject, None, None, None),
    //        ODFNode("/Objects/SmartCottage/Weather", NodeObject, None, None, None)),
    //        "test",
    //        Seq()))
  }
  def e101 = {
    OmiParser.parse(omi_write_test_file.replace("""omi:write msgformat="odf"""", "omi:write")) match {
      case ParseError("No msgformat in write request") :: _ => true
      case _ => false
    }
  }

  def e102 = {
    OmiParser.parse(omi_write_test_file.replace("""msgformat="odf"""", """msgformat="pdf"""")) match {
      case ParseError("Unknown message format.") :: _ => true
      case _ => false
    }
  }

  def e103 = {
    OmiParser.parse(omi_write_test_file.replace("omi:msg", "omi:msn")) match {
      case ParseError("No message node found in write node.") :: _ => true
      case _ => false
    }
  }

  def e104 = {
    OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:write msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
""") match {
        case ParseError("No Objects node found in msg node.") :: _ => true
        case _ => false
      }
  }

  def e105 = {
    OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:write msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
  </omi:write>
</omi:omiEnvelope>
""") match {
        case ParseError("No Objects to parse") :: _ => true
        case _ => false
      }
  }

  //  def e200 = {
  //    OmiParser.parse(omi_response_test_file) == List(
  //      Result("", Some(List(
  //        ODFNode("/Objects/SmartHouse/SmartFridge/PowerConsumption", InfoItem, Some("56"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
  //        ODFNode("/Objects/SmartHouse/SmartOven/PowerOn", InfoItem, Some("1"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
  //        ODFNode("/Objects/SmartHouse/PowerConsumption", InfoItem, Some("180"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
  //        ODFNode("/Objects/SmartHouse/Moisture", InfoItem, Some("0.20"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
  //        ODFNode("/Objects/SmartCar/Fuel", InfoItem, Some("30"), Some("dateTime=\"2014-12-186T15:34:52\""), None),
  //        ODFNode("/Objects/SmartCottage/Heater", NodeObject, None, None, None),
  //        ODFNode("/Objects/SmartCottage/Sauna", NodeObject, None, None, None),
  //        ODFNode("/Objects/SmartCottage/Weather", NodeObject, None, None, None)))))
  //  }

  def e201 = {
    OmiParser.parse(omi_response_test_file.replace("""omi:result msgformat="odf"""", "omi:result")) match {
      case ParseError("No msgformat in result message") :: _ => true
      case _ => false
    }
  }

  def e202 = {
    OmiParser.parse(omi_response_test_file.replace("""msgformat="odf"""", """msgformat="pdf"""")) match {
      case ParseError("Unknown message format.") :: _ => true
      case _ => false
    }
  }

  //  def e203 = {
  //    OmiParser.parse(omi_response_test_file.replace("omi:msg", "omi:msn")) match {
  //      case ParseError("No message node found in response node.") :: _ => true
  //      case _ => false
  //    }
  //  }

  def e204 = {
    OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:response>
      <omi:result msgformat="odf" > 
      <omi:return></omi:return> 
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
      </omi:result> 
  </omi:response>
</omi:omiEnvelope>
""") match {
        case ParseError("No Objects node found in msg node.") :: _ => true
        case _ => false
      }
  }

  def e205 = {
    OmiParser.parse(omi_response_test_file.replace("<omi:return></omi:return>", "")) match {
      case ParseError("No return node in result node") :: _ => true
      case _ => false
    }
  }

  def e206 = {
    OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:response>
      <omi:result msgformat="odf" > 
      <omi:return></omi:return> 
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
      </omi:result> 
  </omi:response>
</omi:omiEnvelope>
""") match {
        case ParseError("No Objects to parse") :: _ => true
        case _ => false
      }
  }

  //  def e300 = {
  //    OmiParser.parse(omi_read_test_file) == List(
  //      OneTimeRead("10", List(
  //        ODFNode("/Objects/SmartHouse/SmartFridge/PowerConsumption", InfoItem, None, None, None),
  //        ODFNode("/Objects/SmartHouse/SmartOven/PowerConsumption", InfoItem, None, None, None),
  //        ODFNode("/Objects/SmartHouse/PowerConsumption", InfoItem, None, None, None),
  //        ODFNode("/Objects/SmartHouse/Moisture", InfoItem, None, None, None),
  //        ODFNode("/Objects/SmartCar/Fuel", InfoItem, None, None, None),
  //        ODFNode("/Objects/SmartCottage", NodeObject, None, None, None))))
  //  }

  def e301 = {
    OmiParser.parse(omi_read_test_file.replace("""omi:read msgformat="odf"""", "omi:read")) match {
      case ParseError("No msgformat in read request") :: _ => true
      case _ => false
    }
  }

  def e302 = {
    OmiParser.parse(omi_read_test_file.replace("""msgformat="odf"""", """msgformat="pdf"""")) match {
      case ParseError("Unknown message format.") :: _ => true
      case _ => false
    }
  }

  def e303 = {
    OmiParser.parse(omi_read_test_file.replace("omi:msg", "omi:msn")) match {
      case ParseError("No message node found in read node.") :: _ => true
      case _ => false
    }
  }

  def e304 = {
    OmiParser.parse(
      """
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:read msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
      </omi:msg>
  </omi:read>
</omi:omiEnvelope>
""") match {
        case ParseError("No Objects node found in msg node.") :: _ => true
        case _ => false
      }
  }

  def e305 = {
    OmiParser.parse(
      """<?xml version="1.0" encoding="UTF-8"?>
<omi:omiEnvelope xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:omi="omi.xsd" xsi:schemaLocation="omi.xsd omi.xsd" version="1.0" ttl="10">
  <omi:read msgformat="odf" >
      <omi:msg xmlns="odf.xsd" xsi:schemaLocation="odf.xsd odf.xsd">
    <Objects>
    </Objects>
      </omi:msg>
  </omi:read>
</omi:omiEnvelope>
""") match {
        case ParseError("No Objects to parse") :: _ => true
        case _ => false
      }
  }

  def e401 = {
    OdfParser.parse("incorrect xml") match {
      case Left(ParseError("Invalid XML")) :: _ => true
      case _ => false
    }

  }
  def e402 = {
    OdfParser.parse("""<Object>
        <Object>
      <id>SmartHouse</id>
      <InfoItem name="PowerConsumption">
      </InfoItem>
      <InfoItem name="Moisture">
      </InfoItem>
        </Object>
        <Object>
      <id>SmartCar</id>
      <InfoItem name="Fuel">
      </InfoItem>
        </Object>
        <Object>
      <id>SmartCottage</id>
        </Object>
    </Object>
""") match {
      case Left(ParseError("ODF doesn't have Objects as root.")) :: _ => true
      case _ => false
    }
  }
  def e403 = {
    OdfParser.parse("""
    <Objects>
        <Object>
        <id></id>
        </Object>
    </Objects>
""") match {
      case Left(ParseError("No id for Object.")) :: _ => true
      case _ => false
    }
  }
  def e404 = {
    OdfParser.parse("""
    <Objects>
        <Object>
        <id>SmartHouse</id>
        <InfoItem name="">
        </InfoItem>
        </Object>
    </Objects>
""") match {
      case Left(ParseError("No name for InfoItem.")) :: _ => true
      case _ => false

    }
  }
  //  def e405 = false

}



