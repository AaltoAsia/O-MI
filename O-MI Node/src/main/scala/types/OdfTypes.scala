package types
package OdfTypes

import parsing.xmlGen._
import parsing.xmlGen.xmlTypes._
import scala.xml.XML
import java.sql.Timestamp
import java.lang.{Iterable => JavaIterable}
import scala.collection.JavaConversions.{asJavaIterable, iterableAsScalaIterable, seqAsJavaList}

import scala.language.existentials



/** Object containing internal types used to represent O-DF formatted data
  *
  **/
object `package` {
  trait OdfElement

  case class OdfDescription(
    value:                String,
    lang:                 Option[String] = None
  ) extends OdfElement {
    implicit def asDescription = Description( value, lang, Map.empty)
  }
  type  OdfParseResult = Either[JavaIterable[ParseError], OdfObjects]
  def getObjects( odf: OdfParseResult ) : JavaIterable[OdfObject] = 
    odf match{
      case Right(objs: OdfObjects) => objs.objects
      case _ => Iterable()
    }
  def getErrors( odf: OdfParseResult ) : JavaIterable[ParseError] = 
    odf match {
      case Left(pes: JavaIterable[ParseError]) => pes
      case _ => Iterable()
    }

  def getLeafs(objects: OdfObjects ) : JavaIterable[HasPath] = {
    def getLeafs(obj: OdfObject ) : JavaIterable[HasPath] = {
      if(obj.infoItems.isEmpty && obj.objects.isEmpty)
        Iterable(obj)
      else 
        obj.infoItems ++ obj.objects.flatMap{          
          subobj =>
            getLeafs(subobj)
        } 
    }
    if (objects.objects.nonEmpty)
      objects.objects.flatMap{
        obj => getLeafs(obj)
      }
    else Iterable(objects)
  }
  trait HasPath {
    def path: Path
    def description: Option[OdfDescription]
  }
  def getHasPaths(hasPaths : HasPath *) : Seq[HasPath] ={
    hasPaths.flatMap{ 
      case info : OdfInfoItem => Seq(info)
      case obj : OdfObject => Seq( obj ) ++ getHasPaths( (obj.objects.toSeq ++ obj.infoItems.toSeq ): _* )
      case objs : OdfObjects => Seq( objs ) ++ getHasPaths(objs.objects.toSeq: _*)
    }.toSeq
  }
  
  /**
   * Generates odf tree containing the ancestors of given object.
   */
  @annotation.tailrec
  def fromPath( last: HasPath) : OdfObjects = {

    val parentPath = last.path.dropRight(1)

    last match {
      case info: OdfInfoItem =>
        val parent = OdfObject( parentPath, Iterable(info), Iterable() )  
        fromPath(parent)

      case obj: OdfObject =>
        if (parentPath.length == 1)
          OdfObjects( Iterable(obj) )
        else {
          val parent = OdfObject( parentPath, Iterable(), Iterable(obj) )  
          fromPath(parent)
        }

      case objs: OdfObjects =>
        objs
    }
  }
  def getparent( child: HasPath) : HasPath = {
    val parentPath = child.path.dropRight(1)
    child match {
      case info: OdfInfoItem =>
        val parent = OdfObject( parentPath, Iterable(info), Iterable() )  
        parent
      case obj: OdfObject =>
        if(parentPath.length == 1)
          OdfObjects( Iterable(obj) )
        else {
          val parent = OdfObject( parentPath, Iterable(), Iterable(obj) )  
          parent
        }

      case objs: OdfObjects =>
        objs
    }
  }
}
