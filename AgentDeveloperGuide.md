Other Guides
============

[Developnig agents using Scala](https://github.com/AaltoAsia/O-MI/blob/development/ScalaAgentDeveloperGuide.md).
[Developnig agents using Java](https://github.com/AaltoAsia/O-MI/blob/developmentr/JavaAgentDeveloperGuide.md).

What are Agents?
================

Agents are small programs that are connect to devices and push received data to
O-MI Node. 

There are two kinds of Agents using different interfaces: 

- *Internal agents* that are loaded from .jar file and instantiated to be run 
inside the same JVM as O-MI Node. They can be created using Scala or Java. Only
scala implementations can also own paths and receive any authorized write to 
them for futher handling. *Internal agents* that owns any paths is called 
*responsible*.

- *External agents* that push O-DF formatted sensor data to a TCP port of 
O-MI Node. O-MI Nodes standart configuration in `application.conf` does not 
enable support *external agents*. They can be enabled by uncommenting 
*ExternalAgentListener* from `agent-system.internal-agents`. 
*ExternalAgentListener* is *internal agent*, that opens a port on an interface 
to listen O-DF formatted data. 

Internal Agent 
================

*Internal agents* are classes that extend `InternalAgent` interface. 
`InternalAgent` interface extends `Akka.actor.Actor`. Both Scala and Java have 
own interfaces that need to be implemented to be instantiated by `InternalAgentLoader`.

[Developnig agents using Scala](https://github.com/AaltoAsia/O-MI/blob/development/ScalaAgentDeveloperGuide.md).
[Developnig agents using Java](https://github.com/AaltoAsia/O-MI/blob/developmentr/JavaAgentDeveloperGuide.md).

External Agent
==============

The recommended option for writing data from outside of O-MI Node is to send O-MI write request to it. 
This is prefered way because there are more security options. 

To enable *external agents* add following lines to `agent-system.internal-agents` in `application.conf`: 

```
    "ExternalAgentListener" = {
        class = "agents.ExternalAgentListener"
        language = "scala"
        config =  {
            timeout = 10 seconds
            port = 8181
            interface = "localhost"
        }
    }
```

This adds an *internal agent* of class `agents.ExternalAgentListener` named 
ExternalAgentListener to O-MI Node. ExternalAgentListener will now listen to 
given port of given interface for O-DF formatted data. Timeout in config 
definens how long will ExternalAgentListener wait for binding and unbinding of
given port before failing.

When *external agents* are enabled, any program that pushes O-DF formatted data to the TCP
port defined in configuration of `ExternalAgentListener` will be writen to O-MI Node.  

Program that act as *external agent* can be written with any programming language. See
[the simple python example](https://github.com/AaltoAsia/O-MI/blob/master/agentExample.py).
Starting and stopping of external agents are the user's responsibility.

If your external agent is run on a different computer, you will need to add its IP-address to
`o-mi-service.input-whitelist-ips`. You can also accept input from subnets by adding their masks to
`o-mi-service.input-whitelist-subnets`.


