package types;

import types.OmiTypes.Returns;
import types.OmiTypes.OmiReturn;

final public class ReturnFactory{
  public static OmiReturn Success(
      String description
    ){
    return new Returns.Success(
        scala.Option.apply(description)
    );
  }

  public static OmiReturn Success(){
    return Success(null);
  }

  public static OmiReturn InvalidRequest(
      String description
    ){
    return new Returns.InvalidRequest(
        scala.Option.apply(description)
    );
  }
  
  public static OmiReturn InvalidRequest(){
    return InvalidRequest(null);
  }

  public static OmiReturn NotFound(
      String description
    ){
    return new Returns.NotFound(
        scala.Option.apply(description)
    );
  }
  
  public static OmiReturn NotFound(){
    return NotFound(null);
  }
  
  public static OmiReturn InternalError(
      String description
    ){
    return new Returns.InternalError(
        scala.Option.apply(description)
    );
  }
  
  public static OmiReturn InternalError(){
    return InternalError(null);
  }
  
  public static OmiReturn NotImplemented(
      String description
    ){
    return new Returns.NotImplemented(
        scala.Option.apply(description)
    );
  }
  
  public static OmiReturn NotImplemented(){
    return NotImplemented(null);
  }

  public static OmiReturn Timeout(
      String description
    ){
    return new Returns.Timeout(
        scala.Option.apply(description)
    );
  }
  
  public static OmiReturn Timeout(){
    return Timeout(null);
  }
} 
