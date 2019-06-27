package types
package odf

import types.OdfTypes._

import scala.collection.immutable.HashMap

@deprecated("Should refactor all OdfTypes usages to use new ODF types.", "O-MI-Node 0.11.0")
object NewTypeConverter {
  def convertODF[M <: scala.collection.Map[Path, Node], S <: scala.collection.SortedSet[Path]](
                                                                                                o_df: ODF
                                                                                              ): OdfObjects = {
    val firstLevelObjects = o_df.getChilds(new Path("Objects"))
    val odfObjects = firstLevelObjects.collect {
      case obj: Object =>
        createOdfObject(obj, o_df)
    }
    o_df.nodes.get(new Path("Objects")).collect {
      case objs: Objects =>
        convertObjects(objs, odfObjects)
    }.getOrElse {
      OdfObjects()
    }
  }

  def createOdfObject[M <: scala.collection.Map[Path, Node], S <: scala.collection.SortedSet[Path]]
  (obj: Object, o_df: ODF): OdfObject = {
    val (objects, infoItems) = o_df.getChilds(obj.path).partition {
      case obj: Object => true
      case ii: InfoItem => false
      case objs: Objects => throw new Exception("Invalid type encountered while converting types")
    }
    convertObject(
      obj,
      infoItems.collect {
        case ii: InfoItem => convertInfoItem(ii)
      },
      objects.collect {
        case obj: Object =>
          createOdfObject(obj, o_df)
      }
    )
  }

  def convertObjects(
                      objs: Objects,
                      objects: Iterable[OdfObject]
                    ): OdfObjects = {
    OdfObjects(
      objects.toVector,
      objs.version
    )
  }

  def convertObject(obj: Object,
                    infoItems: Iterable[OdfInfoItem] = Vector.empty,
                    objects: Iterable[OdfObject] = Vector.empty
                   ): OdfObject = {
    var ids = obj.ids.map(convertQlmID(_))
    if (!ids.map(_.value).contains(obj.path.last)) {
      ids = ids ++ Vector(OdfQlmID(obj.path.last))
    }
    OdfObject(
      ids,
      Path(obj.path.toSeq),
      infoItems.toVector,
      objects.toVector,
      obj.descriptions.map(convertDescription).headOption,
      obj.typeAttribute,
      obj.attributes
    )
  }

  def convertQlmID(id: QlmID): OdfQlmID = {
    OdfQlmID(
      id.id,
      id.idType,
      id.tagType,
      id.startDate,
      id.endDate,
      HashMap(id.attributes.toSeq: _*)
    )
  }

  def convertDescription(des: Description): OdfDescription = {
    OdfDescription(des.text, des.language)
  }

  def convertInfoItem(ii: InfoItem): OdfInfoItem = {
    OdfInfoItem(
      Path(ii.path.toSeq),
      ii.values.map(convertValue),
      ii.descriptions.map(convertDescription).headOption,
      ii.metaData.map(convertMetaData),
      ii.typeAttribute,
      ii.attributes
    )
  }

  def convertValue(value: Value[Any]): OdfValue[Any] = {
    value.value match {
      case o: ODF => //[scala.collection.Map[Path,Node],scala.collection.SortedSet[Path] ] =>
        OdfValue(convertODF(o), value.timestamp)
      case str: String =>
        OdfValue(str, value.typeAttribute, value.timestamp)
      case o: Any =>
        OdfValue(o, value.timestamp)
    }
  }

  def convertMetaData(md: MetaData): OdfMetaData = {
    OdfMetaData(md.infoItems.map(ii => convertInfoItem(ii)))
  }
}
