package agentSystem;

import scala.concurrent.Future;
import scala.concurrent.ExecutionContext;
import types.OmiTypes.WriteRequest;
import types.OmiTypes.ReadRequest;
import types.OmiTypes.CallRequest;
import types.OmiTypes.ResponseRequest;

public interface ResponsibleInternalAgent extends InternalAgent{
  /**
   * Method to be called when a WriteRequest  is received.
   */
  Future<ResponseRequest> handleWrite(WriteRequest write);
  //public Future<ResponseRequest>  handleRead(ReadRequest read);
  Future<ResponseRequest> handleCall(CallRequest call);

}
