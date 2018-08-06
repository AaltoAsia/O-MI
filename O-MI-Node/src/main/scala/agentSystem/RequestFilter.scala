
package agentSystem

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
}

sealed trait Delete extends RequestFilter {
}

sealed trait Read extends RequestFilter {
}

sealed trait Write extends RequestFilter {
}

sealed trait Call extends RequestFilter {
}

final case class ReadFilter() extends Read

final case class DeleteFilter() extends Delete

final case class WriteFilter() extends Write

final case class CallFilter() extends Call

final case class ReadWriteFilter() extends Read with Write{
}

final case class WriteCallFilter() extends Write with Call {
}

final case class ReadCallFilter() extends Read with Call {
}

final case class ReadWriteCallFilter() extends Read with Write with Call {
}

final case class ReadWriteDeleteFilter() extends Read with Write with Delete{
}

final case class WriteCallDeleteFilter() extends Write with Call with Delete{
}

final case class ReadCallDeleteFilter() extends Read with Call with Delete{
}

final case class ReadWriteCallDeleteFilter() extends Read with Write with Call with Delete{
}

final case class ReadDeleteFilter() extends Read with Delete{
}

final case class WriteDeleteFilter() extends Write with Delete{
}

final case class CallDeleteFilter() extends Call with Delete{
}
