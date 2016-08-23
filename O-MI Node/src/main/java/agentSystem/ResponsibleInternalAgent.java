package agentSystem;

import types.OmiTypes.WriteRequest;

public interface ResponsibleInternalAgent extends InternalAgent{
  /**
   * Method to be called when a WriteRequest  is received.
   */
  public void handleWrite(WriteRequest write);
}
