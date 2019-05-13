O-MI Node Server
[![Latest release](https://img.shields.io/github/release/AaltoAsia/O-MI.svg)](https://github.com/AaltoAsia/O-MI/releases/latest)
[![Build Status](https://travis-ci.org/AaltoAsia/O-MI.svg?branch=master)](https://travis-ci.org/AaltoAsia/O-MI)
[![Coverage Status](https://coveralls.io/repos/AaltoAsia/O-MI/badge.svg?branch=master&service=github)](https://coveralls.io/github/AaltoAsia/O-MI?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/9f49209c70e24c67bbc1826fde507518)](https://www.codacy.com/app/TK009/O-MI?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=AaltoAsia/O-MI&amp;utm_campaign=Badge_Grade)
[![Join the chat at https://gitter.im/AaltoAsia/O-MI](https://badges.gitter.im/AaltoAsia/O-MI.svg)](https://gitter.im/AaltoAsia/O-MI?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
==============


<!-- Not resizable at the moment:
![O-MI Logo](https://cdn.rawgit.com/AaltoAsia/O-MI/3a3b3192/O-MI%20Node/html/0-MI.svg)
![O-DF Logo](https://cdn.rawgit.com/AaltoAsia/O-MI/3a3b3192/O-MI%20Node/html/0-DF.svg)
-->
<img src="https://cdn.rawgit.com/AaltoAsia/O-MI/3a3b3192/O-MI%20Node/html/0-MI.svg" height=100 /><img src="https://cdn.rawgit.com/AaltoAsia/O-MI/3a3b3192/O-MI%20Node/html/0-DF.svg" height=100 />

Table of Contents
-----------------

1. [Introduction](#introduction)
2. [Development status](#development-status)
2. [Resources](#resources)
3. [Dependencies](#dependencies)
4. [Running](#running)
5. [Compiling and Packaging](#compiling-and-packaging)
6. [Setting up a development environment](#setting-up-a-development-environment)
    1. [Setting up IDE](#setting-up-ide)
7. [Configuration](#configuration)
8. [FAQ](https://github.com/AaltoAsia/O-MI/blob/master/docs/FAQ.md)
9. [Optional Components](#optional-components)
10. [Directory structure](#directory-structure)

### Other
1. [sbt help](#simple-build-tool-cheat-sheet)



Introduction
------------

Internet of Things data server.
Implementation of O-MI Node as specified in [Open Messaging Interface (O-MI)](http://www.opengroup.org/iot/omi/index.htm) v1.0 standard with [Open Data Format (O-DF)](http://www.opengroup.org/iot/odf/index.htm) v1.0 standard. It is intended to be as reference implementation that shows how these standards work in more detail. See [Features.md](https://github.com/AaltoAsia/O-MI/blob/master/docs/Features.md) for more details.

O-MI can be used to query or update data, but also to set up data streams with *subscriptions*. It means that the standard can be used to make peer-to-peer like connections, but it can also be used in traditional client-server setup. O-MI standardizes the requests with XML, and they can be sent with almost any text based protocol. This implementation supports http, https and websocket.

O-DF is a simple object hierarchy format defined in XML. O-DF is used as data payload in O-MI. Simply put, O-DF can be thought as a file structure with directories which are `Object`s and files which are `InfoItem`s. O-MI also supports use of any text based data format, but request semantics might be more ambigious. Payloads other than O-DF are not yet supported in this implementation.

Questions or problems with the server or the standards can be posted to [Issues](https://github.com/AaltoAsia/O-MI/issues), email or [gitter chat](https://gitter.im/AaltoAsia/O-MI?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge). [![Join the chat at https://gitter.im/AaltoAsia/O-MI](https://badges.gitter.im/AaltoAsia/O-MI.svg)](https://gitter.im/AaltoAsia/O-MI?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


Development status
-----------------

All important features are working, but the project is in beta phase where things are not yet very optimized and malicious requests might cause crashing. However, the project can be used in production if the server has low risk profile or authentication.

For large amounts of data, it is **not** recommended to use the default value history database. Instead, it can be disabled or changed to e.g. InfluxDB, see [FAQ](https://github.com/AaltoAsia/O-MI/blob/master/docs/FAQ.md#how-to-use-a-different-db) for instructions.

O-MI Node can be extended with special request logic which can be implemented with "agents". Different authentication and authorization mechanisms can be implemented via auth apis. New databases can be implemented with DB interface. Other payloads than O-DF cannot yet be implemented.

See [`development`](https://github.com/AaltoAsia/O-MI/tree/development) branch for latest progress.

Resources
---------

* [O-MI Specification (html)](http://www.opengroup.org/iot/omi/index.htm) ([pdf](https://www2.opengroup.org/ogsys/catalog/C14B)) 
* [O-DF Specification (html)](http://www.opengroup.org/iot/odf/index.htm) ([pdf](https://www2.opengroup.org/ogsys/catalog/C14A))
* [API Documentation ScalaDoc](https://otaniemi3d.cs.hut.fi/omi/node/html/api/index.html)
* [API Documentation JavaDoc](https://otaniemi3d.cs.hut.fi/omi/node/html/api/java/index.html)
* [Examples of requests and responses, as handled by this server](https://otaniemi3d.cs.hut.fi/omi/node/html/ImplementationDetails.html)
* [Technical Documentation (outdated)](https://drive.google.com/folderview?id=0B85FeC7Xf_sSfm9yNnFwTEQxclFCT2s3MUdDd1czWmFCM2FEQjIxTHRHU2xtT2NXUzJNR0U&usp=sharing)



Dependencies
------------

* For running:
    - Java 8 JRE (JDK on Mac)
* For building:
    - Java 8 JDK
    - [SBT (Simple Build Tool)](http://www.scala-sbt.org/) or SBT enabled IDE


Running
-------
[Download the pre-compiled zip, tgz or debian package from latest git releases here](https://github.com/AaltoAsia/O-MI/releases/latest). [![Latest release](https://img.shields.io/github/release/AaltoAsia/O-MI.svg)](https://github.com/AaltoAsia/O-MI/releases/latest)

File to choose:

* `o-mi-node-x.y.z.zip` for Windows
* `o-mi-node-x.y.z.deb` for Ubuntu, Debian or other linux with dpkg
* `o-mi-node-x.y.z.rpm` for Red Hat linux
* `o-mi-node-x.y.z.tgz` for Mac or other Unix

**For zip or tgz file:**

1. Download and extract the file
2. Navigate to the `bin` directory
3. Run O-MI Node run the corresponding startup script from the bin directory for your OS:

* `bin/o-mi-node.bat` for Windows
* `bin/o-mi-node` for Unix and Mac

By default it will start at url [http://localhost:8080/](http://localhost:8080/) and has some example and demo agents.

This will run O-MI Node with configuration in `/conf/application.conf`.

More Information in the [Configuration](#Configuration) section.

**For linux packages**

1. `dpkg -i o-mi-node-x.y.z.deb` or `rpm -i o-mi-node-x.y.z.rpm`
2. `sudo systemctl start o-mi-node`
3. If it needs to start after a reboot: `sudo systemctl enable o-mi-node`

**With docker**

1. `docker pull aaltoasia/o-mi`
2. `docker run docker run -p 8080:8080 aaltoasia/o-mi`


Setting up a development environment
-----------------------------

1. `git clone`
2. [Install sbt](http://www.scala-sbt.org/0.13/tutorial/Setup.html)
3. (on windows: logout, or put sbt into PATH yourself)
4. Open a cmd or shell to the `O-MI` project directory
5. Then run `sbt` and in opened the ">" prompt run `reStart` to compile and run the O-MI Node
6. Visit http://localhost:8080/ to see that it's working

You can check our [Simple Build Tool cheat sheet](https://github.com/AaltoAsia/O-MI/blob/master/docs/DevelopmentEnvironment.md#simple-build-tool-cheat-sheet) section to learn more

If you would like to use an IDE, check [how to set up Eclipse or IntelliJ IDEA](https://github.com/AaltoAsia/O-MI/blob/master/docs/DevelopmentEnvironment.md#setting-up-ide)

Compiling and packaging
-----------------------

1. Follow the instructions 1-4 in [Setting up a development environment](#setup-development-environment) above
2. run `sbt universal:packageBin` (For other package types, use `sbt release`)
3. Result can be found in `./target/universal/o-mi-Node-version.zip`


See [SBT Universal Plugin](http://www.scala-sbt.org/sbt-native-packager/formats/universal.html)
for more packaging methods.

<!--- Currently not supported
  Compiling a jar
  ---------------

  1. Follow the instructions 1-4 in [Setup development environment](#setup-development-environment) below
  2. `sbt one-jar`
  3. Result can be found in `./target/scala-2.11/o-mi-node_2.11-0.1-SNAPSHOT-one-jar.jar`

--->



Configuration
=============

Basic configuration
-------------------

See [reference.conf](https://github.com/AaltoAsia/O-MI/blob/master/O-MI-Node/src/main/resources/reference.conf)
for the defaults and configuration documentation in the comments.

### Configuration Location

* In package releases: `/etc/o-mi-node/application.conf`
* In tar and zip releases: `./conf/application.conf`
* In development environment: `./O-MI-Node/src/main/resources/application.conf` (create a new file if not found)
    * Default values are stored in `./O-MI-Node/src/main/resources/reference.conf`

### Syntax

Configuration file allows json-like syntax but it is not very strict about the object syntax. See [here](https://github.com/lightbend/config#using-hocon-the-json-superset).

Library Config
--------------

`application.conf` can also have a lot of Akka (threading framework and HTTP server) and Slick (database) specific settings:

- [HTTP server settings (Akka HTTP)](http://doc.akka.io/docs/akka-http/10.0.9/scala/http/configuration.html)
- [Threading system etc. (Akka)](http://doc.akka.io/docs/akka/2.3.9/general/configuration.html)
- [SQL value history database (Slick forConfig)](http://slick.typesafe.com/doc/3.0.0-RC2/api/index.html#slick.jdbc.JdbcBackend$DatabaseFactoryDef@forConfig\(String,Config,Driver\):Database)
   - see also [guide on how to use a different db](https://github.com/AaltoAsia/O-MI/blob/master/docs/FAQ.md#how-to-use-a-different-db)


Optional components
-------------------

- [Authentication and/or Authorization module](https://github.com/AaltoAsia/O-MI/blob/master/docs/AuthenticationAuthorization.md#o-mi-authentication-and-authorization-reference-implementations)
- InfluxDB as value history db. Install it separately and see [this guide](https://github.com/AaltoAsia/O-MI/blob/master/docs/FAQ.md#how-to-use-a-different-db)
- Warp10 as value history db. Branch `warp10integration` has experimental integration to [Warp10](http://www.warp10.io/) as the DB backend. Download `-warp10` version from releases.

Directory structure
----------------------

- `O-MI-Node/` - Main directory for the o-mi-node server
    - `src/main` - Server source code
    - `src/test` - Source code of automatic tests
    - `html/` - Developer web app source
- `Agents/` - Sources for all internal agent examples
- `tools/` - Some scripts and examples for working with O-DF/O-MI
- `database/` - Database location when running
- `project/dependencies.scala` - Library dependencies list and versions
- `build.sbt` - Build system settings and instructions
- `src/` - Only some specific files for releases, see `O-MI-Node/src` for real sources


Acknowledgements
---------------

Sections of this project has been developed as part of the [bIoTope Project](www.bIoTope-project.eu), which has received funding from the European Union’s Horizon 2020 Research and Innovation Programme under grant agreement No. 688203.
