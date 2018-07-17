package agentSystem;

import scala.concurrent.Future;
import types.OmiTypes.CallRequest;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.WriteRequest;

public interface ResponsibleInternalAgent extends InternalAgent{
  /**
   * Method to be called when a WriteRequest  is received.
   */
  Future<ResponseRequest> handleWrite(WriteRequest write);
  //public Future<ResponseRequest>  handleRead(ReadRequest read);
  Future<ResponseRequest> handleCall(CallRequest call);

}
