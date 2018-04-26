package types;

import java.util.Dictionary;
import types.odf.*;
import types.JavaHelpers;
import types.odf.Objects;
import types.odf.ImmutableODF;
import types.odf.MutableODF;
import types.odf.Node;
import types.odf.Description;
import types.odf.MetaData;
import types.odf.Object;
import types.odf.InfoItem;
import types.odf.Value;
import types.odf.Value$;
import types.odf.OdfCollection;
import types.odf.QlmID;
import types.odf.QlmID$;
import java.sql.Timestamp;

  /**
   * Factory class for creating O-DF types used in Scala.
   */
public class ODFFactory{

  /**
   *
   * @param value Value inside of O-DF value element.
   * @param typeAttribute Type of value, one of built in XML Schema data types specifed in
   *  <a href="https://www.w3.org/TR/xmlschema-2/#built-in-datatypes">XML Schema types</a>
   *  Parameter value is cast to type specifed by typeAttribute parameter. If cast fails, value's
   *  type will be String.
   * @param timestamp Timestamp when value was measured or received.
   * @return Value
   */
  public static Value<java.lang.Object> createValue(
    String value,
    String typeAttribute,
    Timestamp timestamp,
    Dictionary<String,String> attributes
  ){
    return Value$.MODULE$.apply(
        value,
        typeAttribute,
        timestamp,
        JavaHelpers.dictionaryToMap(attributes)
        );
  }

  /**
   *
   * @param value Value inside of O-DF value element.
   * @param timestamp Timestamp when value was measured or received.
   * @return Value
   */
  public static Value<java.lang.Object> createValue(
    java.lang.Object value,
    Timestamp timestamp,
    Dictionary<String,String> attributes
  ){
    if( attributes != null ){
      return Value$.MODULE$.apply(
          value,
          timestamp,
          JavaHelpers.dictionaryToMap(attributes)
          );
    } else {
      return createValue(value,timestamp);
    }

  }
  /**
   *
   * @param value Value inside of O-DF value element.
   * @param timestamp Timestamp when value was measured or received.
   * @return Value
   */
  public static Value<java.lang.Object> createValue(
    java.lang.Object value,
    Timestamp timestamp
  ){
    return Value$.MODULE$.apply(
        value,
        timestamp
        );
  }



  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param typeAttribute type parameter of InfoItem.
   * @param names Additional names of InfoItem.
   * @param description Descriptions of InfoItem.
   * @param values Values stored in InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param attributes Attributes of InfoItem.
   * @return InfoItem
   */
  public static InfoItem createInfoItem(
    Path path,
    String typeAttribute,
    Iterable<QlmID> names,
    Iterable<Description> description,
    Iterable<Value<java.lang.Object>> values,
    MetaData metaData,
    Dictionary<String,String> attributes
  ){
    return new InfoItem(
        path.toSeq().last(),
        path,
        scala.Option.apply(typeAttribute),
        OdfCollection.fromJava(names),
        OdfCollection.fromJava(description).toSet(),
        OdfCollection.fromJava(values),
        scala.Option.apply(metaData),
        JavaHelpers.dictionaryToMap(attributes)
        );
  }

  /**
   *
   * @param ids QlmIDs of O-DF Object
   * @param path Path of O-DF Object.
   * @param description Description of O-DF Object.
   * @param typeAttribute Type of an O-DF Object
   * @return Object
   */
  public static Object createObject(
    Iterable<QlmID> ids,
    Path path,
    String typeAttribute,
    Iterable<Description> description,
    Dictionary<String,String> attributes
  ){
    return new Object(
        OdfCollection.fromJava(ids),
        path,
        scala.Option.apply(typeAttribute),
        OdfCollection.fromJava(description).toSet(),
        JavaHelpers.dictionaryToMap(attributes)
    );
  }

  /**
   *
   * @param value Text of description.
   * @param language Language of description.
   * @return Description
   */
  public static Description createDescription(
    String value,
    String language
  ) {
    return new Description(
        value,
        scala.Option.apply(language)
    );
  }

  /**
   *
   * @param value Text of description.
   * @return Description
   */
  public static Description createDescription(
    String value
  ) {
    return new Description(
        value,
        scala.Option.empty()
    );
  }

  /**
   *
   * @param version Version of O-DF standard used.
   * @param attributes Attributes of Objects.
   * @return Objects
   */
  public static Objects createObjects(
    String version,
    Dictionary<String,String> attributes
  ){
    return new Objects(
        scala.Option.apply(version),
        JavaHelpers.dictionaryToMap(attributes)
    );
  }


  public static MetaData createMetaData(
    Iterable<InfoItem> infoItems
  ){
    return new MetaData(
        OdfCollection.fromJava(infoItems)
    );
  }

  public static QlmID createQlmID(
      String id,
      String idType,
      String tagType,
      Timestamp startDate,
      Timestamp endDate,
    Dictionary<String,String> attributes
  ){
    return new QlmID(
        id,
        scala.Option.apply(idType),
        scala.Option.apply(tagType),
        scala.Option.apply(startDate),
        scala.Option.apply(endDate),
        JavaHelpers.dictionaryToMap(attributes)
    );
  }
  
  public static ImmutableODF createImmutableODF(
      Iterable<Node> nodes
  ){
    return ImmutableODF$.MODULE$.apply(OdfCollection.fromJava(nodes));
  }
  public static MutableODF createMutableODF(
      Iterable<Node> nodes
  ){
    return MutableODF$.MODULE$.apply(OdfCollection.fromJava(nodes));
  }
}
