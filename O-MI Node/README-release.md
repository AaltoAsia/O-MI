O-MI Node Server
================

Implementation of Internet of Things standards Open Messaging Interface and Open Data Format.

Dependencies
------------

java


Running
-------

To run O-MI Node run the corresponding startup script for your OS:

1. `start.bat` for Windows
2. `start.sh` for Unix and Mac

This will run O-MI Node with configuration in application.conf.
By default it has some example and demo agents.
More Information in next section.


Basic configuration
-------------------

File application.conf is the main connfiguration file that is read 
at start. 

`omi-service` configuration options:


`interface = "0.0.0.0"`

Defines at which interface ip or hostname to bind the O-MI Node. 
O-MI Node accepts O-MI messages only for this address.
Use `"0.0.0.0"` for binding to all interfaces.


`port = 8080`

Defines which port O-IM Node listens for HTTP requests.

  
`external-agent-interface = "localhost"`

Defines interface used for listening sensor updates of external agents.
Should be restricted to localhost or LAN ip, otherwise anyone could
send sensor data from the internet.


`external-agent-port = 8181`

Defines port used for listening sensor updates of external agents.


`num-latest-values-stored = 10`

Defines how many latest values are stored for each sensor.


`settings-read-odfpath = "Objects/OMI-Service/Settings/"`

Defines path in O-DF hierarchy where values of the settings can be found.


```
agent-system {
   agents {
       "agents.SensorBoot" = "configs/SensorConfig"
       "agents.GenericBoot" = "configs/GenericConfig"
    }     
}
```
For internal agents, configuration has classname and
configuration filepath of the agents in pairs.

NOTE: application.conf can also have a lot of Akka, Spray and Database (slick) specific settings:

Library Config
--------------

- [Akka Actors](http://doc.akka.io/docs/akka/2.3.9/general/configuration.html)
- [Spray-can http server](http://spray.io/documentation/1.2.2/spray-can/configuration/)
- [Slick forConfig docs](http://slick.typesafe.com/doc/3.0.0-RC2/api/index.html#slick.jdbc.JdbcBackend$DatabaseFactoryDef@forConfig\(String,Config,Driver\):Database)

