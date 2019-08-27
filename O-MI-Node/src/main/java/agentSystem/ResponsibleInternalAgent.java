package agentSystem;

import scala.concurrent.Future;
import types.omi.CallRequest;
import types.omi.ResponseRequest;
import types.omi.WriteRequest;
import types.omi.ReadRequest;
import types.omi.DeleteRequest;

public interface ResponsibleInternalAgent extends InternalAgent{
  /**
   * Method to be called when a WriteRequest  is received.
   */
  Future<ResponseRequest> handleWrite(WriteRequest write);
  Future<ResponseRequest>  handleRead(ReadRequest read);
  Future<ResponseRequest>  handleDelete(DeleteRequest delete);
  Future<ResponseRequest> handleCall(CallRequest call);

}
