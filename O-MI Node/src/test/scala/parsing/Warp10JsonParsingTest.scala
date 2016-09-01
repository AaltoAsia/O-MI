package parsing

import java.sql.Timestamp

import scala.collection.immutable.HashMap

import org.specs2.Specification
import parsing.xmlGen.xmlTypes.MetaData
import spray.json._
import types.OdfTypes._
import types.Path
import akka.actor.{ActorSystem}
import http.{OmiConfig,OmiConfigExtension}

import database.Warp10JsonProtocol._
import database.SingleStores

class Warp10JsonParsingTest extends Specification {

  implicit val system : ActorSystem = ActorSystem("on-core") 
  implicit val settings : OmiConfigExtension = OmiConfig(system)

  implicit val singleStores = new SingleStores(settings)
  implicit val forwatter : Warp10JsonFormat = new Warp10JsonFormat()(singleStores)

  def is =
    s2"""
   This is a specification to check forrect parsing of incoming JSON messages

    Parsing should give correct result for
      message with single element array containing
       2 parameters $twoParams
       3 parameters $threeParams
       4 parameters $fourParams
       5 parameters $fiveParams
      message with longer array containing
       5 parameters $fiveParamsLongerArray
      message containing
       multiple different classes $hybridRequest
    """

  def twoParams = {
    val testJson =
      """
        |[{"c":"class.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,2.5],
        |       [1380475082000000,3.0]]}]
      """.stripMargin

    val correctInfoItem = Vector(OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty))
    ))

    val result = testJson.parseJson.convertTo[Seq[(OdfObject, OdfObject)]]
    result.head._1.infoItems should be equalTo correctInfoItem

  }

  def threeParams = {
    val testJson =
      """
        |[{"c":"class.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,10000,3.14],
        |       [1380475081500000,20000,2.5],
        |       [1380475082000000,30000,3.0]]}]
      """.stripMargin

    val correctLocations = OdfInfoItem(
      Path("class/test/location"),
      Vector(
        OdfValue("+10000CRSWGS_84/", "xs:String", new Timestamp(1380475081000L)),
        OdfValue("+20000CRSWGS_84/", "xs:String", new Timestamp(1380475081500L)),
        OdfValue("+30000CRSWGS_84/", "xs:String", new Timestamp(1380475082000L))
      )
    )

    val correctValues = OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty))
    )



    val result = testJson.parseJson.convertTo[Seq[(OdfObject, OdfObject)]]
    result.head._1.infoItems must contain(correctValues) and (result.head._2.infoItems must contain(correctLocations))


  }

  def fourParams = {
    val testJson =
      """
        |[[{"c":"class.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,48.0,-4.5,3.14],
        |       [1380475081500000,50.0,50.0,2.5],
        |       [1380475082000000,50.0,60.0,3.0]]}]]
      """.stripMargin


    val correctLocations = OdfInfoItem(
      Path("class/test/location"),
      Vector(
        OdfValue("+48-004.5/", "xs:String", new Timestamp(1380475081000L)),
        OdfValue("+50+050/", "xs:String", new Timestamp(1380475081500L)),
        OdfValue("+50+060/", "xs:String", new Timestamp(1380475082000L)))
    )



    val correctValues = OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty))
    )
    val result = testJson.parseJson.convertTo[Seq[(OdfObject,OdfObject)]]

    result.head._1.infoItems must contain(correctValues) and (result.head._2.infoItems must contain(correctLocations))

  }

  def fiveParams = {
    val testJson =
      """
        |[{"c":"class.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,48.0,-4.5,10000000,3.14],
        |       [1380475081500000,50.0,50.0,11000000,2.5],
        |       [1380475082000000,50.0,60.0,12000000,3.0]]}]
      """.stripMargin


    val correctLocations = OdfInfoItem(
      Path("class/test/location"),
      Vector(
        OdfValue("+48-004.5+10000000CRSWGS_84/", "xs:String", new Timestamp(1380475081000L)),
        OdfValue("+50+050+11000000CRSWGS_84/", "xs:String", new Timestamp(1380475081500L)),
        OdfValue("+50+060+12000000CRSWGS_84/", "xs:String", new Timestamp(1380475082000L)))
    )

    val correctValues = OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty))
    )
    val result = testJson.parseJson.convertTo[Seq[(OdfObject, OdfObject)]]

    result.head._1.infoItems must contain(correctValues) and (result.head._2.infoItems must contain(correctLocations))
  }

  def fiveParamsLongerArray = {
    val testJson =
      """
        |[{"c":"class.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,48.0,-4.5,10000000,3.14],
        |       [1380475081500000,50.0,50.0,11000000,2.5],
        |       [1380475082000000,50.0,60.0,12000000,3.0]]},
        | {"i":"1",
        |  "v":[[1380475082500000,55.0,65.0,13000000,7.5],
        |       [1380475083000000,60.0,70.0,14000000,4.5],
        |       [1380475083500000,70.0,80.0,15000000,6.5]]}]
      """.stripMargin

    val correctLocations = OdfInfoItem(
      Path("class/test/location"),
      Vector(
        OdfValue("+48-004.5+10000000CRSWGS_84/", "xs:String", new Timestamp(1380475081000L)),
        OdfValue("+50+050+11000000CRSWGS_84/", "xs:String", new Timestamp(1380475081500L)),
        OdfValue("+50+060+12000000CRSWGS_84/", "xs:String", new Timestamp(1380475082000L)),
        OdfValue("+55+065+13000000CRSWGS_84/", "xs:String", new Timestamp(1380475082500L)),
        OdfValue("+60+070+14000000CRSWGS_84/", "xs:String", new Timestamp(1380475083000L)),
        OdfValue("+70+080+15000000CRSWGS_84/", "xs:String", new Timestamp(1380475083500L)))
    )

    val correctValues = OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty),
        OdfValue(7.5, new Timestamp(1380475082500L), attributes = HashMap.empty),
        OdfValue(4.5, new Timestamp(1380475083000L), attributes = HashMap.empty),
        OdfValue(6.5, new Timestamp(1380475083500L), attributes = HashMap.empty))
    )

    val result = testJson.parseJson.convertTo[Seq[(OdfObject, OdfObject)]]

    result.head._1.infoItems must contain(correctValues) and(result.head._2.infoItems must contain(correctLocations))

  }

    def hybridRequest = {
    val testJson =
      """
        |[{"c":"class.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,48.0,-4.5,2.5],
        |       [1380475082000000,10000000,3.0]]},
        | {"i":"1",
        |  "v":[[1380475083000000,48.1,-4.5,11000000,42.0]]},
        | {"c":"test1.test.test2",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"2",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,48.0,-4.5,2.5],
        |       [1380475082000000,10000000,3.0]]},
        | {"c":"test2.test.test2",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,48.0,-4.5,2.5],
        |       [1380475082000000,10000000,3.0]]}]
        |      """.stripMargin



      val correctLocations1 = OdfInfoItem(
        Path("class/test/location"),
        Vector(
          OdfValue("+48-004.5/", "xs:String", new Timestamp(1380475081500L)),
          OdfValue("+10000000CRSWGS_84/", "xs:String", new Timestamp(1380475082000L)),
          OdfValue("+48.1-004.5+11000000CRSWGS_84/", "xs:String", new Timestamp(1380475083000L))
        )
      )

      val correctLocations2 = OdfInfoItem(
        Path("test1/test/location"),
        Vector(
          OdfValue("+48-004.5/", "xs:String", new Timestamp(1380475081500L)),
          OdfValue("+10000000CRSWGS_84/", "xs:String", new Timestamp(1380475082000L))
        )
      )

      val correctLocations3 = OdfInfoItem(
            Path("test2/test/location"),
            Vector(
              OdfValue("+48-004.5/", "xs:String", new Timestamp(1380475081500L)),
              OdfValue("+10000000CRSWGS_84/", "xs:String", new Timestamp(1380475082000L))
            )
          )

     val correctValues1 = OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty),
        OdfValue(42.0, new Timestamp(1380475083000L), attributes = HashMap.empty))
     )

    val correctValues2 = OdfInfoItem(
      Path("test1/test/test2/"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty))
    )

    val correctValues3 = OdfInfoItem(
      Path("test2/test/test2"),
      Vector(
        OdfValue(3.14, new Timestamp(1380475081000L), attributes = HashMap.empty),
        OdfValue(2.5, new Timestamp(1380475081500L), attributes = HashMap.empty),
        OdfValue(3.0, new Timestamp(1380475082000L), attributes = HashMap.empty))
      )

      val res = testJson.parseJson.convertTo[Seq[(OdfObject,OdfObject)]]
      val obj1 = res.find(_._1.path == Path("class/test/test2")).get
      val obj2 = res.find(_._1.path == Path("test1/test/test2")).get
      val obj3 = res.find(_._1.path == Path("test2/test/test2")).get

      obj1._1.infoItems must contain(correctValues1) and(
        obj1._2.infoItems must contain(correctLocations1)) and(
        obj2._1.infoItems must contain(correctValues2) and (
          obj2._2.infoItems must contain(correctLocations2))) and(
        obj3._1.infoItems must contain(correctValues3) and (
          obj3._2.infoItems must contain(correctLocations3)))

  }

}
