package types;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import scala.collection.JavaConverters;
import scala.Tuple2;
import types.odf.*;

import java.sql.Timestamp;
import java.util.Vector;

  /**
   * Factory class for creating O-DF types used in Scala.
   */
public class OdfFactory{

  public static <K, V> scala.collection.immutable.Map<K, V> toScalaImmutableMap(java.util.Map<K, V> jmap) {
    List<Tuple2<K, V>> tuples = jmap.entrySet()
      .stream()
      .map(e -> Tuple2.apply(e.getKey(), e.getValue()))
      .collect(Collectors.toList());

    return scala.collection.immutable.Map$.MODULE$.apply(JavaConverters.asScalaBuffer(tuples).toSeq());
  }
  /**
   *
   * @param value Value inside of O-DF value element.
   * @param typeValue Type of value, one of built in XML Schema data types specifed in
   *  <a href="https://www.w3.org/TR/xmlschema-2/#built-in-datatypes">XML Schema types</a>
   *  Parameter value is cast to type specifed by typeValue parameter. If cast fails, value's
   *  type will be String.
   * @param timestamp Timestamp when value was measured or received.
   * @return Value
   */
  public static Value<java.lang.Object> createValue(
    String value,
    String typeValue,
    Timestamp timestamp
  ){
    return Value$.MODULE$.apply(
        value,
        typeValue,
        timestamp
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
   * @param values Values stored in InfoItem.
   * @param descriptions Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param typeValue type parameter of InfoItem.
   * @return InfoItem
   */
  public static InfoItem createInfoItem(
    Path path,
    String typeValue,
    Iterable<QlmID> names,
    Iterable<Description> descriptions,
    Iterable<Value<java.lang.Object>> values,
    MetaData metaData,
    java.util.Map<String,String> attr 
  ){
    return new InfoItem(
        path.toSeq().last(),
        path,
        scala.Option.apply(typeValue),
        OdfCollection.fromJava(names),
        Description.unionReduce(OdfCollection.fromJava(descriptions).toSet()),
        OdfCollection.fromJava(values),
        scala.Option.apply(metaData),
        toScalaImmutableMap(attr)
        );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItem.
   * @param descriptions Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param typeValue type parameter of InfoItem.
   * @return InfoItem
   */
  public static InfoItem createInfoItem(
    Path path,
    String typeValue,
    Iterable<Description> descriptions,
    Iterable<Value<java.lang.Object>> values,
    MetaData metaData
  ){
    return createInfoItem(
        path,
        typeValue,
        new Vector<QlmID>(),
        descriptions,
        values,
        metaData,
        new HashMap<>()
    );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItem.
   * @param descriptions Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param typeValue type parameter of InfoItem.
   * @return InfoItem
   */
  public static InfoItem createInfoItem(
    Path path,
    Iterable<Description> descriptions,
    Iterable<Value<java.lang.Object>> values,
    MetaData metaData
  ){
    return createInfoItem(
        path,
        null,
        new Vector<QlmID>(),
        descriptions,
        values,
        metaData,
        new HashMap<>()
    );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItem.
   * @param descriptions Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param typeValue type parameter of InfoItem.
   * @return InfoItem
   */
  public static InfoItem createInfoItem(
    Path path,
    String typeValue,
    Iterable<Description> descriptions,
    Iterable<Value<java.lang.Object>> values,
    MetaData metaData,
    java.util.Map<String,String> attr 
  ){
    return createInfoItem(
        path,
        typeValue,
        new Vector<QlmID>(),
        descriptions,
        values,
        metaData,
        attr
    );
  }

  /**
   *
   * @param path Path of O-DF InfoItem.
   * @param values Values stored in InfoItem.
   * @param descriptions Description of InfoItem.
   * @param metaData MetaData of InfoItem.
   * @param typeValue type parameter of InfoItem.
   * @return InfoItem
   */
  public static InfoItem createInfoItem(
    Path path,
    Iterable<Value<java.lang.Object>> values
  ){
    return createInfoItem(
        path,
        null,
        new Vector<QlmID>(),
        new Vector<Description>(),
        values,
        null,
        new HashMap<>()
    );
  }

  /**
   *
   * @param ids QlmIDs of O-DF Object
   * @param path Path of O-DF Object.
   * @param descriptions Description of O-DF Object.
   * @param typeValue Type of an O-DF Object
   * @return Object
   */
  public static types.odf.Object createObject(
    Iterable<QlmID> ids,
    Path path,
    Iterable<Description> descriptions,
    String typeValue,
    java.util.Map<String,String> attr 
  ){
    return new types.odf.Object(
        OdfCollection.fromJava(ids),
        path,
        scala.Option.apply(typeValue),
        Description.unionReduce(OdfCollection.fromJava(descriptions).toSet()),
        toScalaImmutableMap(attr)
    );
  }
  /**
   *
   * @param path Path of O-DF Object.
   * @param descriptions Description of O-DF Object.
   * @return Object
   */
  public static types.odf.Object createObject(
    Path path,
    Iterable<Description> descriptions
  ){
    Vector<QlmID> ids = new Vector<QlmID>();
    ids.add(createQlmID(path.toSeq().last()));
    return createObject(
        ids,
        path,
        descriptions,
        null,
        new HashMap<>()
    );
  }

  /**
   *
   * @param value Text of descriptions.
   * @param language Language of descriptions.
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
   * @param value Text of descriptions.
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
   * @return Objects
   */
  public static Objects createObjects(
    String version,
    Map<String,String> attr
  ){
    return new Objects(
        scala.Option.apply(version),
        toScalaImmutableMap(attr)
    );
  }

  /**
   *
   * @return Objects
   */
  public static Objects createObjects(
    Map<String,String> attr
  ){
    return new Objects(
        scala.Option.empty(),
        toScalaImmutableMap(attr)
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
      Map<String,String> attr
  ){
    return new QlmID(
        id,
        scala.Option.apply(idType),
        scala.Option.apply(tagType),
        scala.Option.apply(startDate),
        scala.Option.apply(endDate),
        toScalaImmutableMap(attr)
    );
  }
  public static QlmID createQlmID(
      String id
  ){
    return createQlmID(
        id,
        null,
        null,
        null,
        null,
        new HashMap<>()
    );
  }

  public static ImmutableODF createImmutableODF( 
      Iterable<Node> nodes
  ){
    return ImmutableODF.apply(OdfCollection.fromJava(nodes));
  }
}
