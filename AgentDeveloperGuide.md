Other Guides
============

* [Developing agents using Scala](https://github.com/AaltoAsia/O-MI/blob/development/ScalaAgentDeveloperGuide.md).
* [Developing agents using Java](https://github.com/AaltoAsia/O-MI/blob/development/JavaAgentDeveloperGuide.md).

What are Agents?
================

Agents are small plugin programs that can be run on the same JVM as the server or otherwise easily connect to the Node. For example, an agent can be connected to devices and push received data to O-MI Node. 

There are two kinds of agents using different interfaces: 

- *Internal agents* that are loaded from a .jar file and instantiated to be run 
inside the same JVM as O-MI Node. They can be created using Scala or Java (or other JVM compatible language). Only
scala implementations can also "own" paths and decide if the write is succesful or failed/rejected. *Internal agents* that "owns" paths is called *responsible*.

- *External agents* push O-DF formatted data to a TCP port of 
O-MI Node. The default configuration of O-MI Node (in `application.conf`) does not 
enable support for *external agents*. *External agents* are implemented as a simple *internal agent*.


Internal Agent 
================

*Internal agents* are classes that extend `InternalAgent` interface. 
`InternalAgent` interface extends `akka.actor.Actor`. Both Scala and Java have 
own interfaces that need to be implemented to be loaded by `InternalAgentLoader`.

* [Developing agents using Scala](https://github.com/AaltoAsia/O-MI/blob/development/ScalaAgentDeveloperGuide.md).
* [Developing agents using Java](https://github.com/AaltoAsia/O-MI/blob/development/JavaAgentDeveloperGuide.md).

External Agent
==============

The recommended option for writing data from outside of O-MI Node is to send O-MI write request to it, because there are more security options and possibility to change to different O-MI compatible service. External agents can be used for prototyping or implementing simple adapters, usually in the same machine or local network as the server. For security reasons, don't change the interface setting (or firewalls) to allow external network to this feature.

To enable *external agents* uncomment/add following lines to `agent-system.internal-agents` in `application.conf`: 

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

This adds an *internal agent* of class `agents.ExternalAgentListener` with agent name 
`ExternalAgentListener`. *ExternalAgentListener* will now listen to 
given port of given interface for O-DF formatted data. Timeout in config 
defines how long will *ExternalAgentListener* wait for binding and unbinding of
given port before failing.

When *external agents* are enabled, any program that pushes O-DF formatted data to the TCP
port will be writen to O-MI Node.  

Program that act as *external agent* can be written with any programming language. See
[the simple python example](https://github.com/AaltoAsia/O-MI/blob/master/agentExample.py).
Starting and stopping of external agents are the user's responsibility.

If your external agent is run on a different computer, you will need to add its IP-address to
`o-mi-service.input-whitelist-ips`. You can also accept input from subnets by adding their masks to
`o-mi-service.input-whitelist-subnets`.


