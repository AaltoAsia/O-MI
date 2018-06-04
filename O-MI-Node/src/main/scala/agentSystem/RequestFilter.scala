package agentSystem
import types.OmiTypes._

  object RequestFilter{
    def apply( str: String ): RequestFilter ={
      val read = str.contains("r")
      val write = str.contains("w")
      val call = str.contains("c")
      (read, write, call) match {
        case (true, false, false) => ReadFilter()
        case (false, true, false) => WriteFilter()
        case (false, false, true) => CallFilter()
        case (true, true, false) => ReadWriteFilter()
        case (true, true, true) => ReadWriteCallFilter()
        case (false, true, true) => WriteCallFilter()
        case (true, false, true) => ReadCallFilter()
        case (false, false, false) => 
          throw new Exception(s"Could not parse request filter: $str. Should contain only lower case letters, rwc.")
      }
    }
  }
  sealed trait RequestFilter{
    def filter( request: OdfRequest ): Boolean 
  }
  sealed trait Read extends RequestFilter {
    def filter( request: OdfRequest ): Boolean = request match {
      case read: ReadRequest => true
      case other: OdfRequest => false
    }
  }
  sealed trait Write extends RequestFilter{
    def filter( request: OdfRequest ): Boolean = request match {
      case write: WriteRequest => true
      case other: OdfRequest => false
    }
  }
  sealed trait Call extends RequestFilter{
    def filter( request: OdfRequest ): Boolean = request match {
      case call: CallRequest => true
      case other: OdfRequest => false
    }
  }
  final case class ReadFilter() extends Read
  final case class WriteFilter() extends Write
  final case class CallFilter() extends Call
  final case class ReadWriteFilter() extends Read with Write{
    override def filter( request: OdfRequest ): Boolean = request match {
      case read: ReadRequest => true
      case write: WriteRequest => true
      case other: OdfRequest => false
    }
  }
  final case class WriteCallFilter() extends Write with Call{
    override def filter( request: OdfRequest ): Boolean = request match {
      case write: WriteRequest => true
      case call: CallRequest => true
      case other: OdfRequest => false
    }
  }
  final case class ReadCallFilter() extends Read with Call{
    override def filter( request: OdfRequest ): Boolean = request match {
      case read: ReadRequest => true
      case call: CallRequest => true
      case other: OdfRequest => false
    }
  }
  final case class ReadWriteCallFilter() extends Read with Write with Call{
    override def filter( request: OdfRequest ): Boolean = request match {
      case read: ReadRequest => true
      case write: WriteRequest => true
      case call: CallRequest => true
      case other: OdfRequest => false
    }
  }
