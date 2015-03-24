Code-Gardeners
==============

[![Coverage Status](https://coveralls.io/repos/SnowblindFatal/Code-Gardeners/badge.png?branch=development)](https://coveralls.io/r/SnowblindFatal/Code-Gardeners?branch=development)

[![Build Status](https://travis-ci.org/SnowblindFatal/Code-Gardeners.svg?branch=development)](https://travis-ci.org/SnowblindFatal/Code-Gardeners)

Software project course repository. Implementation of Internet of Things standards Open Messaging Interface and Open Data Format. 


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

