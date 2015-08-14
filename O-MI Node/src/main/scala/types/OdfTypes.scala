package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import scala.xml.XML
import java.sql.Timestamp
import java.lang.{ Iterable => JavaIterable }
import scala.collection.JavaConversions.{ asJavaIterable, iterableAsScalaIterable }
import scala.language.existentials
/**
 * Object containing internal types used to represent O-DF formatted data
 *
 */
object `package` {
  type OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]
  def getObjects(odf: OdfParseResult): JavaIterable[OdfObject] =
    odf match {
      case Right(objs: OdfObjects) => objs.objects
      case _                       => Iterable()
    }
  def getErrors(odf: OdfParseResult): JavaIterable[ParseError] =
    odf match {
      case Left(pes: JavaIterable[ParseError]) => pes
      case _                                   => Iterable()
    }

  def getLeafs(objects: OdfObjects): JavaIterable[OdfNode] = {
    def getLeafs(obj: OdfObject): JavaIterable[OdfNode] = {
      if (obj.infoItems.isEmpty && obj.objects.isEmpty)
        Iterable(obj)
      else
        obj.infoItems ++ obj.objects.flatMap {
          subobj =>
            getLeafs(subobj)
        }
    }
    if (objects.objects.nonEmpty)
      objects.objects.flatMap {
        obj => getLeafs(obj)
      }
    else Iterable(objects)
  }
  def getOdfNodes(hasPaths: OdfNode*): Seq[OdfNode] = {
    hasPaths.flatMap {
      case info: OdfInfoItem => Seq(info)
      case obj: OdfObject    => Seq(obj) ++ getOdfNodes((obj.objects.toSeq ++ obj.infoItems.toSeq): _*)
      case objs: OdfObjects  => Seq(objs) ++ getOdfNodes(objs.objects.toSeq: _*)
    }.toSeq
  }

  def getInfoItems( objects: OdfObjects ) : JavaIterable[OdfInfoItem] = {
    getLeafs(objects).collect{ case info: OdfInfoItem => info}
  }
  /**
   * Generates odf tree containing the ancestors of given object.
   */
  @annotation.tailrec
  def fromPath(last: OdfNode): OdfObjects = {

    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject(parentPath, Iterable(info), Iterable())
        fromPath(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(Iterable(obj))
        else {
          val parent = OdfObject(parentPath, Iterable(), Iterable(obj))
          fromPath(parent)
        }

      case objs: OdfObjects =>
        objs

      case newType => throw new MatchError
    }
  }
  def getParent(child: OdfNode): OdfNode = {
    val parentPath = child.path.dropRight(1)
    child match {
      case info: OdfInfoItem =>
        val parent = OdfObject(parentPath, Iterable(info), Iterable())
        parent
      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects(Iterable(obj))
        else {
          val parent = OdfObject(parentPath, Iterable(), Iterable(obj))
          parent
        }

      case objs: OdfObjects =>
        objs

      case newType => throw new MatchError
    }
  }
}

sealed trait OdfNode {
  def path: Path
  def description: Option[OdfDescription]
  def get(path: Path): Option[OdfNode]
  //def combine( another : OdfNode ) : OdfNode
  //def update( another : OdfNode ) : OdfNode
}

case class OdfObjects(
  objects: JavaIterable[OdfObject] = Iterable(),
  version: Option[String] = None) extends OdfObjectsImpl(objects, version) with OdfNode

case class OdfObject(
  path: Path,
  infoItems: JavaIterable[OdfInfoItem],
  objects: JavaIterable[OdfObject],
  description: Option[OdfDescription] = None,
  typeValue: Option[String] = None) extends OdfObjectImpl(path, infoItems, objects, description, typeValue) with OdfNode

case class OdfInfoItem(
    path: Path,
    values: JavaIterable[OdfValue] = Iterable(),
    description: Option[OdfDescription] = None,
    metaData: Option[OdfMetaData] = None) extends OdfInfoItemImpl(path, values, description, metaData) with OdfNode {
  def get(path: Path): Option[OdfNode] = if (path == this.path) Some(this) else None
}

case class OdfDescription(
    value: String,
    lang: Option[String] = None) {
  implicit def asDescription = Description(value, lang, Map.empty)
}

