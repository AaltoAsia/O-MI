package agentSystem;

import scala.concurrent.Future;
import types.OmiTypes.CallRequest;
import types.OmiTypes.ResponseRequest;
import types.OmiTypes.WriteRequest;
import types.OmiTypes.ReadRequest;
import types.OmiTypes.DeleteRequest;

public interface ResponsibleInternalAgent extends InternalAgent{
  /**
   * Method to be called when a WriteRequest  is received.
   */
  Future<ResponseRequest> handleWrite(WriteRequest write);
  Future<ResponseRequest>  handleRead(ReadRequest read);
  Future<ResponseRequest>  handleDelete(DeleteRequest delete);
  Future<ResponseRequest> handleCall(CallRequest call);

}
