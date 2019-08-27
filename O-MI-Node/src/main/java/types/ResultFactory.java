package types;

import types.omi.Results;
import types.omi.OmiResult;
import types.odf.ODF;

final public class ResultFactory{
  public static OmiResult Success(
      Iterable<Long > requestIDs,
      ODF odf,
      String description
    ){
    return new Results.Success(
        types.JavaHelpers.requestIDsFromJava(requestIDs),
        scala.Option.apply(odf),
        scala.Option.apply(description)
    );
  }

  public static OmiResult Success(
      ODF odf,
      String description
    ){
    return Success(null, odf, description);
  }
  
  public static OmiResult Success(
      Iterable<Long > requestIDs,
      String description
    ){
    return Success(requestIDs, null, description);
  }
  public static OmiResult Success(
      Iterable<Long > requestIDs,
      ODF odf
    ){
    return Success(requestIDs, odf, null);
  }

  public static OmiResult Success(
      ODF odf
    ){
    return Success(null, odf, null);
  }
  public static OmiResult Success(
      Iterable<Long > requestIDs
    ){
    return Success(requestIDs, null, null);
  }

  public static OmiResult Success(
      String description
    ){
    return Success(null, null, description);
  }

  public static OmiResult NotImplemented(
      String description
    ){
    return new Results.NotImplemented(
        scala.Option.apply(description)
    );
  }
  
  public static OmiResult NotImplemented(
    ){
    return NotImplemented(null);
  }

  public static OmiResult InternalError(
      String description
    ){
    return new Results.InternalError(
        scala.Option.apply(description)
    );
  }

  public static OmiResult InternalError(
      Exception exp
    ){
    return Results.InternalError$.MODULE$.apply(
        exp
    );
  }
  
  public static OmiResult InternalError(){
    String tmp = null;
    return InternalError(tmp);
  }

  public static OmiResult InvalidRequest(
      String description
    ){
    return new Results.InvalidRequest(
        scala.Option.apply(description)
    );
  }
  
  public static OmiResult InvalidRequest(){
    return InvalidRequest(null);
  }

  public static OmiResult NotFound(
      String description
    ){
    return new Results.NotFound(
        scala.Option.apply(description)
    );
  }

  public static OmiResult NotFound(){
    return NotFound(null);
  }

  public static OmiResult Unauthorized(
      String description
    ){
    return new Results.Unauthorized(
        scala.Option.apply(description)
    );
  }

  public static OmiResult Unauthorized(){
    return Unauthorized(null);
  }

  public static OmiResult Timeout(
      String description
    ){
    return new Results.Timeout(
        scala.Option.apply(description)
    );
  }

  public static OmiResult Timeout(){
    return Unauthorized(null);
  }

} 
