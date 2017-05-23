package types;

import java.util.ArrayList;
import java.util.Iterator;
import types.OdfTypes.*;
import java.sql.Timestamp;
import scala.collection.immutable.HashMap;
import scala.concurrent.duration.*;
import types.OmiTypes.*;
import types.OmiTypes.UserInfo;

  /**
   * Factory class for creating O-MI types used in Scala.
   */
final public class OmiFactory{
  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param begin Timestamp defining begin of time frame to be read.
   * @param end Timestamp defining end of time frame to be read.
   * @param newest Number of newest values to be read.
   * @param oldest Number of oldest values to be read.
   * @param callback Callback address were results of this request should be sent.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
  }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param begin Timestamp defining begin of time frame to be read.
   * @param end Timestamp defining end of time frame to be read.
   * @param newest Number of newest values to be read.
   * @param oldest Number of oldest values to be read.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param begin Timestamp defining begin of time frame to be read.
   * @param end Timestamp defining end of time frame to be read.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param begin Timestamp defining begin of time frame to be read.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()

        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param newest Number of newest values to be read.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()

        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param begin Timestamp defining begin of time frame to be read.
   * @param end Timestamp defining end of time frame to be read.
   * @param callback Callback address were results of this request should be sent.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param begin Timestamp defining begin of time frame to be read.
   * @param callback Callback address were results of this request should be sent.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param newest Number of newest values to be read.
   * @param callback Callback address were results of this request should be sent.
   * @return ReadRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @param callback Callback address were results of this request should be sent.
   * @return ReadRequest
   */
  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new ReadRequest(
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.apply(cb),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be read.
   * @return ReadRequest
   */
  public static ReadRequest createReadRequest(
      Duration ttl,
      OdfObjects odf
      ){
    return new ReadRequest(
        odf,
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        scala.Option.empty(),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param requestIDs Iterable of requestIDs to be polled.
   * @param callback Callback address were results of this request should be sent.
   * @return PollRequest
   */
  public static PollRequest createPollRequest(
      Duration ttl,
      Iterable<Long > requestIDs,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new PollRequest(
        scala.Option.apply(cb),
        types.JavaHelpers.requestIDsFromJava(requestIDs),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()

        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param requestIDs Iterable of requestIDs to be polled.
   * @return PollRequest
   */
  public static PollRequest createPollRequest(
      Duration ttl,
      Iterable<Long > requestIDs
      ){
    return new PollRequest(
        scala.Option.empty(),
        types.JavaHelpers.requestIDsFromJava(requestIDs),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()

        );
      }

  /**
   * Creates interval subscription with callback address.
   * @param ttl Time to live of subscription.
   * @param interval Interval of sending of subscriped data.
   * @param odf O-DF structure to be subscriped.
   * @param callback Callback address were subscriped data is to be sent.
   * @return SubscriptionRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   * Creates interval subscription to be polled.
   * @param ttl Time to live of subscription.
   * @param interval Interval of sending of subscriped data.
   * @param odf O-DF structure to be subscriped.
   * @return SubscriptionRequest
   */
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
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()

        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param results Iterable of O-MI result contained in O-MI response.
   * @return ResponseRequest
   */
  public static ResponseRequest createResponseRequest(
      Duration ttl,
      Iterable<OmiResult> results
      ){
    return ResponseRequest$.MODULE$.apply(
        OdfTreeCollection.fromJava(results),
        ttl
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param requestIDs Iterable of requestIDs to be cancelled.
   * @return CancelRequest
   */
  public static CancelRequest createCancelRequest(
      Duration ttl,
      Iterable<Long > requestIDs
      ){
    return new CancelRequest(
        types.JavaHelpers.requestIDsFromJava(requestIDs),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()

        );
      }

  /**
   * O-MI result of subscription request.
   * @param returnValue Return element of O-MI result element.
   * @param requestIDs Iterable of requestIDs associated with request associated to this result.
   * @param odf O-DF structure that was subscriped.
   * @return OmiResult
   */
  public static OmiResult createOmiResult(
      OmiReturn returnValue,
      Iterable<Long > requestIDs,
      OdfObjects odf
      ){
    return OmiResult$.MODULE$.apply(
        returnValue,
        types.JavaHelpers.requestIDsFromJava(requestIDs),
        scala.Option.apply(odf)
        );
      }

  /**
   * O-MI result of subscription or cancel request.
   * @param returnValue Return element of O-MI result element.
   * @param requestIDs Iterable of requestIDs associated with request associated to this result.
   * @return OmiResult
   */
  public static OmiResult createOmiResult(
      OmiReturn returnValue,
      Iterable<Long > requestIDs
      ){
    return OmiResult$.MODULE$.apply(
        returnValue,
        types.JavaHelpers.requestIDsFromJava(requestIDs),
        scala.Option.empty()
        );
      }

  /**
   * O-MI result of subscription or cancel request.
   * @param returnValue Return element of O-MI result element.
   * @return OmiResult
   */
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

  /**
   *
   * @param returnCode HTTP return code associated with O-MI request. Different from HTTP return code of actual HTTP request.
   * @param description Textual description of return value.
   * @return OmiReturn
   */
  public static OmiReturn createOmiReturn(
      String returnCode,
      String description
      ){
    return OmiReturn$.MODULE$.apply(
        returnCode,
        scala.Option.apply(description)
    );
  }

  /**
   *
   * @param returnCode HTTP return code associated with O-MI request. Different from HTTP return code of actual HTTP request.
   * @return OmiReturn
   */
  public static OmiReturn createOmiReturn(
      String returnCode
      ){
    return OmiReturn$.MODULE$.apply(
        returnCode,
        scala.Option.empty()
    );
  }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be written to O-MI Node.
   * @param callback Callback address were results of this request should be sent.
   * @return WriteRequest
   */
  public static WriteRequest createWriteRequest(
      Duration ttl,
      OdfObjects odf,
      String callback
      ){
    Callback cb = new RawCallback(callback);
    return new WriteRequest(
        odf,
        scala.Option.apply(cb),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

  /**
   *
   * @param ttl Time to live of request.
   * @param odf O-DF structure to be written to O-MI Node.
   * @return WriteRequest
   */
  public static WriteRequest createWriteRequest(
      Duration ttl,
      OdfObjects odf
      ){
    return new WriteRequest(
        odf,
        scala.Option.empty(),
        ttl,
            new UserInfo(UserInfo.apply$default$1(),UserInfo.apply$default$2()),
            scala.Option.empty()
        );
      }

}

