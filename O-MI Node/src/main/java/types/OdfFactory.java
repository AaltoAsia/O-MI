package types;

import types.OdfTypes.*;
import java.sql.Timestamp;
import scala.collection.immutable.HashMap;

public class OdfFactory{
  public static OdfValue createOdfValue(
    String value,
    String typeValue,
    Timestamp timestamp
  ){
    HashMap<String,String> attr = new HashMap();
    return OdfValue$.MODULE$.apply(
        value,
        typeValue,
        timestamp,
        attr
        );
  }
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
  public static OdfDescription createOdfDescprition(
    String value,
    String lang
  ) {
    return new OdfDescription(
        value,
        scala.Option.apply(lang)
    );
  }
  public static OdfObjects createOdfObjects(
    Iterable<OdfObject> objects,
    String version
  ){
    return new OdfObjects(
        OdfTreeCollection.fromJava(objects),
        scala.Option.apply(version)
    );
  }
  public static OdfObjects createOdfObjects(
    Iterable<OdfObject> objects
  ){
    return new OdfObjects(
        OdfTreeCollection.fromJava(objects),
        scala.Option.empty()
    );
  }

}
