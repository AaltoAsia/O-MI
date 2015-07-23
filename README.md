O-MI Node Server
==============

[![Build Status](https://travis-ci.org/AaltoAsia/O-MI.svg?branch=master)](https://travis-ci.org/AaltoAsia/O-MI)
[![Coverage Status](https://coveralls.io/repos/AaltoAsia/O-MI/badge.svg?branch=master&service=github)](https://coveralls.io/github/AaltoAsia/O-MI?branch=master)
[![Codacy Badge](https://www.codacy.com/project/badge/9f49209c70e24c67bbc1826fde507518)](https://www.codacy.com/app/tkinnunen/O-MI)


Internet of Things data server.
Implementation of O-MI Node as specified in [Open Messaging Interface](https://www2.opengroup.org/ogsys/catalog/C14B) standard with [Open Data Format](https://www2.opengroup.org/ogsys/catalog/C14A) standard.

See `development` branch for latest progress.

[API Documentation](http://pesutykki.mooo.com/dump/Code-Gardeners-api/#package)

[Technical Documentation](https://drive.google.com/folderview?id=0B85FeC7Xf_sSfm9yNnFwTEQxclFCT2s3MUdDd1czWmFCM2FEQjIxTHRHU2xtT2NXUzJNR0U&usp=sharing)

Dependencies
------------
java 1.7


Compiling and packaging
-----------------------

1. Follow the instructions 1-4 in [Setup development environment](#setup-development-environment) below
2. `sbt release`
3. Result can be found in ./release/o-mi-node-0.1-SNAPSHOT.zip

Compiling a jar
---------------

1. Follow the instructions 1-4 in [Setup development environment](#setup-development-environment) below
2. `sbt one-jar`
3. Result can be found in ./target/scala-2.11/o-mi-node_2.11-0.1-SNAPSHOT-one-jar.jar


Setup development environment
-----------------------------

1. git clone
2. [Install sbt](http://www.scala-sbt.org/0.13/tutorial/Setup.html)
3. (windows: logout, or put sbt into PATH yourself)
4. Open a cmd or shell to the `O-MI Node/` project directory
5. You can (_optional step_)
    - `sbt compile`: compile the project
    - `sbt test`: run tests
    - `sbt run`: run the project or better:
    - `sbt re-start`:  run the project in background
    - `sbt re-stop`: close the background process
    - `sbt clean coverage test`: generate test coverage

    _extra info:_
    - run any of above commands again when there is a file change by adding `~` in front, like `sbt ~re-start`
    - all commands above compiles the needed files that are not yet compiled
    - run many commands in sequence easier if you open sbt command line with `sbt`

6. (_optional step_) Create an Eclipse project with `sbt eclipse` and then you can File->import `Existing Projects into Workspace` from eclipse.

Running
-------
To run O-MI Node run the corresponding startup script for your OS:

1. `start.bat` for Windows
2. `bash start.sh` for Unix and Mac

This will run O-MI Node with configuration in application.conf.
By default it has some example and demo agents.
More Information in next section.

Configuration
=============

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

