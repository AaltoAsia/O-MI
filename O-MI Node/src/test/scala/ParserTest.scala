package specs2

import org.specs2._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import scala.io.Source
import parsing._
import parsing.OdfParser._

class ParserTest extends Specification {
lazy val omi_read_test_file = Source.fromFile("src/test/scala/omi_read_test.xml").getLines.mkString("\n")
lazy val omi_write_test_file = Source.fromFile("src/test/scala/omi_write_test.xml").getLines.mkString("\n")
lazy val omi_response_test_file = Source.fromFile("src/test/scala/omi_response_test.xml").getLines.mkString("\n")


def is =  "This is Specification to check the O-MI Parser "           ^ 
                                                                      p^
          "Parser should give certain result for"                     ^
          "Write request parse should return same that in test file"  ! e1^
          "Responseparse should return same that in test file"        ! e2^
          "Read request parse should return same that in test file"   ! e3^
                                                                        end

   def e1 = OmiParser.parse(omi_write_test_file) == List(
      Write("10",List(
        ODFNode("/Objects/SmartHouse/SmartFridge/PowerConsumption",Infoitem(),Some("56"),Some("dateTime=\"2014-12-186T15:34:52\""),None), 
        ODFNode("/Objects/SmartHouse/SmartOven/PowerOn",Infoitem(),Some("1"),Some("dateTime=\"2014-12-186T15:34:52\""),None), 
        ODFNode("/Objects/SmartHouse/PowerConsumption",Infoitem(),Some("180"),Some("dateTime=\"2014-12-186T15:34:52\""),None),
        ODFNode("/Objects/SmartHouse/Moisture",Infoitem(),Some("0.20"),Some("dateTime=\"2014-12-186T15:34:52\""),None),
        ODFNode("/Objects/SmartCar/Fuel",Infoitem(),Some("30"),Some("dateTime=\"2014-12-186T15:34:52\""),None),
        ODFNode("/Objects/SmartCottage/Heater",Object(),None,None,None),
        ODFNode("/Objects/SmartCottage/Sauna",Object(),None,None,None),
        ODFNode("/Objects/SmartCottage/Weather",Object(),None,None,None)
      ))
  )
   

    def e2 = OmiParser.parse(omi_response_test_file) == List(
  Result("",Some(List(
    ODFNode("/Objects/SmartHouse/SmartFridge/PowerConsumption",Infoitem(),Some("56"),Some("dateTime=\"2014-12-186T15:34:52\""),None), 
    ODFNode("/Objects/SmartHouse/SmartOven/PowerOn",Infoitem(),Some("1"),Some("dateTime=\"2014-12-186T15:34:52\""),None),
    ODFNode("/Objects/SmartHouse/PowerConsumption",Infoitem(),Some("180"),Some("dateTime=\"2014-12-186T15:34:52\""),None), 
    ODFNode("/Objects/SmartHouse/Moisture",Infoitem(),Some("0.20"),Some("dateTime=\"2014-12-186T15:34:52\""),None),
    ODFNode("/Objects/SmartCar/Fuel",Infoitem(),Some("30"),Some("dateTime=\"2014-12-186T15:34:52\""),None),
    ODFNode("/Objects/SmartCottage/Heater",Object(),None,None,None),
    ODFNode("/Objects/SmartCottage/Sauna",Object(),None,None,None),
    ODFNode("/Objects/SmartCottage/Weather",Object(),None,None,None)
  )))
)

    def e3 = OmiParser.parse(omi_read_test_file) == List(
      OneTimeRead("10",List( 
        ODFNode("/Objects/SmartHouse/SmartFridge/PowerConsumption",Infoitem(),None,None,None),
        ODFNode("/Objects/SmartHouse/SmartOven/PowerConsumption",Infoitem(),None,None,None),
        ODFNode("/Objects/SmartHouse/PowerConsumption",Infoitem(),None,None,None),
        ODFNode("/Objects/SmartHouse/Moisture",Infoitem(),None,None,None),
        ODFNode("/Objects/SmartCar/Fuel",Infoitem(),None,None,None),
        ODFNode("/Objects/SmartCottage",Object(),None,None,None)
      ))
    )
      
}



