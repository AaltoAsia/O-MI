package types;

import java.util.ArrayList;
import java.util.Iterator;
import types.OdfTypes.*;
import java.sql.Timestamp;
import scala.collection.immutable.HashMap;
import scala.concurrent.duration.*;
import types.OmiTypes.*;

public class OmiFactory{
  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    Timestamp begin,
    Timestamp end,
    int newest,
    int oldest,
    String callback
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.apply(callback)
      );
  }
  
  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    Timestamp begin,
    Timestamp end,
    int newest,
    int oldest
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.empty()
      );
  }

  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    Timestamp begin,
    Timestamp end
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty()
      );
  }

  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    Timestamp begin
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.apply(begin),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty()
      );
  }
  
  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    int newest,
    int oldest
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.empty()
      );
  }

  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    int newest
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.empty(),
        scala.Option.empty()
      );
  }

  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    Timestamp begin,
    Timestamp end,
    String callback
  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(callback)
      );
  }

  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    Timestamp begin,
    String callback

  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.apply(begin),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(callback)
      );
  }
  
  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    int newest,
    int oldest,
    String callback

  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.apply(callback)
      );
  }

  public static ReadRequest createReadRequest(
    Duration ttl,
    OdfObjects odf,
    int newest,
    String callback

  ){
    return new ReadRequest(
        ttl,
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.empty(),
        scala.Option.apply(callback)
      );
  }

  public static PollRequest createPollRequest(
    Duration ttl,
    Iterable<Long > requestIDs,
    String callback
  ){
    return new PollRequest(
      ttl,
      scala.Option.apply(callback),
      types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs)
    );
  }
  public static PollRequest createPollRequest(
    Duration ttl,
    Iterable<Long > requestIDs
  ){
    return new PollRequest(
      ttl,
      scala.Option.empty(),
      types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs)
    );
  }
  
  public static SubscriptionRequest createSubscriptionRequest(
    Duration ttl,
    Duration interval,
    OdfObjects odf,
    String callback
  ){
    return new SubscriptionRequest(
      ttl,
      interval,
      odf,
      scala.Option.empty(),
      scala.Option.empty(),
      scala.Option.apply(callback)
    );
  }
  
  public static SubscriptionRequest createSubscriptionRequest(
    Duration ttl,
    Duration interval,
    OdfObjects odf
  ){
    return new SubscriptionRequest(
      ttl,
      interval,
      odf,
      scala.Option.empty(),
      scala.Option.empty(),
      scala.Option.empty()
    );
  }
  
  public static ResponseRequest createResponseRequest(
    Duration ttl,
    Iterable<OmiResult> results
  ){
    return new ResponseRequest(
      OdfTreeCollection.fromJava(results),
      ttl
    );
  }
  
  public static CancelRequest createCancelRequest(
    Duration ttl,
    Iterable<Long > requestIDs
  ){
    return new CancelRequest(
      ttl,
      types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs)
    ); 
  }
  
  public static OmiResult createOmiResult(
    OmiReturn returnValue,
    Iterable<Long > requestIDs,
    OdfObjects odf
  ){
    return new OmiResult(
      returnValue,
      types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
      scala.Option.apply(odf)
    );
  }

  public static OmiResult createOmiResult(
    OmiReturn returnValue,
    Iterable<Long > requestIDs
  ){
    return new OmiResult(
      returnValue,
      types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
      scala.Option.empty()
    );
  }

  public static OmiResult createOmiResult(
    OmiReturn returnValue
  ){
    scala.collection.immutable.Vector<java.lang.Object> empty = OdfTreeCollection.empty();
    return new OmiResult(
      returnValue,
      empty,
      scala.Option.empty()
    );
  }

  public static OmiReturn createOmiReturn(
    String returnCode,
    String description
  ){
    return new OmiReturn(
      returnCode,
      scala.Option.apply(description)
    );
  }
  
  public static WriteRequest createWriteRequest(
    Duration ttl,
    OdfObjects odf,
    String callback
  ){
    return new WriteRequest(
      ttl,
      odf,
      scala.Option.apply(callback)
    );
  }

  public static WriteRequest createWriteRequest(
    Duration ttl,
    OdfObjects odf
  ){
    return new WriteRequest(
      ttl,
      odf,
      scala.Option.empty()
    );
  }
  
}

