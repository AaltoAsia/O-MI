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
    Callback cb = new RawCallback(callback);
    return new ReadRequest(
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.apply(cb),
        ttl
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
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.empty(),
        ttl
        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      Timestamp begin,
      Timestamp end
      ){
    return new ReadRequest(
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        ttl
        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      Timestamp begin
      ){
    return new ReadRequest(
        odf,
        scala.Option.apply(begin),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        ttl

        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      int newest,
      int oldest
      ){
    return new ReadRequest(
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.empty(),
        ttl

        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      int newest
      ){
    return new ReadRequest(
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.empty(),
        scala.Option.empty(),
        ttl

        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      Timestamp begin,
      Timestamp end,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new ReadRequest(
        odf,
        scala.Option.apply(begin),
        scala.Option.apply(end),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(cb),
        ttl
        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      Timestamp begin,
      String callback

      ){
    Callback cb = new RawCallback(callback);
    return new ReadRequest(
        odf,
        scala.Option.apply(begin),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(cb),
        ttl
        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      int newest,
      int oldest,
      String callback

      ){
    Callback cb = new RawCallback(callback);
    return new ReadRequest(
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.apply(oldest),
        scala.Option.apply(cb),
        ttl
        );
      }

  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      int newest,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new ReadRequest(
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(newest),
        scala.Option.empty(),
        scala.Option.apply(cb),
        ttl
        );
      }

  public static PollRequest createPollRequest(
      Duration ttl,
      Iterable<Long > requestIDs,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new PollRequest(
        scala.Option.apply(cb),
        types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
        ttl

        );
      }
  public static PollRequest createPollRequest(
      Duration ttl,
      Iterable<Long > requestIDs
      ){
    return new PollRequest(
        scala.Option.empty(),
        types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
        ttl

        );
      }

  public static SubscriptionRequest createSubscriptionRequest(
      Duration ttl,
      Duration interval,
      OdfObjects odf,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new SubscriptionRequest(
        interval,
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(cb),
        ttl
        );
      }

  public static SubscriptionRequest createSubscriptionRequest(
      Duration ttl,
      Duration interval,
      OdfObjects odf
      ){
    return new SubscriptionRequest(
        interval,
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        ttl

        );
      }

  public static ResponseRequest createResponseRequest(
      Duration ttl,
      Iterable<OmiResult> results
      ){
    return ResponseRequest$.MODULE$.apply(
        OdfTreeCollection.fromJava(results),
        ttl
        );
      }

  public static CancelRequest createCancelRequest(
      Duration ttl,
      Iterable<Long > requestIDs
      ){
    return new CancelRequest(
        types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
        ttl

        ); 
      }

  public static OmiResult createOmiResult(
      OmiReturn returnValue,
      Iterable<Long > requestIDs,
      OdfObjects odf
      ){
    return OmiResult$.MODULE$.apply(
        returnValue,
        types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
        scala.Option.apply(odf)
        );
      }

  public static OmiResult createOmiResult(
      OmiReturn returnValue,
      Iterable<Long > requestIDs
      ){
    return OmiResult$.MODULE$.apply(
        returnValue,
        types.OmiTypes.JavaHelpers.requestIDsFromJava(requestIDs),
        scala.Option.empty()
        );
      }

  public static OmiResult createOmiResult(
      OmiReturn returnValue
      ){
    scala.collection.immutable.Vector<java.lang.Object> empty = OdfTreeCollection.empty();
    return OmiResult$.MODULE$.apply(
        returnValue,
        empty,
        scala.Option.empty()
        );
      }

  public static OmiReturn createOmiReturn(
      String returnCode,
      String description
      ){
    return OmiReturn$.MODULE$.apply(
        returnCode,
        scala.Option.apply(description)
        );
      }

  public static WriteRequest createWriteRequest(
      Duration ttl,
      OdfObjects odf,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new WriteRequest(
        odf,
        scala.Option.apply(cb),
        ttl
        );
      }

  public static WriteRequest createWriteRequest(
      Duration ttl,
      OdfObjects odf
      ){
    return new WriteRequest(
        odf,
        scala.Option.empty(),
        ttl

        );
      }

}

