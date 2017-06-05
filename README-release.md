O-MI Node Server
================

Implementation of Internet of Things standards: Open Messaging Interface and Open Data Format.

Dependencies
------------

java-1.8


Running
-------
To run O-MI Node run the corresponding startup script from the bin directory for your OS:

1. `o-mi-node.bat` for Windows
2. `o-mi-node` for Unix and Mac
3. Visit http://localhost:8080/ to see that it's working

This will run O-MI Node with configuration in application.conf.
By default it has some example and demo agents.
More Information in the next section.

Basic configuration
-------------------

See `application.conf` for the defaults and configuration documentation.


Library Config
--------------

NOTE: application.conf can also have a lot of Akka, Spray and Database (slick) specific settings:

- [Akka Actors](http://doc.akka.io/docs/akka/2.3.9/general/configuration.html)
- [Spray-can http server](http://spray.io/documentation/1.2.2/spray-can/configuration/)
- [Slick forConfig docs](http://slick.typesafe.com/doc/3.0.0-RC2/api/index.html#slick.jdbc.JdbcBackend$DatabaseFactoryDef@forConfig\(String,Config,Driver\):Database)

