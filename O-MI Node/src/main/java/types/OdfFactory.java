package types;

import java.lang.Object;
import java.util.Vector;
import types.OdfTypes.*;
import types.OdfTypes.OdfObjects;
import types.OdfTypes.OdfDescription;
import types.OdfTypes.OdfMetaData;
import types.OdfTypes.OdfObject;
import types.OdfTypes.OdfInfoItem;
import types.OdfTypes.OdfValue;
import types.OdfTypes.OdfValue$;
import types.OdfTypes.OdfTreeCollection;
import types.OdfTypes.OdfQlmID;
import types.OdfTypes.OdfQlmID$;
import java.sql.Timestamp;
import scala.collection.immutable.HashMap;

  /**
   * Factory class for creating O-DF types used in Scala.
   */
public class OdfFactory{

  /**
   *
   * @param value Value inside of O-DF value element.
   * @param typeValue Type of value, one of built in XML Schema data types specifed in
   *  <a href="https://www.w3.org/TR/xmlschema-2/#built-in-datatypes">XML Schema types</a>
   *  Parameter value is cast to type specifed by typeValue parameter. If cast fails, value's
   *  type will be String.
   * @param timestamp Timestamp when value was measured or received.
   * @return OdfValue
   */
  public static OdfValue<Object> createOdfValue(
    String value,
    String typeValue,
    Timestamp timestamp
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return OdfValue$.MODULE$.apply(
        value,
        typeValue,
        timestamp,
        attr
        );
  }


  /**
   *
   * @param value Value inside of O-DF value element.
   * @param timestamp Timestamp when value was measured or received.
   * @return OdfValue
   */
  public static OdfValue<Object> createOdfValue(
    Object value,
    Timestamp timestamp
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return OdfValue$.MODULE$.apply(
        value,
        timestamp
        );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItem.
   * @param description Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param typeValue type parameter of InfoItem.
   * @return OdfInfoItem
   */
  public static OdfInfoItem createOdfInfoItem(
    Path path,
    Iterable<OdfValue<Object>> values,
    OdfDescription description,
    OdfMetaData metaData,
    String typeValue
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.apply(description),
        scala.Option.apply(metaData),
        scala.Option.apply(typeValue),
        attr
        );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItem.
   * @param description Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @return OdfInfoItem
   */
  public static OdfInfoItem createOdfInfoItem(
    Path path,
    Iterable<OdfValue<Object>> values,
    OdfDescription description,
    OdfMetaData metaData
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.apply(description),
        scala.Option.apply(metaData),
        scala.Option.empty(),
        attr
        );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItew.
   * @param description Description of InfoItem.
   * @return OdfInfoItem
   */
  public static OdfInfoItem createOdfInfoItem(
    Path path,
    Iterable<OdfValue<Object>> values,
    OdfDescription description
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.apply(description),
        scala.Option.empty(),//Look at type of MetaData.
        scala.Option.empty(),
        attr
        );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItew.
   * @return OdfInfoItem
   */
  public static OdfInfoItem createOdfInfoItem(
    Path path,
    Iterable<OdfValue<Object>> values
    ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.empty(),
        scala.Option.empty(),//Look at type of MetaData.
        scala.Option.empty(),
        attr
        );
    }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItew.
   * @return OdfInfoItem
   */
  public static OdfInfoItem createOdfInfoItem(
      Path path,
      Iterable<OdfValue<Object>> values,
    OdfMetaData metaData
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.empty(),
        scala.Option.apply(metaData),//Look at type of MetaData.
        scala.Option.empty(),
        attr
        );
  }

  /**
   *
   * @param ids OdfQlmIDs of O-DF Object
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param description Description of O-DF Object.
   * @param typeValue
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Iterable<OdfQlmID> ids,
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    OdfDescription description,
    String typeValue
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(description),
        scala.Option.apply(typeValue),
        attr
    );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param description Description of O-DF Object.
   * @param typeValue
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    OdfDescription description,
    String typeValue
  ){
    Vector<OdfQlmID> ids = new Vector<OdfQlmID>();
    OdfQlmID id = OdfFactory.createOdfQlmID(
        path.toArray()[path.length()-1],
        null,
        null,
        null,
        null
    );
    ids.add(id);
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(description),
        scala.Option.apply(typeValue),
        attr
    );
  }

  /**
   *
   * @param ids OdfQlmIDs of O-DF Object
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param typeValue
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Iterable<OdfQlmID> ids,
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    String typeValue
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        scala.Option.apply(typeValue),
        attr
    );
  }

  /**
   *
   * @param ids OdfQlmIDs of O-DF Object
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param description Description of O-DF Object.
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Iterable<OdfQlmID> ids,
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    OdfDescription description
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(description),
        scala.Option.empty(),
        attr
    );
  }

  /**
   *
   * @param ids OdfQlmIDs of O-DF Object
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Iterable<OdfQlmID> ids,
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        scala.Option.empty(),
        attr
    );
  }
  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param typeValue
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    String typeValue
  ){
    Vector<OdfQlmID> ids = new Vector<OdfQlmID>();
    OdfQlmID id = OdfFactory.createOdfQlmID(
        path.toArray()[path.length()-1],
        null,
        null,
        null,
        null
    );
    ids.add(id);
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        scala.Option.apply(typeValue),
        attr
    );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param description Description of O-DF Object.
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    OdfDescription description
  ){
    Vector<OdfQlmID> ids = new Vector<OdfQlmID>();
    OdfQlmID id = OdfFactory.createOdfQlmID(
        path.toArray()[path.length()-1],
        null,
        null,
        null,
        null
    );
    ids.add(id);
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(description),
        scala.Option.empty(),
        attr
    );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @return OdfObject
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects
  ){
    Vector<OdfQlmID> ids = new Vector<OdfQlmID>();
    OdfQlmID id = OdfFactory.createOdfQlmID(
        path.toArray()[path.length()-1],
        null,
        null,
        null,
        null
    );
    ids.add(id);
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObject(
        OdfTreeCollection.fromJava(ids),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        scala.Option.empty(),
        attr
    );
  }

  /**
   *
   * @param value Text of description.
   * @param language Language of description.
   * @return OdfDescription
   */
  public static OdfDescription createOdfDescprition(
    String value,
    String language
  ) {
    return new OdfDescription(
        value,
        scala.Option.apply(language)
    );
  }
  /**
   *
   * @param value Text of description.
   * @return OdfDescription
   */
  public static OdfDescription createOdfDescription(
    String value
  ) {
    return new OdfDescription(
        value,
        scala.Option.empty()
    );
  }

  /**
   *
   * @param objects Child O-DF Objects of O-DF Objects.
   * @param version Version of O-DF standart used.
   * @return OdfObjects
   */
  public static OdfObjects createOdfObjects(
    Iterable<OdfObject> objects,
    String version
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObjects(
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(version),
        attr
    );
  }

  /**
   *
   * @param objects Child O-DF Objects of O-DF Objects.
   * @return OdfObjects
   */
  public static OdfObjects createOdfObjects(
    Iterable<OdfObject> objects
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfObjects(
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        attr
    );
  }

  public static OdfMetaData createOdfMetaData(
    Iterable<OdfInfoItem> infoItems
  ){
    return new OdfMetaData(
        OdfTreeCollection.fromJava(infoItems)
    );
  }

  public static OdfQlmID createOdfQlmID(
      String id,
      String idType,
      String tagType,
      Timestamp startDate,
      Timestamp endDate
  ){
    HashMap<String,String> attr = new HashMap<String,String>();
    return new OdfQlmID(
        id,
        scala.Option.apply(idType),
        scala.Option.apply(tagType),
        scala.Option.apply(startDate),
        scala.Option.apply(endDate),
        attr
    );
  }
}
