package parsing

import java.sql.Timestamp

import org.specs2.Specification
import spray.json._
import types.OdfTypes.{OdfInfoItem, OdfValue}
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
        |[{"c":"class",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,2.5],
        |       [1380475082000000,3.0]]}]
      """.stripMargin

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class"),
      Vector(
        OdfValue("3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("3.0", timestamp = new Timestamp(1380475082000L)))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

  }

  def threeParams = {
    val testJson =
      """
        |[{"c":"class",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,10000,3.14],
        |       [1380475081500000,20000,2.5],
        |       [1380475082000000,30000,3.0]]}]
      """.stripMargin

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class"),
      Vector(
        OdfValue("10000 3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("20000 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("30000 3.0", timestamp = new Timestamp(1380475082000L)))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

  }

  def fourParams = {
    val testJson =
      """
        |[[{"c":"class",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,48.0,-4.5,3.14],
        |       [1380475081500000,50.0,50.0,2.5],
        |       [1380475082000000,50.0,60.0,3.0]]}]]
      """.stripMargin

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class"),
      Vector(
        OdfValue("48.0:-4.5 3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("50.0:50.0 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("50.0:60.0 3.0", timestamp = new Timestamp(1380475082000L)))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

  }

  def fiveParams = {
    val testJson =
      """
        |[{"c":"class",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,48.0,-4.5,10000000,3.14],
        |       [1380475081500000,50.0,50.0,11000000,2.5],
        |       [1380475082000000,50.0,60.0,12000000,3.0]]}]
      """.stripMargin

    val correctInfoItem = Seq(OdfInfoItem(
      Path("class"),
      Vector(
        OdfValue("48.0:-4.5/10000000 3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("50.0:50.0/11000000 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("50.0:60.0/12000000 3.0", timestamp = new Timestamp(1380475082000L)))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

  }

  def fiveParamsLongerArray = {
    val testJson =
      """
        |[{"c":"class",
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
      Path("class"),
      Vector(
        OdfValue("48.0:-4.5/10000000 3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("50.0:50.0/11000000 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("50.0:60.0/12000000 3.0", timestamp = new Timestamp(1380475082000L)),
        OdfValue("55.0:65.0/13000000 7.5", timestamp = new Timestamp(1380475082500L)),
        OdfValue("60.0:70.0/14000000 4.5", timestamp = new Timestamp(1380475083000L)),
        OdfValue("70.0:80.0/15000000 6.5", timestamp = new Timestamp(1380475083500L)))
    ))

    testJson.parseJson.convertTo[Seq[OdfInfoItem]] should be equalTo correctInfoItem

  }

    def hybridRequest = {
    val testJson =
      """
        |[{"c":"class",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"1",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,48.0,-4.5,2.5],
        |       [1380475082000000,10000000,3.0]]},
        | {"i":"1",
        |  "v":[[1380475083000000,48.1,-4.5,11000000,42.0]]},
        | {"c":"test1",
        |  "l":{"label0":"value0","label1":"value1"},
        |  "a":{"attr0":"value0"},
        |  "i":"2",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,48.0,-4.5,2.5],
        |       [1380475082000000,10000000,3.0]]},
        | {"c":"test2",
        |  "v":[[1380475081000000,3.14],
        |       [1380475081500000,48.0,-4.5,2.5],
        |       [1380475082000000,10000000,3.0]]}]
        |      """.stripMargin

    val correctInfoItem1 = OdfInfoItem(
      Path("class"),
      Vector(
        OdfValue("3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("48.0:-4.5 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("10000000 3.0", timestamp = new Timestamp(1380475082000L)),
        OdfValue("48.1:-4.5/11000000 42.0", timestamp = new Timestamp(1380475083000L)))
    )

    val correctInfoItem2 = OdfInfoItem(
      Path("test1"),
      Vector(
        OdfValue("3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("48.0:-4.5 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("10000000 3.0", timestamp = new Timestamp(1380475082000L)))
    )

    val correctInfoItem3 = OdfInfoItem(
      Path("test2"),
      Vector(
        OdfValue("3.14", timestamp = new Timestamp(1380475081000L)),
        OdfValue("48.0:-4.5 2.5", timestamp = new Timestamp(1380475081500L)),
        OdfValue("10000000 3.0", timestamp = new Timestamp(1380475082000L)))
    )

    val res = testJson.parseJson.convertTo[Seq[OdfInfoItem]]

    res must contain(correctInfoItem1) and
      contain(correctInfoItem2) and
      contain(correctInfoItem3)

  }


}