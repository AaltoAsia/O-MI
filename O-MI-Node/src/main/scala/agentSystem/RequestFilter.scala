package agentSystem

import types.OmiTypes._

object RequestFilter{
  def apply( str: String ): RequestFilter ={
    val read = str.contains("r")
    val write = str.contains("w")
    val call = str.contains("c")
    val delete = str.contains("d")
    (read, write, call, delete) match {
      case (true, false, false, false ) => ReadFilter()
      case (false, true, false, false ) => WriteFilter()
      case (false, false, true, false ) => CallFilter()
      case (false, false, false, true ) => DeleteFilter()
      case (true, true, false, false ) => ReadWriteFilter()
      case (true, true, true, false ) => ReadWriteCallFilter()
      case (false, true, true, false ) => WriteCallFilter()
      case (true, false, true, false ) => ReadCallFilter()
      case (true, false, false, true ) => ReadDeleteFilter()
      case (false, true, false, true ) => WriteDeleteFilter()
      case (false, false, true, true ) => CallDeleteFilter()
      case (true, true, false, true ) => ReadWriteDeleteFilter()
      case (true, true, true, true ) => ReadWriteCallDeleteFilter()
      case (false, true, true, true ) => WriteCallDeleteFilter()
      case (true, false, true, true ) => ReadCallDeleteFilter()
      case (false, false, false,false) => 
        throw new Exception(s"Could not parse request filter: $str. Should contain only lower case letters, rwcd.")
    }
  }
}

sealed trait RequestFilter{
  def filter( request: OdfRequest ): Boolean 
}

sealed trait Delete extends RequestFilter {
  def filter( request: OdfRequest ): Boolean = request match {
    case delete: DeleteRequest => true
    case other: OdfRequest => false
  }
}

sealed trait Read extends RequestFilter {
  def filter( request: OdfRequest ): Boolean = request match {
    case read: ReadRequest => true
    case other: OdfRequest => false
  }
}

sealed trait Write extends RequestFilter {
  def filter(request: OdfRequest): Boolean = request match {
    case write: WriteRequest => true
    case other: OdfRequest => false
  }
}

sealed trait Call extends RequestFilter {
  def filter(request: OdfRequest): Boolean = request match {
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadFilter() extends Read

final case class DeleteFilter() extends Delete

final case class WriteFilter() extends Write

final case class CallFilter() extends Call

final case class ReadWriteFilter() extends Read with Write{
  override def filter( request: OdfRequest ): Boolean = request match {
    case read: ReadRequest => true
    case write: WriteRequest => true
    case other: OdfRequest => false
  }
}

final case class WriteCallFilter() extends Write with Call {
  override def filter(request: OdfRequest): Boolean = request match {
    case write: WriteRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadCallFilter() extends Read with Call {
  override def filter(request: OdfRequest): Boolean = request match {
    case read: ReadRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadWriteCallFilter() extends Read with Write with Call {
  override def filter(request: OdfRequest): Boolean = request match {
    case read: ReadRequest => true
    case write: WriteRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadWriteDeleteFilter() extends Read with Write with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case read: ReadRequest => true
    case delete: DeleteRequest => true
    case write: WriteRequest => true
    case other: OdfRequest => false
  }
}

final case class WriteCallDeleteFilter() extends Write with Call with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case write: WriteRequest => true
    case delete: DeleteRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadCallDeleteFilter() extends Read with Call with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case read: ReadRequest => true
    case delete: DeleteRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadWriteCallDeleteFilter() extends Read with Write with Call with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case read: ReadRequest => true
    case delete: DeleteRequest => true
    case write: WriteRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}

final case class ReadDeleteFilter() extends Read with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case read: ReadRequest => true
    case delete: DeleteRequest => true
    case other: OdfRequest => false
  }
}

final case class WriteDeleteFilter() extends Write with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case delete: DeleteRequest => true
    case write: WriteRequest => true
    case other: OdfRequest => false
  }
}

final case class CallDeleteFilter() extends Call with Delete{
  override def filter( request: OdfRequest ): Boolean = request match {
    case delete: DeleteRequest => true
    case call: CallRequest => true
    case other: OdfRequest => false
  }
}
