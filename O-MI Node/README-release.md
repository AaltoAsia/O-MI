O-MI Node Server
================

Implementation of Internet of Things standards Open Messaging Interface and Open Data Format.

Dependencies
------------
SQLite 


Running
-------
To run O-MI Node run the corresponding startup script for your OS:
1. start.bat for Windows
2. start.sh for Unix and Mac

This will run O-MI Node with configuration in application.conf.
By default it has some example and demo agents.
More Information in next section.

Configuration
-------------
File application.conf is the main connfiguration file that is read 
at start. 

interface = "0.0.0.0"
Defines at which interface or hostname O-MI Node is started.

port = 8080
Defines which port O-IM Node listens for HTTP requests.

agent-input-port = 8181
Defines port used for listening external agents' sensor updates.
  
num-latest-values-stored = 10
Defines how many latest values are stored for each sensor.

settings-read-odfpath = "Objects/OMI-Service/Settings/"
Defines path in O-DF hierarchy where values of the settings can be found.


agent-system {
   agents {
       "agents.SensorBoot" = "configs/SensorConfig"
       "agents.GenericBoot" = "configs/GenericConfig"
    }     
}
For internal agents, configuration has Bootable's classname and Agent's
configuration file pairs.

application.conf can also have some Akka, Spray and SQLite specific settings:
[Akka Configuration documentation](http://doc.akka.io/docs/akka/2.3.9/general/configuration.html)
[Spray-can server Configuration documentation](http://spray.io/documentation/1.2.2/spray-can/configuration/)
[Slick](http://slick.typesafe.com/doc/3.0.0-RC2/api/index.html#slick.jdbc.JdbcBackend$DatabaseFactoryDef@forConfig\(String,Config,Driver\):Database)
