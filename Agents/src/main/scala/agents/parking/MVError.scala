package agents.parking

import types.ParseError

case class MVError( msg: String ) extends ParseError(msg, "MobiVoc error:")
