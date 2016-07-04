package parsing

import java.sql.Timestamp

import org.specs2.Specification
import spray.json._
import types.OdfTypes._
import types.Path

import database.Warp10JsonProtocol._

class Warp10JsonParsingTest extends Specification {


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

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map.empty),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map.empty),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map.empty))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

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

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map("elev"-> "10000")),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("elev"-> "20000")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("elev"-> "30000")))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

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

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map("lat"-> "48.0", "lon"->"-4.5")),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("lat"-> "50.0", "lon"->"50.0")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("lat"-> "50.0", "lon"->"60.0")))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

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

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map("elev" -> "10000000", "lat"-> "48.0", "lon"->"-4.5")),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("elev"-> "11000000", "lat"-> "50.0", "lon"->"50.0")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("elev"-> "12000000", "lat"-> "50.0", "lon"->"60.0")))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

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

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map("elev" -> "10000000", "lat"-> "48.0", "lon"->"-4.5")),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("elev"-> "11000000", "lat"-> "50.0", "lon"->"50.0")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("elev"-> "12000000", "lat"-> "50.0", "lon"->"60.0")),
        OdfDoubleValue(7.5, new Timestamp(1380475082500L), Map("elev"-> "13000000", "lat"-> "55.0", "lon"->"65.0")),
        OdfDoubleValue(4.5, new Timestamp(1380475083000L), Map("elev"-> "14000000", "lat"-> "60.0", "lon"->"70.0")),
        OdfDoubleValue(6.5, new Timestamp(1380475083500L), Map("elev"-> "15000000", "lat"-> "70.0", "lon"->"80.0")))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

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

    val correctInfoItem1 = OdfInfoItem(
      Path("class/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map.empty),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("lat" -> "48.0", "lon" -> "-4.5")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("elev" -> "10000000")),
        OdfLongValue(42L, new Timestamp(1380475083000L), Map("lat" -> "48.1", "lon" -> "-4.5", "elev" -> "11000000")))
    )

    val correctInfoItem2 = OdfInfoItem(
      Path("test1/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map.empty),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("lat" -> "48.0", "lon" -> "-4.5")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("elev" -> "10000000")))
    )

    val correctInfoItem3 = OdfInfoItem(
      Path("test2/test/test2"),
      Vector(
        OdfStringPresentedValue("3.14", new Timestamp(1380475081000L), attributes = Map.empty),
        OdfDoubleValue(2.5, new Timestamp(1380475081500L), Map("lat" -> "48.0", "lon" -> "-4.5")),
        OdfLongValue(3L, new Timestamp(1380475082000L), Map("elev" -> "10000000")))
      )

    val res = testJson.parseJson.convertTo[Seq[OdfInfoItem]]

    res must contain(correctInfoItem1) and
      contain(correctInfoItem2) and
      contain(correctInfoItem3)

  }


}