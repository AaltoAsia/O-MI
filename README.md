O-MI Node
==============

[![Build Status](https://travis-ci.org/AaltoAsia/O-MI.svg?branch=master)](https://travis-ci.org/AaltoAsia/O-MI)
[![Coverage Status](https://coveralls.io/repos/AaltoAsia/O-MI/badge.svg?branch=master&service=github)](https://coveralls.io/github/AaltoAsia/O-MI?branch=master)
[![Codacy Badge](https://www.codacy.com/project/badge/9f49209c70e24c67bbc1826fde507518)](https://www.codacy.com/app/tkinnunen/O-MI)


Internet of Things data server.
Implementation of O-MI Node as specified in [Open Messaging Interface](https://www2.opengroup.org/ogsys/catalog/C14B) standard with [Open Data Format](https://www2.opengroup.org/ogsys/catalog/C14A) standard.

See `development` branch for latest progress.


Setup development environment
-----------------------------

1. git clone
2. [Install sbt](http://www.scala-sbt.org/0.13/tutorial/Setup.html)
3. (windows: logout, or put sbt into PATH yourself)
4. Open a cmd or shell to the project directory
5. You can
    - run the project with `sbt run`
    - compile the project with `sbt compile`
    - run tests with `sbt test`
    - or better: run the project in background with `sbt re-start`
    - close the background process with `sbt re-stop`
    - see test coverage `sbt clean coverage test`
    - run any of above commands again when there is a file change by adding `~` in front, like `sbt ~re-start`
    - all commands above compiles the needed files that are not yet compiled
    - run many commands in sequence easier if you open sbt command line with `sbt`
6. Create an Eclipse project with `sbt eclipse` and then you can import `Existing Projects into Workspace` from eclipse.

