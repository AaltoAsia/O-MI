Code-Gardeners
==============

[![Coverage Status](https://coveralls.io/repos/SnowblindFatal/Code-Gardeners/badge.png?branch=development)](https://coveralls.io/r/SnowblindFatal/Code-Gardeners?branch=development)

[![Build Status](https://travis-ci.org/SnowblindFatal/Code-Gardeners.svg?branch=development)](https://travis-ci.org/SnowblindFatal/Code-Gardeners)

Software project course repository. Implementation of Internet of Things standards Open Messaging Interface and Open Data Format. 

Dependies
---------
SQLite 


Compiling
---------

1. Follow the instructions 1-4 in 'Setup development environment' below
2. `sbt one-jar`
3. Result can be found in ./target/scala-2.11/o-mi-node_2.11-0.1-SNAPSHOT-one-jar.jar


Setup development environment
-----------------------------

1. git clone
2. [Install sbt](http://www.scala-sbt.org/0.13/tutorial/Setup.html)
3. (windows: logout, or put sbt into PATH yourself)
4. Open a cmd or shell to the `O-MI Node/` project directory
5. You can
    - `sbt compile`: compile the project
    - `sbt test`: run tests
    - `sbt run`: run the project or better:
    - `sbt re-start`:  run the project in background
    - `sbt re-stop`: close the background process
    - `sbt clean coverage test`: generate test coverage 

    - run any of above commands again when there is a file change by adding `~` in front, like `sbt ~re-start`
    - all commands above compiles the needed files that are not yet compiled
    - run many commands in sequence easier if you open sbt command line with `sbt`

6. Create an Eclipse project with `sbt eclipse` and then you can import `Existing Projects into Workspace` from eclipse.

Running
-------
To run O-MI Node you need to run corresponding startup script for your OS.
1. start.bat for Windows
2. start.sh for Unix and Mac

This will run O-MI Node with two internal Agents, GenericAgent and SensorAgent.
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
SQLite:
