Agent Developer Guide
=====================

What are Agents?
----------------
Agents are small programs that connect to sensors and push received data to
O-MI Node. 

There are two kind of Agents using different interfaces: 
* *External agents* that push O-DF formatted sensor data to a TCP port of O-MI
Node.
* *Internal agents* that can be loaded from .jar file and instantiated to be run
inside same JVM as O-MI Node.

External Agent
--------------
All you need to do is to write a program that pushes O-DF formatted data to the TCP
port defined by `application.conf`'s omi-service.external-agent-port parameter.
Program can be written with any programming language. See
[the simple python example](https://github.com/AaltoAsia/O-MI/blob/master/agentExample.py).
Starting and stopping of external agents are the user's responsibility.

Another option for writing data from outside of O-MI Node is to send O-MI write request to it. 

If your external agent is run on a different computer, you will need to add its IP-address to 
`o-mi-service.input-whitelist-ips`. You can also accept input from subnets by adding 
their masks to `o-mi-service.input-whitelist-subnets`.

There is also possibility to use Shibboleth authentication to get permission for writing.

Internal Agent
----------------
`InternalAgent` is a trait extending `Actor` with `ActorLogging` and `Receiving`.
It has helper method name for accessing its name in `ActorSystem` and 
five abstract methods each handling received respective command received from 
`AgentSystem`. `Receiving` trait is used to force handling of commands, because Akka's ask pattern is used
when commands are send from `AgentSystem`.


`Receining` trait implements two 
methods. Method `receiver` adds given `Actor.Receive` to `receivers` so that it is called if 
there is not matching case statement for received message in any previously added 
`Actor.Receive`. Another final method is receive that calls receivers. 

`InternalAgent` calls `receiver` method adding Start, Restart, Stap, Quit and Configure 
commands to handled commands so that command's respective method's return value is send back 
to sender, `AgentSystem`. Now creating an InternalAgent means only creating a class 
extending `InternalAgent` and implementing methods: `start`, `restart`, `stop`, `quit` and 
`configure(config: String)`.


Now we have a responsible internal agent, but to get O-MI Node to run it, we need to
compile it to a .jar file and put it to `deploy` directory. After this we have
the final step, open the `application.conf` and add new object to
`agent-system.internal-agents`: 
```
"<name of agent>" = {
    class = "<class of agent>"
    config = "<config string>"
    owns = ["<Path owned by agent>", ...]
}
"Agent" = {
    class  = "agents.ScalaAgent"
    config = "Objects/Agent/sensor"
    owns = ["Objects/Agent/sensor"]
}
```
Field `owns` is optional and only needed for `ResponsibleInternalAgent`.

Now you need to restart O-MI Node to update its configuration.

ODFAgent
---------------
ODFAgent is also very simple agent that get path to .xml file as config string.
This file contains a O-DF structure.
ODFAgent parses xml file for O-DF, and start random generating values for OdfInfoItems.

