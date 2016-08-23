package types;

import types.OdfTypes.*;
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
   *  <a href="https://www.w3.org/TR/xmlschema-2/#built-in-datatypes">Akka recommends to</a>
   *  Parameter value is cast to type specifed by typeValue parameter. If cast fails, value's
   *  type will be String.
   * @param timestamp Timestamp when value was measured or received.
   */
  public static OdfValue createOdfValue(
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
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItew.
   * @param description Description of InfoItem.
   */
  public static OdfInfoItem createOdfInfoItem(
    Path path,
    Iterable<OdfValue> values,
    OdfDescription description
  ){
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.apply(description),
        scala.Option.empty()//Look at type of MetaData. 
        );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItew.
   */
  public static OdfInfoItem createOdfInfoItem(
    Path path,
    Iterable<OdfValue> values
  ){
    return new OdfInfoItem(
        path,
        OdfTreeCollection.fromJava(values),
        scala.Option.empty(),
        scala.Option.empty()//Look at type of MetaData. 
        );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param description Description of O-DF Object.
   * @param typeValue 
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    OdfDescription description,
    String typeValue
  ){
    return new OdfObject(
        OdfTreeCollection.empty(),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(description),
        scala.Option.apply(typeValue) 
    );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param typeValue 
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    String typeValue
  ){
    return new OdfObject(
        OdfTreeCollection.empty(),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        scala.Option.apply(typeValue) 
    );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   * @param description Description of O-DF Object.
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects,
    OdfDescription description
  ){
    return new OdfObject(
        OdfTreeCollection.empty(),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(description),
        scala.Option.empty()
    );
  }

  /**
   *
   * @param path Path of O-DF Object.
   * @param infoitems Child O-DF InfoItems of created O-DF Object.
   * @param objects Child O-DF Objects of created O-DF Object.
   */
  public static OdfObject createOdfObject(
    Path path,
    Iterable<OdfInfoItem> infoitems,
    Iterable<OdfObject> objects
  ){
    return new OdfObject(
        OdfTreeCollection.empty(),
        path,
        OdfTreeCollection.fromJava(infoitems),
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty(),
        scala.Option.empty()
    );
  }

  /**
   *
   * @param value Text of description.
   * @param language Language of description.
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
   */
  public static OdfDescription createOdfDescprition(
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
   */
  public static OdfObjects createOdfObjects(
    Iterable<OdfObject> objects,
    String version
  ){
    return new OdfObjects(
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(version)
    );
  }

  /**
   *
   * @param objects Child O-DF Objects of O-DF Objects.
   */
  public static OdfObjects createOdfObjects(
    Iterable<OdfObject> objects
  ){
    return new OdfObjects(
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty()
    );
  }

}
