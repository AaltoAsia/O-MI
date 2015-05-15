Code-Gardeners
==============

[![Build Status](https://travis-ci.org/TK009/Code-Gardeners.svg?branch=master)](https://travis-ci.org/SnowblindFatal/Code-Gardeners)
[![Coverage Status](https://coveralls.io/repos/TK009/Code-Gardeners/badge.svg)](https://coveralls.io/r/TK009/Code-Gardeners)
[![Codacy Badge](https://www.codacy.com/project/badge/a4c681fb3fef4d21bb561a1b160c6d07)](https://www.codacy.com/app/tkinnunen/Code-Gardeners)


Software project course repository


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

