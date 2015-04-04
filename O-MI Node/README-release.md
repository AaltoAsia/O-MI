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
File application.conf is main connfiguration file that is given to O-MI Node
at start. File it's self contains few parameter.

interface = "0.0.0.0"
Defines were O-MI Node is started.

port = 8080
Defines witch port O-IM Node listens for HTTP requests.

agent-input-port = 8181
Defines port used for listening external agents' sensor updates.
  
num-latest-values-stored = 10
Defines how many latest values are stored for each sensor.

settings-read-odfpath = "Objects/OMI-Service/Settings/"
Defines path in DB were these values can be found.


agent-system {
   agents {
       "agents.SensorBoot" = "configs/SensorConfig"
       "agents.GenericBoot" = "configs/GenericConfig"
    }     
}
For internal agents configuration have Bootable's classname and Agent's
configuration file pairs.

Application.conf also have some Akka, Spray and SQLite  specific values:
Akka Configuration documentation:
http://doc.akka.io/docs/akka/2.3.9/general/configuration.html
Spray-can server Configuration documentation:
http://spray.io/documentation/1.2.2/spray-can/configuration/
Slick:
