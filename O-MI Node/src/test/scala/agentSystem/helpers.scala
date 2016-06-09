package agentSystem
import scala.util.Try
import scala.concurrent.{ Future,ExecutionContext, TimeoutException, Promise }

import com.typesafe.config.Config

import agentSystem._
import agentSystem.AgentTypes._


trait StartFailure{
  protected final def start: Try[InternalAgentSuccess ] = Try{  
    throw  CommandFailed("Test failure.")
  }
}
trait StopFailure{
  protected final def stop: Try[InternalAgentSuccess ] = Try{ 
    throw  CommandFailed("Test failure.")
  }
}
trait StartSuccess{
  protected final def start: Try[InternalAgentSuccess ] = Try{  
    CommandSuccessful()
  }
}
trait StopSuccess{
  protected final def stop: Try[InternalAgentSuccess ] = Try{ 
    CommandSuccessful()
  }
}
class FailurePropsAgent extends InternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
object FailurePropsAgent extends PropsCreator{
  final def props(config: Config) : InternalAgentProps = { 
    throw  CommandFailed("Test failure.")
  }
}

class FFAgent extends InternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
class FSAgent extends InternalAgent with StartFailure with StopSuccess{
  def config = throw  CommandFailed("Test failure.")
}
class SFAgent extends InternalAgent with StartSuccess with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
class SSAgent extends InternalAgent with StartSuccess with StopSuccess{
  def config = throw  CommandFailed("Test failure.")
}
object SSAgent extends PropsCreator{
  final def props(config: Config) : InternalAgentProps = { 
    InternalAgentProps( new SSAgent )
  }
}
object FFAgent extends PropsCreator{
  final def props(config: Config) : InternalAgentProps = { 
    InternalAgentProps( new FFAgent )
  }
}
object SFAgent extends PropsCreator{
  final def props(config: Config) : InternalAgentProps = { 
    InternalAgentProps( new SFAgent )
  }
}
object FSAgent extends PropsCreator{
  final def props(config: Config) : InternalAgentProps = { 
    InternalAgentProps( new FSAgent )
  }
}

class CompanionlessAgent extends InternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}

object ClasslessCompanion extends PropsCreator {
  final def props(config: Config) : InternalAgentProps = { 
    InternalAgentProps( new FFAgent )
  }
}

class WrongInterfaceAgent {
  def config = throw  CommandFailed("Test failure.")
}
object WrongInterfaceAgent {

}

class NotPropsCreatorAgent  extends InternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
object NotPropsCreatorAgent {

}

class WrongPropsAgent  extends InternalAgent with StartFailure with StopFailure{
  def config = throw  CommandFailed("Test failure.")
}
object WrongPropsAgent extends PropsCreator{
  final def props(config: Config) : InternalAgentProps = { 
    InternalAgentProps( new FFAgent )
  }
}


